package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/** WorkManager's side of the birthday reminders' scheduling, a seam for tests. */
internal interface BirthdayReminderWork {
    /** Makes [rings], each reminder at its time, the only birthday reminders of account [slot] with work scheduled. */
    suspend fun sync(
        slot: Int,
        rings: Map<BirthdayReminder, Instant>,
    )
}

internal class WorkManagerBirthdayReminderWork(
    private val context: Context,
    private val now: () -> Instant = Instant::now,
) : BirthdayReminderWork {
    override suspend fun sync(
        slot: Int,
        rings: Map<BirthdayReminder, Instant>,
    ) {
        val manager = WorkManager.getInstance(context)
        val wanted = rings.entries.associateBy { workName(slot, it.key, it.value) }
        manager
            .getWorkInfosByTagFlow(accountTag(slot))
            .first()
            .filter { it.state == WorkInfo.State.ENQUEUED && it.tags.none { tag -> tag in wanted } }
            .forEach { manager.cancelWorkById(it.id) }
        for ((name, ring) in wanted) enqueue(slot, ring.key, ring.value, name)
    }

    /** Schedules [reminder] of account [slot] to ring at [at], kept as it is when it already is. */
    fun enqueue(
        slot: Int,
        reminder: BirthdayReminder,
        at: Instant,
        name: String = workName(slot, reminder, at),
    ) {
        val request =
            OneTimeWorkRequestBuilder<BirthdayReminderWorker>()
                .setInitialDelay(Duration.between(now(), at).coerceAtLeast(Duration.ZERO))
                .setInputData(BirthdayReminderWorker.inputOf(slot, reminder))
                .addTag(accountTag(slot))
                .addTag(name)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    internal companion object {
        fun accountTag(slot: Int) = "birthday_reminders_$slot"

        /** The work of [reminder] ringing at [at]: a new name each year and for a new day. */
        fun workName(
            slot: Int,
            reminder: BirthdayReminder,
            at: Instant,
        ) = "birthday_reminder_${slot}_${reminder.userId}_${at.epochSecond}"
    }
}
