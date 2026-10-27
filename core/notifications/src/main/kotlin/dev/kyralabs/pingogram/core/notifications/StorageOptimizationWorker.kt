package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * An account's weekly clean-up of TDLib's files ([StorageRepository.optimizeStorage], `docs/PERFORMANCE.md` §3), as
 * [StorageOptimization] schedules it: while the phone charges and is idle, once a week. It never runs while the
 * account's transfers ([TransferRepository]) or a chat export are on their way, whose files it could delete: it tries
 * again instead ([Result.retry]), the next time the phone charges and is idle (an idle job takes no backoff of its
 * own). An account that left the device, or is not logged in, has nothing to clean.
 */
@HiltWorker
class StorageOptimizationWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val accounts: suspend (slot: Int) -> AccountStorage?,
        private val exportRunning: suspend () -> Boolean,
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            graphs: AccountGraphs,
        ) : this(
            context = context,
            parameters = parameters,
            accounts = { slot ->
                graphs.accounts
                    .first()
                    .firstOrNull { it.slot == slot }
                    ?.repositories
                    ?.let { AccountStorage(it.authRepository(), it.transferRepository(), it.storageRepository()) }
            },
            exportRunning = {
                WorkManager
                    .getInstance(context)
                    .getWorkInfosByTagFlow(WorkManagerChatExportJobs.TAG)
                    .first()
                    .any { !it.state.isFinished }
            },
        )

        override suspend fun doWork(): Result {
            val slot = inputData.getInt(SLOT, NO_SLOT)
            val account = if (slot == NO_SLOT) null else accounts(slot)
            val loggedIn = account != null && withTimeoutOrNull(LOGIN_TIMEOUT) { account.auth.awaitLoggedIn() } == true
            return when {
                account == null || !loggedIn -> Result.success()
                !account.transfers.transfers.value.isEmpty || exportRunning() -> Result.retry()
                account.storage.optimizeStorage() is Outcome.Success -> Result.success()
                else -> Result.retry()
            }
        }

        internal companion object {
            private const val SLOT = "slot"
            private const val NO_SLOT = -1

            /** How long TDLib may take to open its database and report the login. */
            val LOGIN_TIMEOUT = 30.seconds

            fun inputOf(slot: Int) = workDataOf(SLOT to slot)
        }
    }

/** What the clean-up needs of an account: its login, its transfers and its storage. */
internal class AccountStorage(
    val auth: AuthRepository,
    val transfers: TransferRepository,
    val storage: StorageRepository,
)
