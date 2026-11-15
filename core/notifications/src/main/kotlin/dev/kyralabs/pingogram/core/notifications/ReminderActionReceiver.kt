package dev.kyralabs.pingogram.core.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives a reminder notification's "Snooze" and hands it to [ReminderWorker]: the user is at the phone, so plain
 * one-time work runs at once (expedited work would need a foreground notification before Android 12). Not exported:
 * only the app's own pending intents reach it.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        val chatId = intent.getLongExtra(EXTRA_CHAT, 0)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE, 0)
        if (slot < 0 || chatId == 0L) return
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(ReminderWorker.snooze(slot, chatId, messageId))
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    internal companion object {
        private const val EXTRA_SLOT = "dev.kyralabs.pingogram.extra.REMINDER_SLOT"
        private const val EXTRA_CHAT = "dev.kyralabs.pingogram.extra.REMINDER_CHAT"
        private const val EXTRA_MESSAGE = "dev.kyralabs.pingogram.extra.REMINDER_MESSAGE"

        /** "Snooze" of the reminder of message [messageId] of chat [chatId] in account [slot]. */
        fun snoozeIntent(
            context: Context,
            slot: Int,
            chatId: Long,
            messageId: Long,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, ReminderActionReceiver::class.java)
                    .putExtra(EXTRA_SLOT, slot)
                    .putExtra(EXTRA_CHAT, chatId)
                    .putExtra(EXTRA_MESSAGE, messageId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
