package com.p2pvoice.webrtc

import android.content.Context
import android.util.Log
import com.p2pvoice.data.CallLogDao
import com.p2pvoice.data.CallLogEntity
import com.p2pvoice.signaling.IceCandidatePayload
import com.p2pvoice.signaling.SdpPayload
import com.p2pvoice.signaling.SignalMessage
import com.p2pvoice.signaling.SupabaseSignalingManager
import com.p2pvoice.utils.ProximitySensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

// ─── Call State ────────────────────────────────────────────────────────────

sealed class CallState {
    object Idle : CallState()
    data class Calling(val targetId: String) : CallState()
    data class IncomingCall(val callerId: String) : CallState()
    data class Connected(val peerId: String) : CallState()
    object Ended : CallState()
    data class Error(val message: String) : CallState()
}

// ─── WebRTC Manager ────────────────────────────────────────────────────────

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingManager: SupabaseSignalingManager,
    private val proximitySensorManager: ProximitySensorManager,
    private val callLogDao: CallLogDao
) {
    private val tag = "WebRTCManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var callStartTime: Long = 0
    private var isIncomingCall: Boolean = false

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var eglBase: EglBase? = null

    @Volatile
    private var myUserId: String = ""
    @Volatile
    private var remotePeerId: String = ""
    private var ringtone: Ringtone? = null
    private var isInitialized = false
    private var signalingJob: kotlinx.coroutines.Job? = null
    
    private var pendingOffer: SignalMessage? = null

    // STUN servers for NAT traversal (Google's public STUN — no account needed)
    private var iceServers = listOf<PeerConnection.IceServer>()

    // ── Initialize WebRTC ─────────────────────────────────────────────────
    fun initialize(userId: String) {
        var factoryToDispose: PeerConnectionFactory? = null
        
        synchronized(this) {
            if (isInitialized && myUserId == userId && peerConnectionFactory != null) return
            
            Log.d(tag, "Initializing WebRTC for user: $userId")
            
            // Cleanup existing resources if re-initializing
            if (myUserId.isNotEmpty() && myUserId != userId) {
                factoryToDispose = peerConnectionFactory
                peerConnectionFactory = null
            }
            
            myUserId = userId
        }

        // Dispose old factory outside the lock to prevent deadlocks
        if (factoryToDispose != null) {
            cleanupPeerConnection()
            try {
                factoryToDispose.dispose()
            } catch (e: Exception) {
                Log.e(tag, "Error disposing PeerConnectionFactory: ${e.message}")
            }
        }

        synchronized(this) {
            try {
                if (peerConnectionFactory == null) {
                    if (!isInitialized) {
                        // Initialize PeerConnectionFactory once per process
                        PeerConnectionFactory.initialize(
                            PeerConnectionFactory.InitializationOptions.builder(context)
                                .setEnableInternalTracer(false)
                                .createInitializationOptions()
                        )
                        
                        iceServers = listOf(
                            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
                        )
                        
                        isInitialized = true
                    }

                    if (eglBase == null) {
                        eglBase = EglBase.create()
                    }

                    val options = PeerConnectionFactory.Options()
                    peerConnectionFactory = PeerConnectionFactory.builder()
                        .setOptions(options)
                        .createPeerConnectionFactory()
                    
                    Log.d(tag, "PeerConnectionFactory created")
                }
                
                // Subscribe to signaling events (canceling previous if any)
                startObservingSignaling()
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize WebRTC", e)
                throw e
            }
        }
    }

    private fun startObservingSignaling() {
        signalingJob?.cancel()
        signalingJob = scope.launch {
            observeSignalingEvents()
        }
    }

    // ── Observe Supabase signaling events ────────────────────────────────
    private suspend fun observeSignalingEvents() {
        kotlinx.coroutines.coroutineScope {
            // Incoming call request
            launch {
                signalingManager.callRequestFlow.collect { message ->
                    handleIncomingCallRequest(message)
                }
            }
            // Incoming SDP Offer
            launch {
                signalingManager.offerFlow.collect { message ->
                    try {
                        handleRemoteOffer(message)
                    } catch (e: Exception) {
                        Log.e(tag, "Error handling offer", e)
                    }
                }
            }
            // Incoming SDP Answer
            launch {
                signalingManager.answerFlow.collect { message ->
                    try {
                        handleRemoteAnswer(message)
                    } catch (e: Exception) {
                        Log.e(tag, "Error handling answer", e)
                    }
                }
            }
            // Incoming ICE Candidate
            launch {
                signalingManager.iceCandidateFlow.collect { message ->
                    try {
                        handleRemoteIceCandidate(message)
                    } catch (e: Exception) {
                        Log.e(tag, "Error handling ICE candidate", e)
                    }
                }
            }
            // Call ended or declined by remote
            launch {
                signalingManager.callEndFlow.collect { message ->
                    Log.d(tag, "Call signal 'call_end' received from ${message.senderId}")
                    val currentState = _callState.value
                    
                    // Specific logic for when we are in 'Calling' state and the target declines
                    if (currentState is CallState.Calling && currentState.targetId == message.senderId) {
                        Log.d(tag, "Call was declined by target")
                        stopOutgoingTone()
                        _callState.value = CallState.Error("Call declined")
                        
                        // Wait a bit then go back to Idle
                        kotlinx.coroutines.delay(3000)
                        if (_callState.value is CallState.Error) {
                            _callState.value = CallState.Idle
                        }
                    } else {
                        endCall(notifyRemote = false)
                    }
                }
            }
        }
    }

    private fun handleIncomingCallRequest(message: SignalMessage) {
        synchronized(this) {
            try {
                // Double protection: Ensure we don't handle our own call requests
                if (message.senderId == myUserId) {
                    Log.d(tag, "Ignoring self-sent call request from ${message.senderId}")
                    return
                }

                // Check if we are already in a call or an incoming call from same/different person
                val currentState = _callState.value
                if (currentState is CallState.Idle || currentState is CallState.Ended) {
                    Log.d(tag, "Received call request from ${message.senderId}")
                    remotePeerId = message.senderId
                    isIncomingCall = true
                    _callState.value = CallState.IncomingCall(message.senderId)
                    com.p2pvoice.CallForegroundService.startIncomingCall(context, message.senderId)
                    startRinging()
                } else if (currentState is CallState.IncomingCall && currentState.callerId == message.senderId) {
                    Log.d(tag, "Received duplicate call request from ${message.senderId}, ignoring but ensuring ringing")
                    startRinging()
                } else {
                    Log.d(tag, "Ignored call request from ${message.senderId} (state: $currentState)")
                    // Optional: Send busy signal back?
                }
            } catch (e: Exception) {
                Log.e(tag, "Error processing call request from ${message.senderId}", e)
            }
        }
    }

    // ── Initiate a call ───────────────────────────────────────────────────
    fun startCall(targetUserId: String) {
        synchronized(this) {
            if (!isInitialized || peerConnectionFactory == null) {
                _callState.value = CallState.Error("WebRTC not initialized properly")
                return@synchronized
            }
            
            Log.d(tag, "Initiating call to $targetUserId")
            remotePeerId = targetUserId
            isIncomingCall = false
            _callState.value = CallState.Calling(targetUserId)
            startOutgoingTone()
        }

        // Start foreground service immediately to keep process alive during signaling
        com.p2pvoice.CallForegroundService.startService(context, targetUserId)

        scope.launch {
            try {
                // Notify the target
                signalingManager.sendCallRequest(myUserId, targetUserId)

                // Create peer connection and generate offer
                synchronized(this@WebRTCManager) {
                    createPeerConnection()
                    createOffer()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start call", e)
                _callState.value = CallState.Error("Failed to start call: ${e.message}")
                com.p2pvoice.CallForegroundService.stopService(context)
            }
        }
    }

    // ── Accept an incoming call ───────────────────────────────────────────
    fun acceptCall() {
        Log.d(tag, "acceptCall() called")
        synchronized(this) {
            if (_callState.value !is CallState.IncomingCall) {
                Log.d(tag, "acceptCall ignored: current state is ${_callState.value}, not IncomingCall")
                return
            }
            stopRinging()
        }
        scope.launch {
            try {
                synchronized(this@WebRTCManager) {
                    if (peerConnectionFactory == null) {
                        Log.e(tag, "Cannot accept call: PeerConnectionFactory is null")
                        return@synchronized
                    }
                    
                    if (peerConnection == null) {
                        Log.d(tag, "Creating PeerConnection for accepted call")
                        createPeerConnection()
                    }
                    
                    // Handle pending offer if it arrived before acceptance
                    pendingOffer?.let {
                        Log.d(tag, "Handling cached pending offer from ${it.senderId}")
                        handleRemoteOffer(it)
                        pendingOffer = null
                    } ?: Log.d(tag, "No pending offer found yet during acceptCall")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during acceptCall coroutine", e)
            }
        }
    }

    // ── Reject an incoming call ───────────────────────────────────────────
    fun rejectCall() {
        val peerId: String
        synchronized(this) {
            if (_callState.value !is CallState.IncomingCall) {
                Log.d(tag, "rejectCall ignored: not in IncomingCall state")
                return
            }
            stopRinging()
            peerId = remotePeerId
            _callState.value = CallState.Idle
            remotePeerId = ""
        }
        
        scope.launch {
            if (peerId.isNotEmpty()) {
                signalingManager.sendCallEnd(myUserId, peerId)
            }
        }
    }

    // ── End an active call ────────────────────────────────────────────────
    fun endCall(notifyRemote: Boolean = true) {
        Log.d(tag, "endCall called (notifyRemote=$notifyRemote)")
        
        val shouldProceed = synchronized(this) {
            val currentState = _callState.value
            if (currentState is CallState.Ended || currentState is CallState.Idle) {
                Log.d(tag, "endCall ignored: already in state $currentState")
                false
            } else {
                _callState.value = CallState.Ended
                true
            }
        }
        
        if (!shouldProceed) return

        stopRinging()
        stopOutgoingTone()
        com.p2pvoice.CallForegroundService.stopService(context)
        proximitySensorManager.stop()

        val finalPeerId: String
        val finalIsIncoming: Boolean
        val finalStartTime: Long
        val duration: Long

        synchronized(this) {
            duration = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0
            finalPeerId = remotePeerId
            finalIsIncoming = isIncomingCall
            finalStartTime = if (callStartTime > 0) callStartTime else System.currentTimeMillis()

            remotePeerId = ""
            callStartTime = 0
        }

        val targetIdForSignaling = finalPeerId

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Cleanup WebRTC resources off the signaling thread to prevent deadlocks
            cleanupPeerConnection()

            if (finalPeerId.isNotEmpty()) {
                try {
                    callLogDao.insert(
                        CallLogEntity(
                            peerId = finalPeerId,
                            timestamp = finalStartTime,
                            duration = duration,
                            isIncoming = finalIsIncoming
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to insert call log", e)
                }
            }
            if (notifyRemote && targetIdForSignaling.isNotEmpty()) {
                try {
                    signalingManager.sendCallEnd(myUserId, targetIdForSignaling)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to notify remote of call end", e)
                }
            }

            // Reset to idle after brief pause
            kotlinx.coroutines.delay(2000)
            if (_callState.value == CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }

    // ── Create PeerConnection ─────────────────────────────────────────────
    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            peerConnectionObserver
        )

        // Add local audio track
        setupLocalAudio()
    }

    // ── Setup local microphone audio ──────────────────────────────────────
    private fun setupLocalAudio() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        localAudioTrack?.setEnabled(true)

        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
    }

    // ── Create and send SDP Offer ─────────────────────────────────────────
    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(tag, "Offer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "Local description set (Offer)")
                        val targetId = remotePeerId
                        if (targetId.isNotEmpty()) {
                            scope.launch {
                                try {
                                    signalingManager.sendOffer(myUserId, targetId, sdp.description)
                                    Log.d(tag, "Offer sent to $targetId")
                                } catch (e: Exception) {
                                    Log.e(tag, "Failed to send offer to $targetId", e)
                                }
                            }
                        } else {
                            Log.e(tag, "Cannot send offer: remotePeerId is empty")
                        }
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "Failed to set local description: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Failed to create offer: $error")
                _callState.value = CallState.Error("Failed to create offer: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    // ── Handle remote SDP Offer ───────────────────────────────────────────
    private fun handleRemoteOffer(message: SignalMessage) {
        Log.d(tag, "Handling remote offer from ${message.senderId}")
        val currentPeerConnection: PeerConnection?
        synchronized(this) {
            if (remotePeerId.isEmpty()) {
                remotePeerId = message.senderId
            }
            currentPeerConnection = peerConnection
            if (currentPeerConnection == null) {
                Log.d(tag, "PeerConnection not ready, caching offer from ${message.senderId}")
                pendingOffer = message
                return
            }
        }

        try {
            val sdpPayload = Json.decodeFromString<SdpPayload>(message.payload)
            val remoteDesc = SessionDescription(
                SessionDescription.Type.OFFER,
                sdpPayload.sdp
            )

            currentPeerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(tag, "Remote description (Offer) set successfully")
                    // Create answer
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    currentPeerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            Log.d(tag, "Answer created successfully")
                            currentPeerConnection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d(tag, "Local description (Answer) set successfully")
                                    scope.launch {
                                        try {
                                            signalingManager.sendAnswer(
                                                myUserId,
                                                message.senderId,
                                                sdp.description
                                            )
                                            Log.d(tag, "Answer sent to ${message.senderId}")
                                        } catch (e: Exception) {
                                            Log.e(tag, "Failed to send answer to ${message.senderId}", e)
                                        }
                                    }
                                }
                                override fun onSetFailure(error: String?) {
                                    Log.e(tag, "Failed to set local description (Answer): $error")
                                }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, sdp)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(tag, "Failed to create answer: $error")
                        }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                    }, constraints)
                }
                override fun onSetFailure(error: String?) {
                    Log.e(tag, "Failed to set remote description (Offer): $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDesc)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing SDP offer payload", e)
        }
    }

    // ── Handle remote SDP Answer ──────────────────────────────────────────
    private fun handleRemoteAnswer(message: SignalMessage) {
        Log.d(tag, "Handling remote answer from ${message.senderId}")
        val currentPeerConnection: PeerConnection?
        synchronized(this) {
            if (remotePeerId.isEmpty()) {
                remotePeerId = message.senderId
            }
            currentPeerConnection = peerConnection
        }
        
        if (currentPeerConnection == null) {
            Log.e(tag, "Ignored answer from ${message.senderId}: PeerConnection is null")
            return
        }

        try {
            val sdpPayload = Json.decodeFromString<SdpPayload>(message.payload)
            val remoteDesc = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdpPayload.sdp
            )

            currentPeerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(tag, "Remote description (Answer) set successfully")
                }
                override fun onSetFailure(error: String?) {
                    Log.e(tag, "Failed to set remote description (Answer): $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDesc)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing SDP answer payload", e)
        }
    }

    // ── Handle remote ICE Candidate ───────────────────────────────────────
    private fun handleRemoteIceCandidate(message: SignalMessage) {
        val currentPeerConnection: PeerConnection?
        synchronized(this) {
            currentPeerConnection = peerConnection
        }
        
        if (currentPeerConnection == null) {
            Log.d(tag, "Caching/Ignoring ICE candidate from ${message.senderId}: PeerConnection null")
            return
        }

        try {
            val icePayload = Json.decodeFromString<IceCandidatePayload>(message.payload)
            val candidate = IceCandidate(
                icePayload.sdpMid,
                icePayload.sdpMLineIndex ?: 0,
                icePayload.candidate
            )
            currentPeerConnection.addIceCandidate(candidate)
            Log.d(tag, "Added remote ICE candidate from ${message.senderId}")
        } catch (e: Exception) {
            Log.e(tag, "Error parsing/adding ICE candidate", e)
        }
    }

    // ── PeerConnection Observer ───────────────────────────────────────────
    private val peerConnectionObserver = object : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate) {
            val targetId = remotePeerId
            if (targetId.isEmpty()) {
                Log.e(tag, "Cannot send ICE candidate: remotePeerId is empty")
                return
            }
            Log.d(tag, "Local ICE candidate: ${candidate.sdpMid}, target: $targetId")
            scope.launch {
                try {
                    signalingManager.sendIceCandidate(
                        myId = myUserId,
                        targetId = targetId,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        candidate = candidate.sdp
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send ICE candidate to $targetId", e)
                }
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(tag, "ICE Connection Change: $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    if (callStartTime == 0L) {
                        callStartTime = System.currentTimeMillis()
                    }
                    _callState.value = CallState.Connected(remotePeerId)
                    stopOutgoingTone()
                    com.p2pvoice.CallForegroundService.startService(context, remotePeerId)
                    proximitySensorManager.start()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.d(tag, "ICE Disconnected - waiting for recovery or timeout")
                }
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED -> {
                    Log.d(tag, "ICE Failed or Closed - ending call")
                    com.p2pvoice.CallForegroundService.stopService(context)
                    endCall(notifyRemote = false)
                }
                else -> {}
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            // Remote audio track added — WebRTC handles playback automatically
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
    }

    // ── Toggle Mute ───────────────────────────────────────────────────────
    fun toggleMute() {
        synchronized(this) {
            val newMuteState = !_isMuted.value
            try {
                localAudioTrack?.setEnabled(!newMuteState)
                _isMuted.value = newMuteState
            } catch (e: Exception) {
                Log.e(tag, "Error toggling mute", e)
            }
        }
    }

    // ── Toggle Speaker ────────────────────────────────────────────────────
    fun toggleSpeaker() {
        synchronized(this) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val newSpeakerState = !_isSpeakerOn.value
                audioManager.isSpeakerphoneOn = newSpeakerState
                _isSpeakerOn.value = newSpeakerState
            } catch (e: Exception) {
                Log.e(tag, "Error toggling speaker", e)
            }
        }
    }

    // ── Audio/Haptic Feedback ────────────────────────────────────────────

    private fun startRinging() {
        if (ringtone != null) return
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(tag, "Error playing ringtone", e)
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping ringtone", e)
        }
        ringtone = null
    }

    private var outgoingTone: Ringtone? = null

    private fun startOutgoingTone() {
        if (outgoingTone != null) return
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            outgoingTone = RingtoneManager.getRingtone(context, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outgoingTone?.isLooping = true
            }
            outgoingTone?.play()
        } catch (e: Exception) {
            Log.e(tag, "Error playing outgoing tone", e)
        }
    }

    private fun stopOutgoingTone() {
        try {
            outgoingTone?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping outgoing tone", e)
        }
        outgoingTone = null
    }

    // ── Cleanup ───────────────────────────────────────────────────────────
    private fun cleanupPeerConnection() {
        Log.d(tag, "Cleaning up PeerConnection resources")
        
        val track: AudioTrack?
        val source: AudioSource?
        val pc: PeerConnection?
        
        synchronized(this) {
            track = localAudioTrack
            source = localAudioSource
            pc = peerConnection
            
            localAudioTrack = null
            localAudioSource = null
            peerConnection = null
        }

        // Dispose resources outside the synchronized block to prevent deadlocks
        // WebRTC's dispose() can be blocking and may wait for threads that need this lock
        try {
            track?.setEnabled(false)
            track?.dispose()
        } catch (e: Exception) {
            Log.e(tag, "Error disposing localAudioTrack: ${e.message}")
        }

        try {
            source?.dispose()
        } catch (e: Exception) {
            Log.e(tag, "Error disposing localAudioSource", e)
        }

        try {
            pc?.close()
            pc?.dispose()
        } catch (e: Exception) {
            Log.e(tag, "Error disposing peerConnection", e)
        }
    }

    fun dispose() {
        Log.d(tag, "Disposing WebRTCManager")
        cleanupPeerConnection()
        
        val factory: PeerConnectionFactory?
        val egl: EglBase?
        
        synchronized(this) {
            factory = peerConnectionFactory
            egl = eglBase
            
            peerConnectionFactory = null
            eglBase = null
            isInitialized = false
        }

        try {
            factory?.dispose()
        } catch (e: Exception) {
            Log.e(tag, "Error disposing PeerConnectionFactory: ${e.message}")
        }

        try {
            egl?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing EglBase: ${e.message}")
        }
    }
}
