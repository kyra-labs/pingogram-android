package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibStorageRepository
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorageViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): DataStorageViewModel =
        DataStorageViewModel(handle, TdlibStorageRepository(client)).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: DataStorageViewModel): List<DataStorageEffect> {
        val effects = mutableListOf<DataStorageEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: DataStorageViewModel,
        vararg events: DataStorageEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun answerUsage(
        filesSize: Long,
        fileCount: Int = 10,
        databaseSize: Long = 1_000,
    ) = client.on(
        TdApi.GetStorageStatisticsFast::class,
        TdResult.Success(TdApi.StorageStatisticsFast(filesSize, fileCount, databaseSize, 0, 0)),
    )

    private fun failUsage(error: TdError) = client.on(TdApi.GetStorageStatisticsFast::class, TdResult.Failure(error))

    private fun answerClearCache(freed: Long) =
        client.on(TdApi.OptimizeStorage::class, TdResult.Success(TdApi.StorageStatistics(freed, 3, emptyArray())))

    private fun usage(filesSize: Long) =
        StorageUsageState.Content(StorageUsage(filesSize = filesSize, fileCount = 10, databaseSize = 1_000))

    private val DataStorageViewModel.state get() = uiState.value

    @Test
    fun `is loading until TDLib reports the storage usage`() =
        runTest {
            client.on(TdApi.GetStorageStatisticsFast::class) {
                CompletableDeferred<TdResult<TdApi.StorageStatisticsFast>>().await()
            }

            assertEquals(DataStorageUiState(), viewModel().state)
        }

    @Test
    fun `shows TDLib's storage usage`() =
        runTest {
            answerUsage(filesSize = 5_000, fileCount = 4, databaseSize = 700)

            assertEquals(
                DataStorageUiState(usage = StorageUsageState.Content(StorageUsage(5_000, 4, 700))),
                viewModel().state,
            )
        }

    @Test
    fun `shows why the storage usage could not be loaded`() =
        runTest {
            failUsage(TdError(429, "Too Many Requests: retry after 3"))

            assertEquals(StorageUsageState.Error(DomainError.FloodWait(3)), viewModel().state.usage)
        }

    @Test
    fun `no answer in time is a network error`() =
        runTest {
            client.on(TdApi.GetStorageStatisticsFast::class) {
                CompletableDeferred<TdResult<TdApi.StorageStatisticsFast>>().await()
            }
            val viewModel = viewModel()

            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(StorageUsageState.Error(DomainError.Network), viewModel.state.usage)
        }

    @Test
    fun `retry loads the storage usage again`() =
        runTest {
            failUsage(TdError(500, "Internal"))
            val viewModel = viewModel()
            answerUsage(filesSize = 5_000)

            send(viewModel, DataStorageEvent.Retry)

            assertEquals(usage(filesSize = 5_000), viewModel.state.usage)
            assertEquals(2, client.sentOf<TdApi.GetStorageStatisticsFast>().size)
        }

    @Test
    fun `retry does nothing while the usage shows`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()

            send(viewModel, DataStorageEvent.Retry)

            assertEquals(1, client.sentOf<TdApi.GetStorageStatisticsFast>().size)
        }

    @Test
    fun `clear cache asks for confirmation first`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()

            send(viewModel, DataStorageEvent.ClearCache)

            assertTrue(viewModel.state.isClearCacheDialogOpen)
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }

    @Test
    fun `dismissing the confirmation clears nothing`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.DismissClearCache)

            assertFalse(viewModel.state.isClearCacheDialogOpen)
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }

    @Test
    fun `confirming clears the cache and shows progress until TDLib answers`() =
        runTest {
            answerUsage(filesSize = 5_000)
            client.on(TdApi.OptimizeStorage::class) { CompletableDeferred<TdResult<TdApi.StorageStatistics>>().await() }
            val viewModel = viewModel()

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            assertEquals(1, client.sentOf<TdApi.OptimizeStorage>().size)
            assertEquals(
                DataStorageUiState(usage = usage(filesSize = 5_000), isClearingCache = true),
                viewModel.state,
            )
        }

    @Test
    fun `confirming twice while clearing clears once`() =
        runTest {
            answerUsage(filesSize = 5_000)
            client.on(TdApi.OptimizeStorage::class) { CompletableDeferred<TdResult<TdApi.StorageStatistics>>().await() }
            val viewModel = viewModel()

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)
            send(viewModel, DataStorageEvent.ConfirmClearCache, DataStorageEvent.ClearCache)

            assertEquals(1, client.sentOf<TdApi.OptimizeStorage>().size)
            assertFalse(viewModel.state.isClearCacheDialogOpen)
        }

    @Test
    fun `after clearing it shows how much was freed and the new usage`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            answerClearCache(freed = 4_000)
            answerUsage(filesSize = 1_000)

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            assertEquals(listOf(DataStorageEffect.CacheCleared(freedBytes = 4_000)), effects)
            assertEquals(DataStorageUiState(usage = usage(filesSize = 1_000)), viewModel.state)
        }

    @Test
    fun `the usage shown stays when it cannot be loaded after clearing`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()
            answerClearCache(freed = 4_000)
            failUsage(TdError(500, "Internal"))

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            assertEquals(DataStorageUiState(usage = usage(filesSize = 5_000)), viewModel.state)
        }

    @Test
    fun `a failed clearing shows the error and allows another try`() =
        runTest {
            answerUsage(filesSize = 5_000)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            client.on(TdApi.OptimizeStorage::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")))

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            assertEquals(listOf(DataStorageEffect.ShowError(DomainError.FloodWait(9))), effects)
            assertEquals(DataStorageUiState(usage = usage(filesSize = 5_000)), viewModel.state)

            answerClearCache(freed = 1)
            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)
            assertEquals(2, client.sentOf<TdApi.OptimizeStorage>().size)
        }

    @Test
    fun `clearing without an answer in time shows a network error`() =
        runTest {
            answerUsage(filesSize = 5_000)
            client.on(TdApi.OptimizeStorage::class) { CompletableDeferred<TdResult<TdApi.StorageStatistics>>().await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(listOf(DataStorageEffect.ShowError(DomainError.Network)), effects)
            assertFalse(viewModel.state.isClearingCache)
        }

    @Test
    fun `a silent error shows nothing`() =
        runTest {
            answerUsage(filesSize = 5_000)
            client.on(TdApi.OptimizeStorage::class, TdResult.Failure(TdError(406, "Silent")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            assertTrue(effects.isEmpty())
            assertFalse(viewModel.state.isClearingCache)
        }

    @Test
    fun `the open confirmation survives process death`() =
        runTest {
            answerUsage(filesSize = 5_000)
            send(viewModel(), DataStorageEvent.ClearCache)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertTrue(restored.state.isClearCacheDialogOpen)
            assertEquals(usage(filesSize = 5_000), restored.state.usage)
        }

    @Test
    fun `a clearing in progress is not restored after process death`() =
        runTest {
            answerUsage(filesSize = 5_000)
            client.on(TdApi.OptimizeStorage::class) { CompletableDeferred<TdResult<TdApi.StorageStatistics>>().await() }
            send(viewModel(), DataStorageEvent.ClearCache, DataStorageEvent.ConfirmClearCache)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(DataStorageUiState(usage = usage(filesSize = 5_000)), restored.state)
        }

    private companion object {
        /** `REQUEST_TIMEOUT` of `core:data`. */
        val REQUEST_TIMEOUT = 30.seconds
    }
}
