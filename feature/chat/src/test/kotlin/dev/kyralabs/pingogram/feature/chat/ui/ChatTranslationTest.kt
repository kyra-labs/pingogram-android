package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.FakeTranslationRepository.Companion.translationOf
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearch
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeChatSearchRepository.Companion.found
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.ChatTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslateBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Message translation in [ChatViewModel] (`REQUIREMENTS.md` FR-C3, FR-C6): "Translate" and "Show original" in a
 * message's actions, as the device detects its language; translations in the bubbles, while they come, and dropped by
 * an edit; Telegram's refusals; the translate bar, which translates only the messages on screen, batched; closing it;
 * and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTranslationTest : ChatViewModelHarness() {
    /** Messages 1–4 of Ada, in Spanish but message 3, the user's own in English. */
    private val conversation =
        listOf(
            spanish(1, "¡Hola! ¿Qué tal?"),
            spanish(2, "Te mando la dirección."),
            message(3, isOutgoing = true, content = textContent("Thanks, see you!")),
            spanish(4, "¿Quedamos mañana?"),
        )

    private fun spanish(
        id: Long,
        text: String,
    ): Message {
        languageDetector.inLanguage("es", text)
        return message(id, content = textContent(text))
    }

    private fun textContent(text: String) = MessageContent.Text(text(text), linkPreview = null)

    /** The private chat with Ada, Telegram suggesting its translation where [translatable]. */
    private fun TestScope.translating(
        translatable: Boolean = false,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        languageDetector.inLanguage("en", "Thanks, see you!")
        chats.setChats(chat().copy(isTranslatable = translatable))
        messages.setHistory(PRIVATE_CHAT, history(*conversation.toTypedArray()))
        return viewModel(PRIVATE_CHAT, handle)
    }

    private fun TestScope.actionsOf(
        viewModel: ChatViewModel,
        messageId: Long,
    ): List<MessageAction> {
        send(viewModel, ChatEvent.ShowMessageActions(messageId))
        return checkNotNull(viewModel.state.messageActions).actions
    }

    /** The list shows [messageIds], and the batch they make is asked for. */
    private fun TestScope.visible(
        viewModel: ChatViewModel,
        vararg messageIds: Long,
    ) {
        send(viewModel, ChatEvent.MessagesVisible(messageIds.toSet()))
        advanceTimeBy(ChatTranslation.BATCH_MILLIS + 1)
        runCurrent()
    }

    private fun shownIn(
        code: String,
        messageId: Long,
    ) = BubbleTranslation.Shown(translationOf(messageId, code), "es")

    @Test
    fun `offers Translate after Copy for a message the device found in a language the user does not read`() =
        runViewModelTest {
            val viewModel = translating()

            val actions = actionsOf(viewModel, 2)

            assertEquals(actions.indexOf(MessageAction.Copy) + 1, actions.indexOf(MessageAction.Translate))
            assertTrue(translations.requests.isEmpty())
        }

    @Test
    fun `offers no translation for a message in a language the user reads, Pingogram's or the system's`() =
        runViewModelTest {
            languageDetector.preferred = listOf("en-US", "de-DE")
            languageDetector.inLanguage("de", "Message 9")
            val viewModel = translating()

            assertFalse(MessageAction.Translate in actionsOf(viewModel, 3))
            send(viewModel, ChatEvent.DismissMessageActions)
            messages.setHistory(PRIVATE_CHAT, history(*conversation.toTypedArray(), message(9)))
            runCurrent()
            assertFalse(MessageAction.Translate in actionsOf(viewModel, 9))
        }

    @Test
    fun `offers Translate where the device cannot detect languages, but not for a text without letters`() =
        runViewModelTest {
            languageDetector.canDetect = false
            messages.setHistory(PRIVATE_CHAT, history(message(5, content = textContent("👍 10:30"))))
            chats.setChats(chat())
            val viewModel = viewModel(PRIVATE_CHAT)

            assertFalse(MessageAction.Translate in actionsOf(viewModel, 5))
            send(viewModel, ChatEvent.DismissMessageActions)
            messages.setHistory(PRIVATE_CHAT, history(message(5, content = textContent("Bonjour"))))
            runCurrent()
            assertTrue(MessageAction.Translate in actionsOf(viewModel, 5))
        }

    @Test
    fun `nothing is offered where Telegram does not translate into the user's language`() =
        runViewModelTest {
            translations.unsupported = setOf("en")
            val viewModel = translating()

            val actions = actionsOf(viewModel, 2)

            assertFalse(MessageAction.Translate in actions || MessageAction.ShowOriginal in actions)
        }

    @Test
    fun `Translate shows Telegram's translation in the bubble, from the detected language, then Show original`() =
        runViewModelTest {
            val viewModel = translating()

            send(viewModel, TranslationEvent.Translate(2))

            assertEquals(listOf(Triple(PRIVATE_CHAT, 2L, "en")), translations.requests)
            val bubble = viewModel.bubble(2)
            assertEquals(shownIn("en", 2), bubble.translation)
            assertEquals(translationOf(2, "en"), bubble.text)
            assertTrue(bubble.showsTime)
            assertNull(viewModel.bubble(1).translation)
            assertEquals(MessageAction.ShowOriginal, actionsOf(viewModel, 2).single { it.isTranslation })

            send(viewModel, ChatEvent.DismissMessageActions, TranslationEvent.ShowOriginal(2))
            assertNull(viewModel.bubble(2).translation)
            assertEquals(text("Te mando la dirección."), viewModel.bubble(2).text)

            // Shown again from memory, without asking Telegram again.
            send(viewModel, TranslationEvent.Translate(2))
            assertEquals(shownIn("en", 2), viewModel.bubble(2).translation)
            assertEquals(1, translations.requests.size)
        }

    @Test
    fun `a translated bubble that the search in the chat shows marks the query's words in its translation`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(2) }
            val viewModel = translating()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.searchState.collect {} }
            send(viewModel, TranslationEvent.Translate(2), ChatSearchEvent.Open)

            viewModel.searchQuery.edit { replace(0, length, "message") }
            Snapshot.sendApplyNotifications()
            advanceTimeBy(ChatSearch.DEBOUNCE_MILLIS + 1)
            runCurrent()

            val bubble = viewModel.bubble(2)
            assertEquals(shownIn("en", 2), bubble.translation)
            assertEquals(MatchHighlight.of("message"), bubble.searchMatch)
            // "[en] message 2": the word is marked where the translation shown has it.
            assertEquals(listOf(5..11), bubble.searchMatch!!.ranges(bubble.text.text))

            send(viewModel, TranslationEvent.ShowOriginal(2))
            assertNull(viewModel.bubble(2).translation)
            assertEquals(MatchHighlight.of("message"), viewModel.bubble(2).searchMatch)
        }

    @Test
    fun `while Telegram translates, the bubble keeps its text and says so`() =
        runViewModelTest {
            val answer = CompletableDeferred<Outcome<FormattedText>>()
            translations.onTranslate = { _, _, _ -> answer.await() }
            val viewModel = translating()

            send(viewModel, TranslationEvent.Translate(2))
            assertEquals(BubbleTranslation.Translating, viewModel.bubble(2).translation)
            assertEquals(text("Te mando la dirección."), viewModel.bubble(2).text)

            answer.complete(Outcome.Success(text("I'll send you the address.")))
            runCurrent()
            assertEquals(text("I'll send you the address."), viewModel.bubble(2).text)
        }

    @Test
    fun `translations go into Pingogram's language when it has one`() =
        runViewModelTest {
            appLanguage.language.value = "pt-BR"
            val viewModel = translating()

            send(viewModel, TranslationEvent.Translate(2))

            assertEquals("pt", translations.requests.single().third)
            assertEquals(shownIn("pt", 2), viewModel.bubble(2).translation)
        }

    @Test
    fun `an edit drops the translation, and the message is no longer translated`() =
        runViewModelTest {
            val viewModel = translating()
            send(viewModel, TranslationEvent.Translate(2))
            visible(viewModel, 1, 2)

            val edited = message(2, content = textContent("Te mando la dirección nueva."))
            messages.setHistory(PRIVATE_CHAT, history(*(conversation - conversation[1] + edited).toTypedArray()))
            runCurrent()
            assertNull(viewModel.bubble(2).translation)
            assertEquals(text("Te mando la dirección nueva."), viewModel.bubble(2).text)

            visible(viewModel, 1, 2, 3)
            assertEquals(1, translations.requests.size)
            assertNull(viewModel.bubble(2).translation)
        }

    @Test
    fun `Telegram's refusals are snackbars, and the bubble shows its text again`() =
        runViewModelTest {
            val errors =
                listOf(
                    DomainError.FloodWait(30),
                    DomainError.Network,
                    DomainError.Unauthorized,
                    DomainError.PremiumRequired,
                )
            val viewModel = translating()
            viewModel.effects.test {
                for (error in errors) {
                    translations.onTranslate = { _, _, _ -> Outcome.Failure(error) }
                    send(viewModel, TranslationEvent.Translate(2))
                    assertEquals(TranslationEffect.Failed(error), awaitItem())
                    assertNull(viewModel.bubble(2).translation)
                }
            }
            // Asked once per tap, never in another way.
            assertEquals(errors.size, translations.requests.size)
        }

    @Test
    fun `the bar offers the chat's translation where Telegram suggests it and the chat is in a foreign language`() =
        runViewModelTest {
            assertEquals(TranslateBar(toLanguage = "en-US", fromLanguage = "es", isOn = false), translating(true).bar)
        }

    @Test
    fun `no bar where Telegram does not suggest it, the device cannot detect, or the user reads the chat's language`() =
        runViewModelTest {
            assertNull(translating(translatable = false).bar)

            languageDetector.canDetect = false
            assertNull(translating(translatable = true).bar)

            languageDetector.canDetect = true
            languageDetector.preferred = listOf("en-US", "es-ES")
            assertNull(translating(translatable = true).bar)
        }

    @Test
    fun `the bar translates only the foreign messages on screen, once the list settles`() =
        runViewModelTest {
            val viewModel = translating(true)
            send(viewModel, TranslationEvent.ToggleChat)
            assertEquals(true, viewModel.bar?.isOn)

            send(viewModel, ChatEvent.MessagesVisible(setOf(1)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1, 2)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(2, 3, 4)))
            assertTrue(translations.requests.isEmpty())
            advanceTimeBy(ChatTranslation.BATCH_MILLIS + 1)
            runCurrent()

            // Message 3 is in English; message 1 left the screen before the batch went.
            assertEquals(listOf(4L, 2L), translations.requests.map { it.second })
            assertEquals(shownIn("en", 4), viewModel.bubble(4).translation)
            assertNull(viewModel.bubble(3).translation)
            assertNull(viewModel.bubble(1).translation)

            visible(viewModel, 1, 2)
            assertEquals(listOf(4L, 2L, 1L), translations.requests.map { it.second })
        }

    @Test
    fun `a message that leaves the screen is no longer waited for, and asked for again when it is back`() =
        runViewModelTest {
            val answer = CompletableDeferred<Outcome<FormattedText>>()
            translations.onTranslate = { _, _, _ -> answer.await() }
            val viewModel = translating(true)
            send(viewModel, TranslationEvent.ToggleChat)
            visible(viewModel, 4)
            assertEquals(BubbleTranslation.Translating, viewModel.bubble(4).translation)

            visible(viewModel, 1)
            assertNull(viewModel.bubble(4).translation)
            answer.complete(Outcome.Success(text("Tomorrow?")))
            runCurrent()
            assertNull(viewModel.bubble(4).translation)

            translations.onTranslate = { _, messageId, code -> Outcome.Success(translationOf(messageId, code)) }
            visible(viewModel, 4)
            assertEquals(shownIn("en", 4), viewModel.bubble(4).translation)
            assertEquals(listOf(4L, 1L, 4L), translations.requests.map { it.second })
        }

    @Test
    fun `while the chat is translated, Show original keeps a message's text, and the bar turns it all off`() =
        runViewModelTest {
            val viewModel = translating(true)
            send(viewModel, TranslationEvent.ToggleChat)
            visible(viewModel, 1, 2)

            send(viewModel, TranslationEvent.ShowOriginal(2))
            assertNull(viewModel.bubble(2).translation)
            assertEquals(MessageAction.Translate, actionsOf(viewModel, 2).single { it.isTranslation })
            send(viewModel, ChatEvent.DismissMessageActions)
            assertEquals(shownIn("en", 1), viewModel.bubble(1).translation)

            send(viewModel, TranslationEvent.ToggleChat)
            assertEquals(false, viewModel.bar?.isOn)
            assertNull(viewModel.bubble(1).translation)
        }

    @Test
    fun `the chat's failures are told once, until the bar is turned on again`() =
        runViewModelTest {
            translations.onTranslate = { _, _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = translating(true)
            viewModel.effects.test {
                send(viewModel, TranslationEvent.ToggleChat)
                visible(viewModel, 1, 2, 4)
                assertEquals(TranslationEffect.Failed(DomainError.Network), awaitItem())
                visible(viewModel, 1, 2)
                expectNoEvents()
                // A failed message is not asked for again by itself.
                assertEquals(3, translations.requests.size)

                send(viewModel, TranslationEvent.ToggleChat, TranslationEvent.ToggleChat)
                advanceTimeBy(ChatTranslation.BATCH_MILLIS + 1)
                runCurrent()
                assertEquals(TranslationEffect.Failed(DomainError.Network), awaitItem())
            }
        }

    @Test
    fun `closing the bar hides it for the chat and ends its translation`() =
        runViewModelTest {
            val viewModel = translating(true)
            send(viewModel, TranslationEvent.ToggleChat)
            visible(viewModel, 1)

            viewModel.effects.test {
                send(viewModel, TranslationEvent.CloseBar)
                assertEquals(TranslationEffect.BarHidden, awaitItem())
            }
            assertNull(viewModel.bar)
            assertEquals(setOf(PRIVATE_CHAT), hiddenTranslateBars.hidden.value)
            assertNull(viewModel.bubble(1).translation)
        }

    @Test
    fun `a bar that cannot be closed stays, with the error`() =
        runViewModelTest {
            hiddenTranslateBars.onHide = { Outcome.Failure(DomainError.Unknown(0, "Disk full")) }
            val viewModel = translating(true)

            viewModel.effects.test {
                send(viewModel, TranslationEvent.CloseBar)
                assertEquals(ChatEffect.ShowError(DomainError.Unknown(0, "Disk full")), awaitItem())
            }
            assertEquals(false, viewModel.bar?.isOn)
        }

    @Test
    fun `after process death the choices stay and their translations are asked for again as they show`() =
        runViewModelTest {
            val first = translating(true)
            send(first, TranslationEvent.ToggleChat, TranslationEvent.Translate(3))
            visible(first, 1, 2, 3)
            send(first, TranslationEvent.ShowOriginal(1))
            clear(first)
            translations.requests.clear()

            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
            val viewModel = translating(true, restored)
            visible(viewModel, 1, 2, 3)

            assertEquals(true, viewModel.bar?.isOn)
            assertEquals(listOf(3L, 2L), translations.requests.map { it.second })
            assertNull(viewModel.bubble(1).translation)
            assertEquals(BubbleTranslation.Shown(translationOf(3, "en"), "en"), viewModel.bubble(3).translation)
        }

    private val ChatViewModel.bar get() = state.translateBar

    private val MessageAction.isTranslation get() =
        this == MessageAction.Translate ||
            this == MessageAction.ShowOriginal
}
