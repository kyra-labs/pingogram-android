package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the message reminders' rings scheduled (the owner's board 29): one-time work per reminder that has not rung
 * yet ([ReminderWorker]), for every account on the device, in step with its reminders.
 *
 * - **Scheduled**: each reminder not done whose time is still ahead, as unique work named after the account, the
 *   message and the time, kept as it is when it exists ([ExistingWorkPolicy.KEEP]); a reminder moved to another time
 *   (snoozed, edited) gets new work, and the old one is cancelled.
 * - **Cancelled**: the work of reminders deleted, done or moved, and all of an account's when it logs out (its
 *   reminders go) or leaves the device.
 * - **When**: WorkManager runs the work once its delay is over, also after a restart of the phone; Android may hold it
 *   back for a while in Doze. No alarm and no exact-alarm permission (`docs/PERFORMANCE.md` §6).
 *
 * [start] waits [START_DELAY] first: scheduling starts WorkManager, which the app's start leaves out (§2).
 */
@Singleton
class ReminderAlarms internal constructor(
    private val accounts: Flow<List<ReminderAccount>>,
    private val work: ReminderWork,
    private val clock: WallClock,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        graphs: AccountGraphs,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        accounts =
            graphs.accounts.map { list ->
                list.map { ReminderAccount(it.slot, it.repositories.reminderRepository().reminders) }
            },
        work = WorkManagerReminderWork(context),
        clock = clock,
        scope = scope,
    )

    private val followed = mutableMapOf<Int, Job>()

    /** Follows the accounts from [START_DELAY] on, in every process; once. */
    fun start() {
        scope.launch {
            delay(START_DELAY)
            accounts.collect(::follow)
        }
    }

    private fun follow(current: List<ReminderAccount>) {
        val slots = current.map { it.slot }.toSet()
        (followed.keys - slots).forEach { slot ->
            followed.remove(slot)?.cancel()
            scope.launch { work.sync(slot, emptyList()) }
        }
        current.filter { it.slot !in followed }.forEach { account ->
            followed[account.slot] =
                scope.launch {
                    account.reminders.collect { reminders ->
                        val now = clock.now()
                        work.sync(account.slot, reminders.filter { !it.isDone && it.at.isAfter(now) })
                    }
                }
        }
    }

    internal companion object {
        /** How long after the process started the reminders are first scheduled. */
        val START_DELAY = 5.seconds
    }
}

/** An account on the device, by its slot, and its reminders. */
internal class ReminderAccount(
    val slot: Int,
    val reminders: Flow<List<MessageReminder>>,
)

/** WorkManager's side of [ReminderAlarms], a seam for its tests. */
internal interface ReminderWork {
    /** Makes [upcoming] the only reminders of the account in [slot] with work scheduled. */
    suspend fun sync(
        slot: Int,
        upcoming: List<MessageReminder>,
    )
}

internal class WorkManagerReminderWork(
    private val context: Context,
    private val now: () -> Instant = Instant::now,
) : ReminderWork {
    override suspend fun sync(
        slot: Int,
        upcoming: List<MessageReminder>,
    ) {
        val manager = WorkManager.getInstance(context)
        val wanted = upcoming.associateBy { workName(slot, it) }
        manager
            .getWorkInfosByTagFlow(accountTag(slot))
            .first()
            .filter { it.state == WorkInfo.State.ENQUEUED && it.tags.none { tag -> tag in wanted } }
            .forEach { manager.cancelWorkById(it.id) }
        for ((name, reminder) in wanted) {
            val request =
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now(), reminder.at).coerceAtLeast(Duration.ZERO))
                    .setInputData(ReminderWorker.inputOf(slot, reminder))
                    .addTag(accountTag(slot))
                    .addTag(name)
                    .build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        }
    }

    internal companion object {
        fun accountTag(slot: Int) = "reminders_$slot"

        /** The work of [reminder]: a new name for a new time, so moving a reminder schedules it anew. */
        fun workName(
            slot: Int,
            reminder: MessageReminder,
        ) = "reminder_${slot}_${reminder.chatId}_${reminder.messageId}_${reminder.at.epochSecond}"
    }
}
