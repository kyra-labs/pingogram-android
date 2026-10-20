package dev.kyralabs.pingogram.feature.settings.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.notifications.PingogramChannel

// What Android allows Pingogram's notifications, as the Notifications page reads it, and its settings screens.

/** Starts a screen of Android's settings; nothing on a system without it (none known). */
internal fun Context.openSystemSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No such settings screen: nothing to open.
    }
}

/** Reads what Android allows Pingogram's notifications and reports it to [onChecked]. */
@Composable
internal fun rememberAccessCheck(onChecked: (NotificationSettingsEvent) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val currentOnChecked by rememberUpdatedState(onChecked)
    return remember(activity, context) { { currentOnChecked(accessOf(context, activity)) } }
}

private fun accessOf(
    context: Context,
    activity: Activity?,
): NotificationSettingsEvent.AccessChecked {
    val manager = NotificationManagerCompat.from(context)
    // Before Android 13 notifications need no permission.
    val permission =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    return NotificationSettingsEvent.AccessChecked(
        notificationsEnabled = manager.areNotificationsEnabled(),
        permissionGranted =
            permission == null ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        shouldShowRationale = permission != null && activity?.shouldShowRequestPermissionRationale(permission) == true,
        blockedChannels =
            NotificationScope.entries
                .filter { scope ->
                    manager.getNotificationChannelCompat(PingogramChannel.of(scope).id)?.importance ==
                        NotificationManagerCompat.IMPORTANCE_NONE
                }.toSet(),
    )
}
