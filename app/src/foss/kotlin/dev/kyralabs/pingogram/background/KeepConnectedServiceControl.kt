package dev.kyralabs.pingogram.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Starts and stops [KeepConnectedService], and turns its start after a reboot on and off; a seam for tests. */
internal interface KeepConnectedServiceControl {
    /** Starts the service in the foreground; only while the app is in the foreground, or from an allowed exemption. */
    fun start()

    /** Stops the service; nothing if it does not run. */
    fun stop()

    /** Enables [KeepConnectedBootReceiver] while [enabled], so the service starts again after a reboot or an update. */
    fun setStartsAfterBoot(enabled: Boolean)
}

/** [KeepConnectedServiceControl] on the platform. */
internal class AndroidKeepConnectedServiceControl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : KeepConnectedServiceControl {
        override fun start() = startKeepConnectedService(context)

        override fun stop() {
            context.stopService(Intent(context, KeepConnectedService::class.java))
        }

        override fun setStartsAfterBoot(enabled: Boolean) {
            val receiver = ComponentName(context, KeepConnectedBootReceiver::class.java)
            // The manifest disables it: "default" is off.
            val state =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                }
            val packageManager = context.packageManager
            // Only on a change: setting it writes the package settings to disk.
            if (packageManager.getComponentEnabledSetting(receiver) != state) {
                packageManager.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

/**
 * Starts [KeepConnectedService] as a foreground service. Android 12+ refuses that from the background outside its
 * exemptions (`ForegroundServiceStartNotAllowedException`, an `IllegalStateException`, as is the background start
 * refusal of Android 8+): then it does not run, and the next start from the foreground starts it.
 */
internal fun startKeepConnectedService(context: Context) {
    try {
        ContextCompat.startForegroundService(context, Intent(context, KeepConnectedService::class.java))
    } catch (refused: IllegalStateException) {
        Log.w(TAG, "Keep connected not started: ${refused.javaClass.simpleName}")
    }
}

private const val TAG = "KeepConnected"
