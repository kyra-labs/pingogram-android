package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the birthday reminders' rings scheduled (`REQUIREMENTS.md` FR-X18), as [ReminderAlarms] does the message
 * reminders': one-time work per reminder at its next ring ([nextBirthdayRing]), for every account on the device, in
 * step with its reminders; the work of a deleted reminder, and all of an account's when it leaves, is cancelled. Each
 * ring schedules the next year's ([BirthdayReminderWorker]). No alarm and no exact-alarm permission.
 *
 * [start] waits [START_DELAY] first: scheduling starts WorkManager, which the app's start leaves out.
 */
@Singleton
class BirthdayReminderAlarms internal constructor(
    private val accounts: Flow<List<BirthdayReminderAccount>>,
    private val work: BirthdayReminderWork,
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
                list.map { BirthdayReminderAccount(it.slot, it.repositories.birthdayReminderRepository().reminders) }
            },
        work = WorkManagerBirthdayReminderWork(context),
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

    private fun follow(current: List<BirthdayReminderAccount>) {
        val slots = current.map { it.slot }.toSet()
        (followed.keys - slots).forEach { slot ->
            followed.remove(slot)?.cancel()
            scope.launch { work.sync(slot, emptyMap()) }
        }
        current.filter { it.slot !in followed }.forEach { account ->
            followed[account.slot] =
                scope.launch {
                    account.reminders
                        .map { reminders ->
                            val now = clock.now()
                            reminders.associateWith { nextBirthdayRing(it, now, clock.zone()) }
                        }
                        // Nothing to schedule or cancel while an account never had a reminder: WorkManager stays
                        // unstarted.
                        .dropWhile { it.isEmpty() }
                        .collect { rings -> work.sync(account.slot, rings) }
                }
        }
    }

    internal companion object {
        /** How long after the process started the reminders are first scheduled. */
        val START_DELAY = 5.seconds
    }
}

/** An account on the device, by its slot, and its birthday reminders. */
internal class BirthdayReminderAccount(
    val slot: Int,
    val reminders: Flow<List<BirthdayReminder>>,
)
