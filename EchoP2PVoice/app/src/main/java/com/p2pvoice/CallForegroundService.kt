package com.p2pvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the call alive when the app is backgrounded.
 * Start this when a call connects, stop it when the call ends.
 */
class CallForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "call_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "START_CALL"
        const val ACTION_INCOMING = "INCOMING_CALL"
        const val ACTION_STOP = "STOP_CALL"
        const val EXTRA_PEER_ID = "peer_id"

        fun startService(context: Context, peerId: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_ID, peerId)
            }
            context.startForegroundService(intent)
        }

        fun startIncomingCall(context: Context, callerId: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_INCOMING
                putExtra(EXTRA_PEER_ID, callerId)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("CallForegroundService", "onStartCommand action: $action")
        
        val peerId = intent?.getStringExtra(EXTRA_PEER_ID) ?: "Unknown"
        
        when (action) {
            ACTION_START -> {
                Log.d("CallForegroundService", "Starting foreground for $peerId")
                val notification = buildNotification(peerId)
                startServiceForeground(notification)
            }
            ACTION_INCOMING -> {
                Log.d("CallForegroundService", "Showing incoming call for $peerId")
                val notification = buildIncomingCallNotification(peerId)
                startServiceForeground(notification)
            }
            ACTION_STOP -> {
                Log.d("CallForegroundService", "Stopping foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                Log.d("CallForegroundService", "Unknown action $action, ensuring foreground")
                val notification = buildNotification(peerId)
                startServiceForeground(notification)
            }
        }
        return START_NOT_STICKY
    }

    private fun startServiceForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // We use FOREGROUND_SERVICE_TYPE_MICROPHONE as it's required for voice calls on Android 14+
                // This MUST be declared in AndroidManifest.xml for this service.
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("CallForegroundService", "Failed to start foreground service", e)
            // Fallback attempt without type if it fails due to missing type declaration or permission
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e("CallForegroundService", "Critical: could not start foreground at all", e2)
            }
        }
    }

    private fun buildIncomingCallNotification(peerId: String): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText("Peer ID: $peerId")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setSilent(false)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
    }

    private fun buildNotification(peerId: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Active Call")
            .setContentText("Connected with $peerId")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows when a P2P voice call is active"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
