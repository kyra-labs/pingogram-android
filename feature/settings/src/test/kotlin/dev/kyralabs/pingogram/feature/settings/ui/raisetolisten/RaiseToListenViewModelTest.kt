package dev.kyralabs.pingogram.feature.settings.ui.raisetolisten

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeCompactChatListRepository
import dev.kyralabs.pingogram.core.testing.FakeComposerSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeNotesAndNamesRepository
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeVoiceTranscriber
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatListOptions
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatsPageEvent
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatsPageUiState
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatsPageViewModel
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaiseToListenViewModelTest {
    private val playbackSettings = FakePlaybackSettingsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = RaiseToListenViewModel(playbackSettings).also { runCurrent() }

    private val RaiseToListenViewModel.content get() = uiState.value as RaiseToListenUiState.Content

    private fun TestScope.send(
        viewModel: RaiseToListenViewModel,
        event: RaiseToListenEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    @Test
    fun `raise to listen is on at first, with pause when lowered off but available`() =
        runTest {
            val content = viewModel().content

            assertEquals(RaiseToListenSettings(), content.settings)
            assertTrue(content.pauseWhenLoweredEnabled)
        }

    @Test
    fun `each switch saves and shows`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, RaiseToListenEvent.SetPauseWhenLowered(true))
            send(viewModel, RaiseToListenEvent.SetEnabled(false))

            assertEquals(RaiseToListenSettings(enabled = false, pauseWhenLowered = true), viewModel.content.settings)
        }

    @Test
    fun `pause when lowered is unavailable while raise to listen is off, and keeps its choice`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, RaiseToListenEvent.SetPauseWhenLowered(true))

            send(viewModel, RaiseToListenEvent.SetEnabled(false))
            assertFalse(viewModel.content.pauseWhenLoweredEnabled)

            send(viewModel, RaiseToListenEvent.SetEnabled(true))
            assertTrue(viewModel.content.pauseWhenLoweredEnabled)
            assertTrue(viewModel.content.settings.pauseWhenLowered)
        }

    @Test
    fun `a change that cannot be saved stays as it was, with a snackbar`() =
        runTest {
            playbackSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = mutableListOf<RaiseToListenEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, RaiseToListenEvent.SetEnabled(false))

            assertTrue(viewModel.content.settings.enabled)
            assertTrue(effects.single() is RaiseToListenEffect.ShowError)
        }

    @Test
    fun `the Chats page's split row and the page's master switch stay in step`() =
        runTest {
            val page = viewModel()
            val chats =
                ChatsPageViewModel(
                    FakeComposerSettingsRepository(),
                    playbackSettings,
                    ChatListOptions(
                        FakeChatListSettingsRepository(),
                        FakeCompactChatListRepository(),
                        FakeNotesAndNamesRepository(),
                        FakeUnansweredSettingsRepository(),
                    ),
                    FakeTranscriptionSettingsRepository(),
                    FakeVoiceTranscriber(),
                ).also { runCurrent() }

            chats.onEvent(ChatsPageEvent.SetRaiseToListen(false))
            runCurrent()
            assertFalse(page.content.settings.enabled)

            send(page, RaiseToListenEvent.SetEnabled(true))
            assertTrue((chats.uiState.value as ChatsPageUiState.Content).raiseToListen.enabled)
        }
}
