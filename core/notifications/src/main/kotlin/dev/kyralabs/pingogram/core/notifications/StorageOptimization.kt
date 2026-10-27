package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Schedules each account's weekly clean-up of TDLib's files ([StorageOptimizationWorker], `docs/PERFORMANCE.md` §3):
 * unique periodic work per account slot, kept as it is when it exists ([ExistingPeriodicWorkPolicy.KEEP]), so a start
 * does not move its next run. It is scheduled once the account is logged in and cancelled when it logs out or leaves
 * the device.
 *
 * [start] waits [START_DELAY] first: scheduling starts WorkManager, which the app's start leaves out (§2), so the
 * chat list's first page comes first.
 */
@Singleton
class StorageOptimization internal constructor(
    private val accounts: Flow<List<StorageAccount>>,
    private val work: StorageOptimizationWork,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        graphs: AccountGraphs,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        accounts =
            graphs.accounts.map { list ->
                list.map { StorageAccount(it.slot, it.repositories.authRepository().authStep) }
            },
        work = WorkManagerStorageOptimizationWork(context),
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

    private fun follow(current: List<StorageAccount>) {
        val slots = current.map { it.slot }.toSet()
        (followed.keys - slots).forEach { slot ->
            followed.remove(slot)?.cancel()
            work.cancel(slot)
        }
        current.filter { it.slot !in followed }.forEach { account ->
            followed[account.slot] =
                scope.launch {
                    account.authSteps.collect { step ->
                        when (step) {
                            AuthStep.Ready -> {
                                work.schedule(account.slot)
                            }

                            AuthStep.LoggingOut, AuthStep.Closed -> {
                                work.cancel(account.slot)
                            }

                            else -> {}
                        }
                    }
                }
        }
    }

    internal companion object {
        /** How long after the process started the clean-up is first scheduled. */
        val START_DELAY: Duration = 5.seconds
    }
}

/** An account on the device, by its slot, and its login steps. */
internal class StorageAccount(
    val slot: Int,
    val authSteps: Flow<AuthStep>,
)

/** WorkManager's side of [StorageOptimization], a seam for its tests. */
internal interface StorageOptimizationWork {
    fun schedule(slot: Int)

    fun cancel(slot: Int)
}

internal class WorkManagerStorageOptimizationWork(
    private val context: Context,
) : StorageOptimizationWork {
    override fun schedule(slot: Int) {
        val request =
            PeriodicWorkRequestBuilder<StorageOptimizationWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(constraints())
                .setInputData(StorageOptimizationWorker.inputOf(slot))
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(workName(slot), ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel(slot: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(slot))
    }

    internal companion object {
        const val INTERVAL_DAYS = 7L

        fun workName(slot: Int) = "storage_optimization_$slot"

        /**
         * While the phone charges and is idle (Doze's idle, API 23+), whatever the free space: low storage is when
         * the clean-up helps most.
         */
        fun constraints(): Constraints =
            Constraints
                .Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresStorageNotLow(false)
                .build()
    }
}
