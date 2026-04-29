@file:OptIn(InternalSerializationApi::class)
package com.p2pvoice.signaling

import android.util.Log
import com.p2pvoice.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.okhttp.OkHttp
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.createChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

// ─── Signaling message types ───────────────────────────────────────────────

@Serializable
data class SignalMessage(
    val type: String,       // "offer" | "answer" | "ice_candidate" | "call_request" | "call_end"
    val senderId: String,
    val receiverId: String,
    val payload: String     // JSON-encoded SDP or ICE candidate
)

@Serializable
data class SdpPayload(
    val sdp: String,
    val sdpType: String     // "offer" | "answer"
)

@Serializable
data class IceCandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int?,
    val candidate: String
)

// ─── Signaling Manager ─────────────────────────────────────────────────────

@Singleton
class SupabaseSignalingManager @Inject constructor() {
    private val tag = "SupabaseSignaling"
    
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(tag, "Unhandled coroutine exception in SignalingManager", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    val supabase = createSupabaseClient(
        supabaseUrl = com.p2pvoice.BuildConfig.SUPABASE_URL,
        supabaseKey = com.p2pvoice.BuildConfig.SUPABASE_ANON_KEY
    ) {
        httpEngine = OkHttp.create()
        install(Realtime)
        install(Postgrest)
    }

    private var channel: RealtimeChannel? = null
    private var subscribedUserId: String? = null
    private val connectionMutex = Mutex()
    
    // Cache for target channels to avoid rapid join/leave
    private val targetChannels = mutableMapOf<String, RealtimeChannel>()

    // Flows for incoming signals
    private val _offerFlow = MutableSharedFlow<SignalMessage>()
    val offerFlow: SharedFlow<SignalMessage> = _offerFlow.asSharedFlow()

    private val _answerFlow = MutableSharedFlow<SignalMessage>()
    val answerFlow: SharedFlow<SignalMessage> = _answerFlow.asSharedFlow()

    private val _iceCandidateFlow = MutableSharedFlow<SignalMessage>()
    val iceCandidateFlow: SharedFlow<SignalMessage> = _iceCandidateFlow.asSharedFlow()

    private val _callRequestFlow = MutableSharedFlow<SignalMessage>()
    val callRequestFlow: SharedFlow<SignalMessage> = _callRequestFlow.asSharedFlow()

    private val _callEndFlow = MutableSharedFlow<SignalMessage>()
    val callEndFlow: SharedFlow<SignalMessage> = _callEndFlow.asSharedFlow()


    private var signalingJob: kotlinx.coroutines.Job? = null

    // ── Subscribe to a channel for this user ID ──────────────────────────
    suspend fun subscribe(myUserId: String) {
        connectionMutex.withLock {
            try {
                // If already subscribed to this user and channel is active, skip
                if (subscribedUserId == myUserId && channel != null) {
                    val currentStatus = supabase.realtime.status.value
                    if (currentStatus == Realtime.Status.CONNECTED) {
                        Log.d(tag, "Already subscribed to $myUserId and connected")
                        return
                    }
                }

                Log.d(tag, "Subscribing to channel for user: $myUserId")
                
                // Connect to the realtime websocket first if not already connected
                val currentStatus = supabase.realtime.status.value
                if (currentStatus != Realtime.Status.CONNECTED && 
                    currentStatus != Realtime.Status.CONNECTING) {
                    try {
                        Log.d(tag, "Connecting to Realtime... Current status: $currentStatus")
                        supabase.realtime.connect()
                    } catch (e: IllegalStateException) {
                        Log.d(tag, "Realtime already connected (IllegalStateException caught)")
                    } catch (e: Exception) {
                        Log.w(tag, "Realtime connect attempt failed: ${e.message}")
                    }
                }

                // Wait for connection to be established before joining channel
                var retries = 0
                while (supabase.realtime.status.value != Realtime.Status.CONNECTED && retries < 10) {
                    Log.d(tag, "Waiting for Realtime connection... Status: ${supabase.realtime.status.value}")
                    kotlinx.coroutines.delay(500)
                    retries++
                }

                if (supabase.realtime.status.value != Realtime.Status.CONNECTED) {
                    Log.e(tag, "Timeout waiting for Realtime connection. Status: ${supabase.realtime.status.value}")
                }

                // Each user listens on their own channel
                val channelName = "user:$myUserId"
                val currentChannel = supabase.realtime.createChannel(channelName)
                channel = currentChannel

                // Cancel previous job if any
                signalingJob?.cancel()

                // Listen for all broadcast events on this channel
                signalingJob = scope.launch {
                    Log.d(tag, "Starting broadcastFlow collection for $channelName")
                    currentChannel.broadcastFlow<SignalMessage>(event = "signal")
                        .onEach { Log.d(tag, "Received signal: ${it.type} from ${it.senderId}") }
                        .collect { message ->
                            // Filter out self-sent messages (Loopback protection)
                            if (message.senderId == myUserId) {
                                Log.d(tag, "Ignoring self-sent signal: ${message.type}")
                                return@collect
                            }

                            when (message.type) {
                                "offer"         -> _offerFlow.emit(message)
                                "answer"        -> _answerFlow.emit(message)
                                "ice_candidate" -> _iceCandidateFlow.emit(message)
                                "call_request"  -> _callRequestFlow.emit(message)
                                "call_end"      -> _callEndFlow.emit(message)
                            }
                        }
                }

                currentChannel.join()
                subscribedUserId = myUserId
                Log.d(tag, "Joined channel: $channelName")
            } catch (e: Exception) {
                Log.e(tag, "Error in subscribe: ${e.message}", e)
                throw e
            }
        }
    }

    // ── Send a signal to a specific user ─────────────────────────────────
    suspend fun sendSignal(message: SignalMessage) {
        if (message.receiverId.isEmpty()) {
            Log.e(tag, "Attempted to send signal ${message.type} to empty receiverId")
            return
        }
        
        connectionMutex.withLock {
            try {
                // Ensure we are connected
                val currentStatus = supabase.realtime.status.value
                if (currentStatus != Realtime.Status.CONNECTED &&
                    currentStatus != Realtime.Status.CONNECTING) {
                    Log.d(tag, "Realtime not connected (status: $currentStatus), connecting before sending...")
                    try {
                        supabase.realtime.connect()
                    } catch (e: IllegalStateException) {
                        Log.d(tag, "Realtime already connected during sendSignal")
                    } catch (e: Exception) {
                        Log.w(tag, "Connect attempt during sendSignal failed: ${e.message}")
                    }
                }

                // Get or create channel for the receiver
                val targetChannelName = "user:${message.receiverId}"
                val targetChannel = targetChannels.getOrPut(message.receiverId) {
                    Log.d(tag, "Creating and joining new target channel: $targetChannelName")
                    supabase.realtime.createChannel(targetChannelName).also {
                        scope.launch {
                            try {
                                it.join()
                                Log.d(tag, "Successfully joined target channel: $targetChannelName")
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to join target channel: $targetChannelName", e)
                            }
                        }
                    }
                }
                
                Log.d(tag, "Broadcasting ${message.type} to ${message.receiverId}")
                try {
                    targetChannel.broadcast(
                        event = "signal",
                        message = Json.encodeToJsonElement(message).jsonObject
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Broadcast failed for ${message.type} to ${message.receiverId}: ${e.message}")
                    // If broadcast fails, the channel might be dead. Clear it for next time.
                    targetChannels.remove(message.receiverId)
                    throw e
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in sendSignal: ${e.message}", e)
                throw e
            }
        }
    }

    // ── Helpers for each signal type ─────────────────────────────────────

    suspend fun sendCallRequest(myId: String, targetId: String) {
        sendSignal(SignalMessage(
            type = "call_request",
            senderId = myId,
            receiverId = targetId,
            payload = ""
        ))
    }

    suspend fun sendOffer(myId: String, targetId: String, sdp: String) {
        val payload = Json.encodeToString(
            SdpPayload(sdp = sdp, sdpType = "offer")
        )
        sendSignal(SignalMessage(
            type = "offer",
            senderId = myId,
            receiverId = targetId,
            payload = payload
        ))
    }

    suspend fun sendAnswer(myId: String, targetId: String, sdp: String) {
        val payload = Json.encodeToString(
            SdpPayload(sdp = sdp, sdpType = "answer")
        )
        sendSignal(SignalMessage(
            type = "answer",
            senderId = myId,
            receiverId = targetId,
            payload = payload
        ))
    }

    suspend fun sendIceCandidate(
        myId: String,
        targetId: String,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        candidate: String
    ) {
        val payload = Json.encodeToString(
            IceCandidatePayload(sdpMid, sdpMLineIndex, candidate)
        )
        sendSignal(SignalMessage(
            type = "ice_candidate",
            senderId = myId,
            receiverId = targetId,
            payload = payload
        ))
    }

    suspend fun sendCallEnd(myId: String, targetId: String) {
        sendSignal(SignalMessage(
            type = "call_end",
            senderId = myId,
            receiverId = targetId,
            payload = ""
        ))
    }

    // ── Cleanup ───────────────────────────────────────────────────────────
    suspend fun unsubscribe() {
        connectionMutex.withLock {
            channel?.let { 
                try {
                    supabase.realtime.removeChannel(it) 
                } catch (e: Exception) {
                    Log.e(tag, "Error removing my channel", e)
                }
            }
            channel = null
            subscribedUserId = null
            
            // Also cleanup target channels
            targetChannels.forEach { (_, channel) ->
                try {
                    supabase.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.e(tag, "Error removing target channel", e)
                }
            }
            targetChannels.clear()
        }
    }
}
