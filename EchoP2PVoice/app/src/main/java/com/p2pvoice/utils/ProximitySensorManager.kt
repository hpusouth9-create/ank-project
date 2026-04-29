package com.p2pvoice.utils

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the proximity sensor to turn off the screen during calls.
 */
@Singleton
class ProximitySensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ProximitySensorManager"
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Activates the proximity sensor wake lock.
     * When the sensor detects something close (e.g., an ear), the screen will turn off.
     */
    @Synchronized
    fun start() {
        if (wakeLock != null && wakeLock?.isHeld == true) {
            Log.d(tag, "Proximity wake lock already active")
            return
        }

        try {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "EchoP2PVoice:ProximityWakeLock"
                    )
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire()
                    Log.d(tag, "Proximity wake lock acquired")
                }
            } else {
                Log.w(tag, "Proximity screen off wake lock not supported on this device")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error acquiring proximity wake lock", e)
        }
    }

    /**
     * Releases the proximity sensor wake lock and restores the screen state.
     */
    @Synchronized
    fun stop() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(tag, "Proximity wake lock released")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing proximity wake lock", e)
        } finally {
            wakeLock = null
        }
    }
}
