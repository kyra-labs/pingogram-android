package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.data.repository.TdlibDataOptionsRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class DataOptionsViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueBoolean(false)))
        client.on(TdApi.SetOption::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.ClearAllDraftMessages::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        DataOptionsViewModel(handle, TdlibDataOptionsRepository(client)).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: DataOptionsViewModel): List<DataOptionsEffect> {
        val effects = mutableListOf<DataOptionsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `prefer IPv6 shows TDLib's option, is set at once, and follows its update`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(false, viewModel.uiState.value.prefersIpv6)

            viewModel.onEvent(DataOptionsEvent.SetPrefersIpv6(true))
            runCurrent()

            assertEquals(true, viewModel.uiState.value.prefersIpv6)
            val set = client.sentOf<TdApi.SetOption>().single()
            assertEquals("prefer_ipv6", set.name)
            assertEquals(true, (set.value as TdApi.OptionValueBoolean).value)

            // TDLib announces the option it set, then any later change (another app of the account's, say).
            client.emit(TdApi.UpdateOption("prefer_ipv6", TdApi.OptionValueBoolean(true)))
            runCurrent()
            assertEquals(true, viewModel.uiState.value.prefersIpv6)
            client.emit(TdApi.UpdateOption("prefer_ipv6", TdApi.OptionValueBoolean(false)))
            runCurrent()
            assertEquals(false, viewModel.uiState.value.prefersIpv6)
        }

    @Test
    fun `a refused switch flips back and says why`() =
        runTest {
            client.on(TdApi.SetOption::class, TdResult.Failure(TdError(500, "INTERNAL")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(DataOptionsEvent.SetPrefersIpv6(true))
            runCurrent()

            assertEquals(false, viewModel.uiState.value.prefersIpv6)
            assertEquals(1, effects.filterIsInstance<DataOptionsEffect.ShowError>().size)
        }

    @Test
    fun `deleting the drafts asks first, keeps the question after process death, and keeps secret chats' drafts`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)
            viewModel.onEvent(DataOptionsEvent.DeleteDrafts)
            runCurrent()
            assertTrue(viewModel.uiState.value.isDeleteDraftsDialogOpen)
            assertTrue(client.sentOf<TdApi.ClearAllDraftMessages>().isEmpty())

            val restored = viewModel(handle)
            assertTrue(restored.uiState.value.isDeleteDraftsDialogOpen)
            val effects = effectsOf(restored)
            restored.onEvent(DataOptionsEvent.ConfirmDeleteDrafts)
            runCurrent()

            assertTrue(client.sentOf<TdApi.ClearAllDraftMessages>().single().excludeSecretChats)
            assertFalse(restored.uiState.value.isDeleteDraftsDialogOpen)
            assertEquals(listOf(DataOptionsEffect.DraftsDeleted), effects)
        }

    @Test
    fun `dismissing the question deletes nothing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(DataOptionsEvent.DeleteDrafts)
            viewModel.onEvent(DataOptionsEvent.DismissDeleteDrafts)
            runCurrent()

            assertFalse(viewModel.uiState.value.isDeleteDraftsDialogOpen)
            assertTrue(client.sentOf<TdApi.ClearAllDraftMessages>().isEmpty())
        }

    @Test
    fun `the drafts show as deleting until Telegram answers, and a failure says why`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.ClearAllDraftMessages::class) { answer.await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(DataOptionsEvent.ConfirmDeleteDrafts)
            runCurrent()
            assertTrue(viewModel.uiState.value.isDeletingDrafts)

            answer.complete(TdResult.Failure(TdError(400, "BAD_REQUEST")))
            runCurrent()
            assertFalse(viewModel.uiState.value.isDeletingDrafts)
            assertEquals(1, effects.filterIsInstance<DataOptionsEffect.ShowError>().size)
            assertTrue(effects.none { it == DataOptionsEffect.DraftsDeleted })
        }

    @Test
    fun `a silent failure shows nothing`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            client.on(TdApi.ClearAllDraftMessages::class, TdResult.Failure(TdError(406, "")))

            viewModel.onEvent(DataOptionsEvent.ConfirmDeleteDrafts)
            runCurrent()

            assertEquals(emptyList<DataOptionsEffect>(), effects)
        }
}
