package dev.kyralabs.pingogram.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Catch-up on the account shown (`REQUIREMENTS.md` FR-X23), the Daily digest's tap: the app's launcher activity
 * with [Intent.ACTION_VIEW] and a `pingogram://catch-up` data URI, which the activity reads back with [opensCatchUp].
 */
object CatchUpIntents {
    /** The Daily digest's tap; immutable, as nothing fills it in. */
    fun openCatchUpPendingIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.removeCategory(Intent.CATEGORY_LAUNCHER)
            intent.data =
                Uri
                    .Builder()
                    .scheme(NotificationIntents.SCHEME)
                    .authority(CATCH_UP_HOST)
                    .build()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    /** Whether [intent] opens Catch-up ([openCatchUpPendingIntent]), not replayed from the recent apps. */
    fun opensCatchUp(intent: Intent?): Boolean =
        intent != null &&
            intent.action == Intent.ACTION_VIEW &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
            intent.data?.scheme == NotificationIntents.SCHEME &&
            intent.data?.host == CATCH_UP_HOST

    private const val CATCH_UP_HOST = "catch-up"
}
