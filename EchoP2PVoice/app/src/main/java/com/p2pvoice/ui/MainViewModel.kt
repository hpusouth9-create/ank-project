package com.p2pvoice.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pvoice.data.CallLogDao
import com.p2pvoice.data.CallLogEntity
import com.p2pvoice.signaling.SupabaseSignalingManager
import com.p2pvoice.utils.UserIdManager
import com.p2pvoice.webrtc.CallState
import com.p2pvoice.webrtc.WebRTCManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val myUserId: String = "",
    val targetUserId: String = "",
    val callState: CallState = CallState.Idle,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isInitialized: Boolean = false,
    val errorMessage: String? = null,
    val callLogs: List<CallLogEntity> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val webRTCManager: WebRTCManager,
    private val signalingManager: SupabaseSignalingManager,
    private val userIdManager: UserIdManager,
    private val callLogDao: CallLogDao
) : ViewModel() {

    private val tag = "MainViewModel"

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        Log.d(tag, "Initializing ViewModel")
        viewModelScope.launch {
            try {
                val userId = userIdManager.getUserId()
                Log.d(tag, "User ID retrieved: $userId")
                
                // Update UI immediately with the generated ID
                _uiState.value = _uiState.value.copy(
                    myUserId = userId
                )

                // Initialize WebRTC
                try {
                    Log.d(tag, "Initializing WebRTC")
                    webRTCManager.initialize(userId)
                    Log.d(tag, "WebRTC initialized")
                } catch (e: Exception) {
                    Log.e(tag, "WebRTC Init Error", e)
                    _uiState.value = _uiState.value.copy(errorMessage = "WebRTC Init Error: ${e.message}")
                }

                // Subscribe to Supabase signaling channel in a separate job
                // so it doesn't block UI state updates if connection is slow
                viewModelScope.launch {
                    try {
                        Log.d(tag, "Subscribing to signaling")
                        signalingManager.subscribe(userId)
                        Log.d(tag, "Signaling subscribed")
                        _uiState.value = _uiState.value.copy(isInitialized = true)
                    } catch (e: Exception) {
                        Log.e(tag, "Signaling Connection Failed", e)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Signaling Connection Failed: ${e.message}"
                        )
                    }
                }

                // Observe WebRTC state
                observeCallState()
                observeAudioState()
                observeCallLogs()
            } catch (e: Exception) {
                Log.e(tag, "Initialization failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Initialization failed: ${e.message}")
            }
        }
    }

    private fun observeCallState() {
        viewModelScope.launch {
            webRTCManager.callState.collect { callState ->
                _uiState.value = _uiState.value.copy(callState = callState)

                if (callState is CallState.Error) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = callState.message
                    )
                }
            }
        }
    }

    private fun observeAudioState() {
        viewModelScope.launch {
            webRTCManager.isMuted.collect { muted ->
                _uiState.value = _uiState.value.copy(isMuted = muted)
            }
        }
        viewModelScope.launch {
            webRTCManager.isSpeakerOn.collect { speaker ->
                _uiState.value = _uiState.value.copy(isSpeakerOn = speaker)
            }
        }
    }

    private fun observeCallLogs() {
        viewModelScope.launch {
            callLogDao.getAllCallLogs().collect { logs ->
                _uiState.value = _uiState.value.copy(callLogs = logs)
            }
        }
    }

    fun onTargetIdChanged(id: String) {
        val normalized = userIdManager.normalizeId(id)
        _uiState.value = _uiState.value.copy(targetUserId = normalized)
    }

    fun callFromLog(peerId: String) {
        onTargetIdChanged(peerId)
        startCall()
    }

    fun startCall() {
        val targetId = _uiState.value.targetUserId
        if (targetId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a peer ID to call")
            return
        }
        if (!userIdManager.isValidId(targetId)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Invalid peer ID format (e.g. ABCD-1234)")
            return
        }
        if (targetId == _uiState.value.myUserId) {
            _uiState.value = _uiState.value.copy(errorMessage = "You cannot call yourself")
            return
        }
        webRTCManager.startCall(targetId)
    }

    fun acceptCall() {
        webRTCManager.acceptCall()
    }

    fun rejectCall() {
        webRTCManager.rejectCall()
    }

    fun endCall() {
        webRTCManager.endCall()
    }

    fun ensureSignalingConnected() {
        val userId = _uiState.value.myUserId
        if (userId.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    signalingManager.subscribe(userId)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to re-subscribe signaling", e)
                }
            }
        }
    }

    fun toggleMute() {
        webRTCManager.toggleMute()
    }

    fun toggleSpeaker() {
        webRTCManager.toggleSpeaker()
    }

    fun regenerateId() {
        viewModelScope.launch {
            signalingManager.unsubscribe()
            val newId = userIdManager.regenerateId()
            webRTCManager.initialize(newId)
            signalingManager.subscribe(newId)
            _uiState.value = _uiState.value.copy(myUserId = newId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearCallLogs() {
        viewModelScope.launch {
            callLogDao.deleteAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            signalingManager.unsubscribe()
        }
        webRTCManager.dispose()
    }
}
