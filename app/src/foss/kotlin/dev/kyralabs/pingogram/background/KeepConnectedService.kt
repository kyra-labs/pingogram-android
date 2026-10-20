package dev.kyralabs.pingogram.background

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.notifications.R as NotificationsR

/**
 * "Keep connected" (`foss`, `docs/adr/0003-keep-connected-foreground-service.md`): a foreground service that does
 * nothing but keep Pingogram's process running, so TDLib's connection stays open and its notification engine
 * (`NotificationRenderer`) shows new messages at once while the app is in the background. TDLib itself starts with the
 * process, in `PingogramApplication`.
 *
 * - Type `specialUse` (Android 14+), with its subtype explained in the manifest: none of the other types covers a
 *   persistent messaging connection, and `dataSync` is limited to 6 hours a day from Android 15.
 * - Its ongoing notification sits in the low-importance "Connection" channel (Android raises a foreground service's
 *   minimum-importance notification to low anyway): silent, no badge. Tapping it opens the app; "Turn off" turns the
 *   setting off ([ACTION_TURN_OFF]), which stops the service.
 * - Sticky: Android starts it again after killing the process for memory. If Android 12+ then refuses the foreground
 *   start (`ForegroundServiceStartNotAllowedException`), it stops, and starts again the next time the app is in the
 *   foreground ([KeepConnectedController]).
 */
class KeepConnectedService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            turnOff()
            return START_NOT_STICKY
        }
        return try {
            startInForeground()
            START_STICKY
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): started from the background without exemption.
            Log.w(TAG, "Keep connected refused in the foreground: ${refused.javaClass.simpleName}")
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private fun startInForeground() {
        createChannel(this)
        val notification = notification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            // Before Android 14 the type comes from the manifest.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Saves the setting as off, on the application scope; the controller then stops the service, and so does this. */
    private fun turnOff() {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, KeepConnectedEntryPoint::class.java)
        entryPoint.applicationScope().launch { entryPoint.keepConnectedRepository().setKeepConnected(false) }
        stopSelf()
    }

    /** What the service takes from the app's graph; it is no Hilt entry point itself, so tests can start it bare. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface KeepConnectedEntryPoint {
        fun keepConnectedRepository(): KeepConnectedRepository

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    internal companion object {
        /** The id of the service's notification; TDLib's notification groups use tags of their own. */
        const val NOTIFICATION_ID = 1

        /** The "Connection" channel's id; kept forever, as Android keeps a channel's settings by id. */
        const val CHANNEL_ID = "connection"

        /** The notification's "Turn off". */
        const val ACTION_TURN_OFF = "dev.kyralabs.pingogram.action.KEEP_CONNECTED_OFF"

        private const val TAG = "KeepConnected"

        /** Creates the "Connection" channel, or renames it after a language change; never changes what the user set. */
        fun createChannel(context: Context) {
            val channel =
                NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.keep_connected_channel))
                    .setDescription(context.getString(R.string.keep_connected_channel_description))
                    .setShowBadge(false)
                    .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        fun notification(context: Context): Notification {
            val turnOff =
                PendingIntent.getService(
                    context,
                    0,
                    Intent(context, KeepConnectedService::class.java).setAction(ACTION_TURN_OFF),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(NotificationsR.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, NotificationsR.color.notification_accent))
                .setContentTitle(context.getString(R.string.keep_connected_notification_title))
                .setContentText(context.getString(R.string.keep_connected_notification_text))
                .setContentIntent(NotificationIntents.openAppPendingIntent(context))
                .addAction(0, context.getString(R.string.keep_connected_turn_off), turnOff)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
