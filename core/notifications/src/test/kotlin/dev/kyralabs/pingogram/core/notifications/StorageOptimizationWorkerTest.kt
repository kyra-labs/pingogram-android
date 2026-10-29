package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStorageRepository
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.FakeTransferRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** An account's weekly clean-up (`docs/PERFORMANCE.md` §3), and when it waits instead. */
@RunWith(RobolectricTestRunner::class)
class StorageOptimizationWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val client = FakeTdlibClient()
    private val transfers = FakeTransferRepository()

    private fun TestScope.account() =
        AccountStorage(
            auth = TdlibAuthRepository(client, StandardTestDispatcher(testScheduler)),
            transfers = transfers,
            storage = TdlibStorageRepository(client),
        )

    private suspend fun runWorker(
        slot: Int = 0,
        exportRunning: Boolean = false,
        accounts: suspend (Int) -> AccountStorage?,
    ): ListenableWorker.Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = StorageOptimizationWorker(appContext, workerParameters, accounts) { exportRunning }
            }
        return TestListenableWorkerBuilder<StorageOptimizationWorker>(context, StorageOptimizationWorker.inputOf(slot))
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private fun loggedInWithoutPinnedChats() {
        client.authState.value = AuthState.Ready
        client.on(TdApi.GetChats::class, TdResult.Success(TdApi.Chats(0, longArrayOf())))
        client.on(TdApi.OptimizeStorage::class, TdResult.Success(TdApi.StorageStatistics(1_000, 3, emptyArray())))
    }

    @Test
    fun `a logged-in account with nothing on its way is cleaned up`() =
        runTest {
            loggedInWithoutPinnedChats()
            val account = account()

            val result = runWorker(slot = 1) { slot -> account.takeIf { slot == 1 } }

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, client.sentOf<TdApi.OptimizeStorage>().size)
        }

    @Test
    fun `a transfer on its way waits for a later try`() =
        runTest {
            loggedInWithoutPinnedChats()
            transfers.transfers.value = Transfers(downloads = 1)
            val account = account()

            assertEquals(ListenableWorker.Result.retry(), runWorker { account })
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }

    @Test
    fun `a chat export on its way waits for a later try`() =
        runTest {
            loggedInWithoutPinnedChats()
            val account = account()

            assertEquals(ListenableWorker.Result.retry(), runWorker(exportRunning = true) { account })
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }

    @Test
    fun `a clean-up TDLib refuses is tried again later`() =
        runTest {
            loggedInWithoutPinnedChats()
            client.on(TdApi.OptimizeStorage::class, TdResult.Failure(TdError(500, "Request aborted")))
            val account = account()

            assertEquals(ListenableWorker.Result.retry(), runWorker { account })
        }

    @Test
    fun `an account signed out or gone has nothing to clean`() =
        runTest {
            client.authState.value = AuthState.WaitPhoneNumber
            val account = account()

            assertEquals(ListenableWorker.Result.success(), runWorker { account })
            assertEquals(ListenableWorker.Result.success(), runWorker { null })
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }
}
