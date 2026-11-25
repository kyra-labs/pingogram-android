package dev.kyralabs.pingogram.feature.settings.ui.chats

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeCompactChatListRepository
import dev.kyralabs.pingogram.core.testing.FakeComposerSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeNotesAndNamesRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsPageViewModelTest {
    private val composerSettings = FakeComposerSettingsRepository()
    private val playbackSettings = FakePlaybackSettingsRepository()
    private val chatListSettings = FakeChatListSettingsRepository()
    private val compactChatList = FakeCompactChatListRepository()
    private val notesAndNames = FakeNotesAndNamesRepository()
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
        ChatsPageViewModel(
            composerSettings,
            playbackSettings,
            ChatListOptions(chatListSettings, compactChatList, notesAndNames),
            transcriptionSettings,
            transcriber,
        ).also { runCurrent() }

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
    fun `messages go with sound at first, and sending silently by default saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.content.sendsSilently)

            send(viewModel, ChatsPageEvent.SetSendsSilently(true))
            assertTrue(viewModel.content.sendsSilently)
            assertTrue(composerSettings.silent.value)

            send(viewModel, ChatsPageEvent.SetSendsSilently(false))
            assertFalse(viewModel.content.sendsSilently)
        }

    @Test
    fun `sending silently that cannot be saved stays off, with a snackbar`() =
        runTest {
            composerSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatsPageEvent.SetSendsSilently(true))

            assertFalse(viewModel.content.sendsSilently)
            assertTrue(effects.single() is ChatsPageEffect.ShowError)
        }

    @Test
    fun `bot commands go at once at first, and confirming them saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.content.confirmsBotCommands)

            send(viewModel, ChatsPageEvent.SetConfirmsBotCommands(true))
            assertTrue(viewModel.content.confirmsBotCommands)
            assertTrue(composerSettings.confirm.value)

            send(viewModel, ChatsPageEvent.SetConfirmsBotCommands(false))
            assertFalse(viewModel.content.confirmsBotCommands)
        }

    @Test
    fun `confirming bot commands that cannot be saved stays off, with a snackbar`() =
        runTest {
            composerSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatsPageEvent.SetConfirmsBotCommands(true))

            assertFalse(viewModel.content.confirmsBotCommands)
            assertTrue(effects.single() is ChatsPageEffect.ShowError)
        }

    @Test
    fun `undo and redo show at first, and turning them off saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.content.showsUndoRedo)

            send(viewModel, ChatsPageEvent.SetShowsUndoRedo(false))
            assertFalse(viewModel.content.showsUndoRedo)
            assertFalse(composerSettings.undo.value)

            send(viewModel, ChatsPageEvent.SetShowsUndoRedo(true))
            assertTrue(viewModel.content.showsUndoRedo)
        }

    @Test
    fun `undo and redo that cannot be saved stay on, with a snackbar`() =
        runTest {
            composerSettings.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatsPageEvent.SetShowsUndoRedo(false))

            assertTrue(viewModel.content.showsUndoRedo)
            assertTrue(effects.single() is ChatsPageEffect.ShowError)
        }

    @Test
    fun `the drafts button shows at first, and turning it off saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.content.chatList.showDraftsButton)

            send(viewModel, ChatsPageEvent.SetShowDraftsButton(false))
            assertFalse(viewModel.content.chatList.showDraftsButton)

            send(viewModel, ChatsPageEvent.SetShowDraftsButton(true))
            assertTrue(viewModel.content.chatList.showDraftsButton)
        }

    @Test
    fun `the chat list is not compact at first, and turning it on saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.content.compactChatList)

            send(viewModel, ChatsPageEvent.SetCompactChatList(true))
            assertTrue(viewModel.content.compactChatList)
            assertTrue(compactChatList.enabled.value)

            send(viewModel, ChatsPageEvent.SetCompactChatList(false))
            assertFalse(viewModel.content.compactChatList)
        }

    @Test
    fun `notes and names show at first, and turning them off saves and shows`() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.content.notesAndNames)

            send(viewModel, ChatsPageEvent.SetNotesAndNames(false))
            assertFalse(viewModel.content.notesAndNames)
            assertFalse(notesAndNames.enabled.value)

            send(viewModel, ChatsPageEvent.SetNotesAndNames(true))
            assertTrue(viewModel.content.notesAndNames)
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
