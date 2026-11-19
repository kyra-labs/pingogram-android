package dev.kyralabs.pingogram.feature.settings.ui.chats

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeComposerSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeVoiceTranscriber
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsPageViewModelTest {
    private val composerSettings = FakeComposerSettingsRepository()
    private val playbackSettings = FakePlaybackSettingsRepository()
    private val chatListSettings = FakeChatListSettingsRepository()
    private val transcriptionSettings = FakeTranscriptionSettingsRepository()
    private val transcriber = FakeVoiceTranscriber()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() =
        ChatsPageViewModel(composerSettings, playbackSettings, chatListSettings, transcriptionSettings, transcriber)
            .also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ChatsPageViewModel): List<ChatsPageEffect> {
        val effects = mutableListOf<ChatsPageEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ChatsPageViewModel,
        event: ChatsPageEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val ChatsPageViewModel.content get() = uiState.value as ChatsPageUiState.Content

    @Test
    fun `shows the saved settings with their defaults`() =
        runTest {
            assertEquals(ChatsPageUiState.Content(transcribeAutomatically = false), viewModel().content)
        }

    @Test
    fun `Enter sends on a keyboard and not on the screen at first, and a change saves and shows`() =
        runTest {
            val viewModel = viewModel()

            val changed = SendKeys(hardwareKeyboard = false, onScreenKeyboard = true)
            send(viewModel, ChatsPageEvent.SetSendKeys(changed))

            assertEquals(changed, viewModel.content.sendKeys)
        }

    @Test
    fun `a send key that cannot be saved stays as it was, with a snackbar`() =
        runTest {
            composerSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatsPageEvent.SetSendKeys(SendKeys(onScreenKeyboard = true)))

            assertEquals(SendKeys(), viewModel.content.sendKeys)
            assertTrue(effects.single() is ChatsPageEffect.ShowError)
        }

    @Test
    fun `raise to listen is on at first, and the split row's switch saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(RaiseToListenSettings(), viewModel.content.raiseToListen)

            send(viewModel, ChatsPageEvent.SetRaiseToListen(false))

            assertEquals(RaiseToListenSettings(enabled = false), viewModel.content.raiseToListen)
        }

    @Test
    fun `raise to listen that cannot be saved stays as it was, with a snackbar`() =
        runTest {
            playbackSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatsPageEvent.SetRaiseToListen(false))

            assertTrue(viewModel.content.raiseToListen.enabled)
            assertTrue(effects.single() is ChatsPageEffect.ShowError)
        }

    @Test
    fun `voice messages keep their silences at first, and skip silence saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(false, viewModel.content.skipSilence)

            send(viewModel, ChatsPageEvent.SetSkipSilence(true))

            assertEquals(true, viewModel.content.skipSilence)
        }

    @Test
    fun `the swipes the Swipe actions row sums up follow the saved ones`() =
        runTest {
            val viewModel = viewModel()

            chatListSettings.setSwipeActions(start = SwipeAction.Pin, end = SwipeAction.Delete)
            runCurrent()

            assertEquals(SwipeAction.Pin, viewModel.content.chatList.startSwipe)
        }

    @Test
    fun `settings saved elsewhere show on the page, as they keep their keys`() =
        runTest {
            val viewModel = viewModel()

            composerSettings.setSendKeys(SendKeys(hardwareKeyboard = false))
            playbackSettings.setSkipSilence(true)
            runCurrent()

            assertEquals(SendKeys(hardwareKeyboard = false), viewModel.content.sendKeys)
            assertTrue(viewModel.content.skipSilence)
        }

    @Test
    fun `the transcription row's switch saves and shows automatic transcription`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatsPageEvent.SetTranscribeAutomatically(true))

            assertEquals(true, viewModel.content.transcribeAutomatically)
            assertTrue(transcriptionSettings.settings.value.automatic)
        }

    @Test
    fun `a phone that cannot transcribe gets no switch on the transcription row`() =
        runTest {
            transcriber.isAvailable = false

            assertEquals(null, viewModel().content.transcribeAutomatically)
        }
}
