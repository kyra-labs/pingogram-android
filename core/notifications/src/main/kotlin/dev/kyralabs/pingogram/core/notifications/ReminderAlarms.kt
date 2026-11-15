package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Rings each account's reminders (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29): follows them
 * and keeps one WorkManager one-time work ([ReminderWorker]) per reminder not done yet, due at its time, so it rings
 * after the app is closed or the phone restarted. A reminder whose time changed is scheduled again, one deleted or done
 * cancelled; an account that leaves the device takes its work with it.
 *
 * No exact alarm (owner decision 2026-11-15: no `SCHEDULE_EXACT_ALARM`): WorkManager runs the work at its time while
 * the phone is awake, but Doze defers it to the next maintenance window (minutes at first, up to a few hours deep in
 * the night), and App Standby's rare bucket to once a day. A reminder rings late then, never early.
 *
 * [start] waits [START_DELAY] first: scheduling starts WorkManager, which the app's start leaves out
 * (`docs/PERFORMANCE.md` §2). Work already scheduled is left as it is, so a start writes nothing.
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

    /** Follows the accounts' reminders from [START_DELAY] on, in every process; once. */
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
            scope.launch { work.cancelAll(slot) }
        }
        current.filter { it.slot !in followed }.forEach { account ->
            followed[account.slot] = scope.launch { account.reminders.collect { sync(account.slot, it) } }
        }
    }

    private suspend fun sync(
        slot: Int,
        reminders: List<Reminder>,
    ) {
        val due = reminders.filterNot { it.done }.associateBy { it.key }
        val scheduled = work.scheduled(slot)
        (scheduled.keys - due.keys).forEach { work.cancel(slot, it) }
        due.forEach { (key, reminder) ->
            if (scheduled[key] != reminder.at) {
                val delayMillis = (reminder.at * MILLIS_PER_SECOND - clock.now().toEpochMilli()).coerceAtLeast(0)
                work.schedule(slot, reminder, delayMillis)
            }
        }
    }

    internal companion object {
        /** How long after the process started the reminders are first followed. */
        val START_DELAY: Duration = 5.seconds

        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/** A reminder's key among its account's work: its chat and message. */
internal val Reminder.key: String get() = "${chatId}_$messageId"

/** An account on the device, by its slot, and its reminders. */
internal class ReminderAccount(
    val slot: Int,
    val reminders: Flow<List<Reminder>>,
)

/** WorkManager's side of [ReminderAlarms], a seam for its tests. */
internal interface ReminderWork {
    /** The reminders of account [slot] that have work waiting, by key, with the time each was scheduled for. */
    suspend fun scheduled(slot: Int): Map<String, Long>

    fun schedule(
        slot: Int,
        reminder: Reminder,
        delayMillis: Long,
    )

    fun cancel(
        slot: Int,
        key: String,
    )

    suspend fun cancelAll(slot: Int)
}

internal class WorkManagerReminderWork(
    private val context: Context,
) : ReminderWork {
    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun scheduled(slot: Int): Map<String, Long> =
        workManager
            .getWorkInfosByTagFlow(accountTag(slot))
            .first()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            .mapNotNull { info ->
                val key = info.tags.firstNotNullOfOrNull { it.removePrefixOrNull(KEY_PREFIX) }
                val at = info.tags.firstNotNullOfOrNull { it.removePrefixOrNull(AT_PREFIX)?.toLongOrNull() }
                if (key != null && at != null) key to at else null
            }.toMap()

    override fun schedule(
        slot: Int,
        reminder: Reminder,
        delayMillis: Long,
    ) {
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(ReminderWorker.ring(slot, reminder.chatId, reminder.messageId))
                .addTag(accountTag(slot))
                .addTag("$KEY_PREFIX${reminder.key}")
                .addTag("$AT_PREFIX${reminder.at}")
                .build()
        workManager.enqueueUniqueWork(workName(slot, reminder.key), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(
        slot: Int,
        key: String,
    ) {
        workManager.cancelUniqueWork(workName(slot, key))
    }

    override suspend fun cancelAll(slot: Int) {
        workManager.cancelAllWorkByTag(accountTag(slot))
    }

    private fun String.removePrefixOrNull(prefix: String): String? =
        if (startsWith(prefix)) removePrefix(prefix) else null

    internal companion object {
        fun accountTag(slot: Int) = "reminders_$slot"

        fun workName(
            slot: Int,
            key: String,
        ) = "reminder_${slot}_$key"

        private const val KEY_PREFIX = "reminder_key_"
        private const val AT_PREFIX = "reminder_at_"
    }
}
