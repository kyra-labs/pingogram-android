package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.SavedTopicKind
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * A tab of Saved Messages in [ChatViewModel]: the topic's history, the top bar staying Saved Messages', and a composer
 * only in the user's notes, where what is sent goes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSavedTopicTest : ChatViewModelHarness() {
    private fun TestScope.inTab(kind: SavedTopicKind): ChatViewModel {
        users.myUserId.value = ME
        chats.setChats(chat(id = ME))
        savedTopics.set(SavedMessagesTopic(TOPIC, kind, isPinned = false, order = 1, lastMessage = null))
        messages.setHistory(ME, history(*(1L..3L).map { message(it, chatId = ME) }.toTypedArray()))
        return viewModel(ME, topic = MessageTopic.SavedMessages(TOPIC))
    }

    @Test
    fun `shows the topic's history under Saved Messages' own top bar`() =
        runViewModelTest {
            val viewModel = inTab(SavedTopicKind.SavedFrom(chatId = 7))

            assertEquals(listOf(3L, 2L, 1L), viewModel.bubbles.map { it.id })
            assertTrue(messages.topics.isNotEmpty() && messages.topics.all { it == MessageTopic.SavedMessages(TOPIC) })
            assertEquals(UiText.resource(UiR.string.chat_saved_messages), viewModel.state.header?.title)
        }

    @Test
    fun `only the user's notes take new messages`() =
        runViewModelTest {
            assertEquals(
                ComposerUiState.Unavailable(UiText.resource(ChatR.string.composer_saved_topic)),
                inTab(SavedTopicKind.SavedFrom(chatId = 7)).state.composer,
            )
            assertInstanceOf(ComposerUiState.Available::class.java, inTab(SavedTopicKind.MyNotes).state.composer)
        }

    private companion object {
        const val TOPIC = 55L
    }
}
