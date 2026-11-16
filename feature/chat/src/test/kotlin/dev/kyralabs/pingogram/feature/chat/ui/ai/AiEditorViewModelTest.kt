package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTranslationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The composer's AI text editor ([AiEditorViewModel], `docs/DESIGN_SYSTEM.md` §6 "AI text editor"). */
@OptIn(ExperimentalCoroutinesApi::class)
class AiEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val chats = FakeChatRepository()
    private val ai = FakeAiText()
    private var consent = FakeAiConsent()
    private val savedState = SavedStateHandle()
    private val draft = FakeAiText.text("Hello there, I wanted to ask about the meetup.")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        chats.setChats(ChatFixtures.chat(id = CHAT))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): AiEditorViewModel {
        val viewModel =
            AiEditorViewModel(CHAT, savedState, chats, ai, consent, FakeAppLanguage(), FakeTranslationRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isAvailable.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.opened(): AiEditorViewModel =
        viewModel().also {
            it.onEvent(AiEditorEvent.Open(draft))
            runCurrent()
        }

    private fun AiEditorViewModel.editor() = sheet.value as AiEditorUiState.Editor

    @Test
    fun `the first use explains first, and sends nothing`() =
        runTest(dispatcher) {
            consent = FakeAiConsent(read = false)
            val viewModel = opened()

            assertEquals(AiEditorUiState.Consent, viewModel.sheet.value)
            viewModel.onEvent(AiEditorEvent.AcceptConsent)
            runCurrent()

            assertTrue(consent.acknowledged.value)
            assertTrue(viewModel.sheet.value is AiEditorUiState.Editor)
            assertTrue(ai.requests.isEmpty())
        }

    @Test
    fun `opening sends nothing until the action's tap`() =
        runTest(dispatcher) {
            val viewModel = opened()

            assertEquals(AiMode.Fix, viewModel.editor().mode)
            assertEquals(AiResult.Idle, viewModel.editor().result)
            assertTrue(ai.requests.isEmpty())
        }

    @Test
    fun `fixing sends the text and shows the result`() =
        runTest(dispatcher) {
            val viewModel = opened()
            val answer = CompletableDeferred<Outcome<FormattedText>>()
            ai.answer = answer

            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()
            assertEquals(AiResult.Working, viewModel.editor().result)

            answer.complete(Outcome.Success(FakeAiText.text("Hello there! I wanted to ask about the meetup.")))
            runCurrent()

            assertEquals(listOf(AiRequest(draft.text, "fix")), ai.requests)
            val done = viewModel.editor().result as AiResult.Done
            assertEquals("Hello there! I wanted to ask about the meetup.", done.text.text)
        }

    @Test
    fun `a rewrite takes the first style until the user picks another, with emoji when asked`() =
        runTest(dispatcher) {
            val viewModel = opened()

            viewModel.onEvent(AiEditorEvent.SetMode(AiMode.Rewrite))
            runCurrent()
            assertEquals("formal", viewModel.editor().styleName)
            viewModel.onEvent(AiEditorEvent.SetStyle("short"))
            viewModel.onEvent(AiEditorEvent.SetAddEmoji(true))
            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()

            assertEquals(listOf(AiRequest(draft.text, "compose", "short", null, addEmoji = true)), ai.requests)
        }

    @Test
    fun `translating goes into the reader's language by default, keeping the text's style`() =
        runTest(dispatcher) {
            val viewModel = opened()

            viewModel.onEvent(AiEditorEvent.SetMode(AiMode.Translate))
            runCurrent()
            assertEquals("de", viewModel.editor().language)
            assertNull(viewModel.editor().styleName)
            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()

            assertEquals(listOf(AiRequest(draft.text, "compose", null, "de")), ai.requests)
        }

    @Test
    fun `a tap right after a choice sends that choice`() =
        runTest(dispatcher) {
            val viewModel = opened()

            viewModel.onEvent(AiEditorEvent.SetMode(AiMode.Rewrite))
            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()

            assertEquals(listOf(AiRequest(draft.text, "compose", "formal")), ai.requests)
        }

    @Test
    fun `use hands the result and the original to the composer, and closes`() =
        runTest(dispatcher) {
            val viewModel = opened()
            val effects = mutableListOf<AiEditorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(effects::add) }

            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()
            viewModel.onEvent(AiEditorEvent.Use)
            runCurrent()

            assertEquals(listOf<AiEditorEffect>(AiEditorEffect.Use(FakeAiText.text("Done."), draft)), effects)
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `changing a choice drops a result made with the old one`() =
        runTest(dispatcher) {
            val viewModel = opened()
            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()

            viewModel.onEvent(AiEditorEvent.SetMode(AiMode.Rewrite))
            runCurrent()

            assertEquals(AiResult.Idle, viewModel.editor().result)
        }

    @Test
    fun `Telegram's limit is shown as the result's error`() =
        runTest(dispatcher) {
            ai.answer = CompletableDeferred(FakeAiText.LIMIT_REACHED)
            val viewModel = opened()

            viewModel.onEvent(AiEditorEvent.Run)
            runCurrent()

            assertEquals(AiResult.Failed(DomainError.AiEditLimitReached), viewModel.editor().result)
        }

    @Test
    fun `a secret chat offers no AI editor`() =
        runTest(dispatcher) {
            chats.setChats(ChatFixtures.chat(id = CHAT, type = ChatType.Secret(7, ChatFixtures.ADA)))
            val viewModel = viewModel()

            viewModel.onEvent(AiEditorEvent.Open(draft))
            runCurrent()

            assertFalse(viewModel.isAvailable.value)
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `after process death the editor asks for the composer's text again, with the choices kept`() =
        runTest(dispatcher) {
            val first = opened()
            first.onEvent(AiEditorEvent.SetMode(AiMode.Rewrite))

            val restored = viewModel()

            assertTrue(restored.needsText)
            restored.onEvent(AiEditorEvent.Open(draft))
            runCurrent()
            assertEquals(AiMode.Rewrite, restored.editor().mode)
        }

    @Test
    fun `closing forgets the text`() =
        runTest(dispatcher) {
            val viewModel = opened()

            viewModel.onEvent(AiEditorEvent.Dismiss)
            runCurrent()

            assertNull(viewModel.sheet.value)
            assertFalse(viewModel.needsText)
        }

    private companion object {
        const val CHAT = 42L
    }
}
