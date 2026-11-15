package dev.kyralabs.pingogram.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * A reminder notification's "Snooze 1 h" ([ReminderNotificationBuilder]): moves the reminder an hour from now in its
 * account and removes the notification; [ReminderAlarms] then schedules it again. The file write is short, so the
 * broadcast is held only while it runs. Not exported: only the app's own pending intent reaches it.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SNOOZE) return
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        val chatId = intent.getLongExtra(EXTRA_CHAT, 0)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE, 0)
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        entryPoint.reminderNotifications().cancel(slot, chatId, messageId)
        val pending = goAsync()
        entryPoint.applicationScope().launch {
            try {
                val reminders = entryPoint.accountGraphs().repositories(slot)?.reminderRepository()
                val reminder =
                    reminders?.reminders?.first()?.firstOrNull { it.chatId == chatId && it.messageId == messageId }
                if (reminders != null && reminder != null) {
                    reminders.set(reminder.copy(at = entryPoint.clock().now().plus(SNOOZE), isDone = false))
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** What the receiver takes from the app's graph; it is no Hilt entry point itself, so tests can create it bare. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun accountGraphs(): AccountGraphs

        fun reminderNotifications(): ReminderNotificationBuilder

        fun clock(): WallClock

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    internal companion object {
        const val ACTION_SNOOZE = "dev.kyralabs.pingogram.action.SNOOZE_REMINDER"
        const val EXTRA_SLOT = "dev.kyralabs.pingogram.extra.REMINDER_SLOT"
        const val EXTRA_CHAT = "dev.kyralabs.pingogram.extra.REMINDER_CHAT"
        const val EXTRA_MESSAGE = "dev.kyralabs.pingogram.extra.REMINDER_MESSAGE"

        val SNOOZE: Duration = Duration.ofHours(1)
    }
}
