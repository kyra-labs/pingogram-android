package dev.kyralabs.pingogram.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Receives the actions of notifications ([NotificationAction]) and hands each to [NotificationActionWorker] as
 * expedited work: a receiver may run only briefly, and the work must wait for TDLib, which a broadcast can start in a
 * new process. Expedited work runs at once; when the app has used up its quota for that, it runs as soon as it can.
 * Not exported: only the app's own pending intents reach it.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = NotificationAction.fromIntent(intent) ?: return
        val request =
            OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(action.toData())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    internal companion object {
        /** The tag of every notification action's work. */
        const val WORK_TAG = "notification_action"
    }
}
