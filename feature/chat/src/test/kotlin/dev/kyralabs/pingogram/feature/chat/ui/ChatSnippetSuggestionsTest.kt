package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.BotCommand
import dev.kyralabs.pingogram.core.domain.model.BotInfo
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Snippets in the "/" suggestions of [ChatViewModel] (`REQUIREMENTS.md` FR-X2): a "/" word before the cursor suggests
 * the snippets its letters start, in any chat, after a bot's commands; choosing one puts its text in place of the
 * word and never sends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSnippetSuggestionsTest : ChatViewModelHarness() {
    private fun TestScope.opened(
        chatId: Long = PERSON,
        chatBots: ChatBots = ChatBots.None,
    ): ChatViewModel {
        snippets.saved.value = listOf(ADDRESS, STICKERS_NOTE, THANKS)
        users.setUsers(user(BOT, firstName = "Sticker Finder", type = UserType.Bot), user(PERSON), user(ME))
        chats.setChats(chat(id = chatId, title = "Ada"))
        messages.setHistory(chatId, history(message(1, chatId = chatId)))
        bots.setBots(chatId, chatBots)
        val viewModel = viewModel(chatId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.commandSuggestions.collect {} }
        runCurrent()
        return viewModel
    }

    /** Sets the field's text with the cursor at [cursor] (its end by default), as typing does. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
        cursor: Int = text.length,
    ) {
        viewModel.composerText.state.edit {
            replace(0, length, text)
            selection = TextRange(cursor)
        }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun `a slash word suggests the snippets its letters start, in their order`() =
        runViewModelTest {
            val viewModel = opened()

            type(viewModel, "/st")

            assertEquals(listOf(STICKERS_NOTE), viewModel.commandSuggestions.value?.snippets)
            assertEquals("st", viewModel.commandSuggestions.value?.query)
        }

    @Test
    fun `a slash word after other words suggests too, in any letter case`() =
        runViewModelTest {
            val viewModel = opened()

            type(viewModel, "Send it to /ADD")

            assertEquals(listOf(ADDRESS), viewModel.commandSuggestions.value?.snippets)
        }

    @Test
    fun `none once a space ends the word, inside a word, or where the cursor is elsewhere`() =
        runViewModelTest {
            val viewModel = opened()

            type(viewModel, "/address ")
            assertNull(viewModel.commandSuggestions.value)

            type(viewModel, "and/or")
            assertNull(viewModel.commandSuggestions.value)

            type(viewModel, "/add later", cursor = "/add later".length)
            assertNull(viewModel.commandSuggestions.value)
        }

    @Test
    fun `in a chat with a bot its commands come first, then the snippets`() =
        runViewModelTest {
            val commands = persistentListOf(BotCommand("start", "Start", BOT), BotCommand("stickers", "Find", BOT))
            val viewModel = opened(chatId = BOT, chatBots = ChatBots(commands, BotInfo(BOT, "", null, null, null)))

            type(viewModel, "/st")

            val suggestions = viewModel.commandSuggestions.value
            assertEquals(listOf("/start", "/stickers"), suggestions?.commands?.map { it.text })
            assertEquals(listOf(STICKERS_NOTE), suggestions?.snippets)
        }

    @Test
    fun `choosing a snippet puts its text in place of the word, focuses the field and sends nothing`() =
        runViewModelTest {
            val viewModel = opened()
            type(viewModel, "Send it to /addr")

            viewModel.effects.test {
                send(viewModel, BotEvent.InsertSnippet(ADDRESS.id))

                assertEquals(ChatEffect.FocusComposer, awaitItem())
            }
            assertEquals("Send it to 12 Crescent Road", viewModel.composerText.content.text)
            assertTrue(messages.sent.isEmpty())
        }

    @Test
    fun `none while editing`() =
        runViewModelTest {
            messages.setHistory(PERSON, history(message(1, chatId = PERSON, isOutgoing = true)))
            val viewModel = opened()
            messages.setHistory(PERSON, history(message(1, chatId = PERSON, isOutgoing = true)))
            runCurrent()

            send(viewModel, ChatEvent.Edit(1))
            type(viewModel, "/add")

            assertNull(viewModel.commandSuggestions.value)
        }

    private companion object {
        const val PERSON = 42L
        const val BOT = 500L
        val ADDRESS = Snippet(1, "address", "12 Crescent Road")
        val STICKERS_NOTE = Snippet(2, "stickerpacks", "My sticker packs")
        val THANKS = Snippet(3, "thanks", "Thank you!")
    }
}
