package com.p2pvoice

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p2pvoice.ui.MainViewModel
import com.p2pvoice.ui.screens.*
import com.p2pvoice.ui.theme.P2PVoiceTheme
import com.p2pvoice.ui.theme.SpaceBlack
import com.p2pvoice.webrtc.CallState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Show rationale — user needs mic permission for calls
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLocked()

        // Request necessary permissions upfront
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            P2PVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SpaceBlack
                ) {
                    P2PVoiceApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        showWhenLocked()
    }

    override fun onResume() {
        super.onResume()
        showWhenLocked()
        
        // Ensure signaling is active when resuming
        viewModel.ensureSignalingConnected()

        val state = viewModel.uiState.value.callState
        if (state !is CallState.Idle && state !is CallState.Ended) {
            Log.d("MainActivity", "Resuming while in state: $state")
            // Re-request dismissal of keyguard to ensure UI is visible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showWhenLocked()
    }

    private fun showWhenLocked() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}

@Composable
fun P2PVoiceApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Route to appropriate screen based on call state
    AnimatedContent(
        targetState = uiState.callState,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        },
        label = "screenTransition"
    ) { callState ->
        when (callState) {
            is CallState.Idle, is CallState.Ended -> {
                HomeScreen(
                    myUserId = uiState.myUserId,
                    targetUserId = uiState.targetUserId,
                    onTargetIdChanged = viewModel::onTargetIdChanged,
                    onStartCall = viewModel::startCall,
                    onRegenerateId = viewModel::regenerateId,
                    errorMessage = uiState.errorMessage,
                    onClearError = viewModel::clearError,
                    callLogs = uiState.callLogs,
                    onClearLogs = viewModel::clearCallLogs,
                    onCallLogClick = viewModel::callFromLog
                )
            }

            is CallState.IncomingCall -> {
                IncomingCallScreen(
                    callerId = callState.callerId,
                    onAccept = viewModel::acceptCall,
                    onReject = viewModel::rejectCall
                )
            }

            is CallState.Calling -> {
                OutgoingCallScreen(
                    targetId = callState.targetId,
                    onCancel = viewModel::endCall
                )
            }

            is CallState.Connected -> {
                ActiveCallScreen(
                    peerId = callState.peerId,
                    isMuted = uiState.isMuted,
                    isSpeakerOn = uiState.isSpeakerOn,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onEndCall = viewModel::endCall
                )
            }

            is CallState.Error -> {
                HomeScreen(
                    myUserId = uiState.myUserId,
                    targetUserId = uiState.targetUserId,
                    onTargetIdChanged = viewModel::onTargetIdChanged,
                    onStartCall = viewModel::startCall,
                    onRegenerateId = viewModel::regenerateId,
                    errorMessage = callState.message,
                    onClearError = viewModel::clearError,
                    callLogs = uiState.callLogs,
                    onClearLogs = viewModel::clearCallLogs,
                    onCallLogClick = viewModel::callFromLog
                )
            }
        }
    }
}
