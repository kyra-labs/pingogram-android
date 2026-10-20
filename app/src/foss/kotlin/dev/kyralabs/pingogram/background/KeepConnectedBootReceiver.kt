package dev.kyralabs.pingogram.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts [KeepConnectedService] after a reboot (`BOOT_COMPLETED`) or an update of Pingogram (`MY_PACKAGE_REPLACED`),
 * which stops it. Disabled in the manifest and enabled by [KeepConnectedController] only while "Keep connected" is on,
 * so Pingogram never starts with the device for users who did not ask for it.
 *
 * Both broadcasts are exemptions from Android 12's background start restrictions. Android 15 forbids starting some
 * foreground service types from `BOOT_COMPLETED` (dataSync, camera, mediaPlayback, phoneCall, mediaProjection,
 * microphone); `specialUse`, the service's type, is not among them. The process this starts runs
 * `PingogramApplication`, whose [KeepConnectedController] stops the service again if nobody is logged in.
 */
class KeepConnectedBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            startKeepConnectedService(context)
        }
    }
}
