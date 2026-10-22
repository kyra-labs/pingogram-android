package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The conversation of a forum topic in [ChatViewModel] (`REQUIREMENTS.md` FR-C9): the topic's history, its name in the
 * header, its unread state, what is sent going into it, its pins, closed topics, and a topic that cannot be found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTopicTest : ChatViewModelHarness() {
    private val topic = MessageTopic.Forum(TOPIC)

    /** The conversation of topic [TOPIC] of the forum, its messages 1–3, the user [status] in the forum. */
    private fun TestScope.inTopic(
        unreadCount: Int = 0,
        isClosed: Boolean = false,
        status: ChatMemberStatus = ChatMemberStatus.Member,
        draft: ChatDraft? = null,
    ): ChatViewModel {
        chats.setChats(forumChat.copy(draft = draft, unreadCount = 40))
        groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(10, isChannel = false, status, isForum = true))
        forumTopics.setTopics(
            FORUM_CHAT,
            forumTopic(TOPIC, name = "Help", unreadCount = unreadCount, isClosed = isClosed),
        )
        messages.setHistory(FORUM_CHAT, history(*(1L..3L).map { message(it, chatId = FORUM_CHAT) }.toTypedArray()))
        return viewModel(FORUM_CHAT, topic = topic)
    }

    @Test
    fun `shows the topic's history, with its name over the forum's`() =
        runViewModelTest {
            val viewModel = inTopic()

            assertEquals(listOf(3L, 2L, 1L), viewModel.bubbles.map { it.id })
            assertTrue(messages.topics.isNotEmpty() && messages.topics.all { it == topic })
            assertEquals(UiText.Plain("Help"), viewModel.state.header?.title)
            assertEquals(ChatSubtitle.Status(UiText.Plain("Compose Developers")), viewModel.subtitle)
        }

    @Test
    fun `the jump button counts the topic's unread messages, not the forum's`() =
        runViewModelTest {
            assertEquals(4, inTopic(unreadCount = 4).state.unreadCount)
        }

    @Test
    fun `a text goes into the topic`() =
        runViewModelTest {
            val viewModel = inTopic()
            viewModel.composerText.state.edit { replace(0, length, "Thanks!") }
            Snapshot.sendApplyNotifications()
            runCurrent()

            send(viewModel, ChatEvent.Send(SendOptions()))

            assertEquals(SendOptions(topic = topic), messages.sent.single().third)
        }

    @Test
    fun `a sticker goes into the topic too`() =
        runViewModelTest {
            val viewModel = inTopic()
            val sticker =
                Sticker(
                    setId = 7,
                    width = 512,
                    height = 512,
                    emoji = "🦆",
                    format = StickerFormat.Tgs,
                    thumbnail = null,
                    file = FileRef(70, 30_000),
                )

            send(viewModel, ChatEvent.SendSticker(sticker))

            assertEquals(SendOptions(topic = topic), attachments.sentStickers.single().second)
        }

    @Test
    fun `messages on screen are viewed in the topic`() =
        runViewModelTest {
            val viewModel = inTopic()

            send(viewModel, ChatEvent.Started, ChatEvent.MessagesVisible(setOf(1, 2)))
            testScheduler.advanceTimeBy(VIEW_BATCH_MILLIS)
            runCurrent()

            assertTrue(chats.viewedIn.isNotEmpty() && chats.viewedIn.all { it == topic })
        }

    @Test
    fun `the forum's own draft stays out of the topic's composer`() =
        runViewModelTest {
            val viewModel = inTopic(draft = ChatDraft(DraftContent.Text(text("For the whole forum")), date = 0))

            assertEquals("", viewModel.composerText.content.text)
        }

    @Test
    fun `the topic's pins are its own`() =
        runViewModelTest {
            inTopic()

            assertTrue(pinnedMessages.topics.isNotEmpty() && pinnedMessages.topics.all { it == topic })
        }

    @Test
    fun `the topic's pinned bar follows the topic's messages on screen and cycles through its pins`() =
        runViewModelTest {
            pinnedMessages.setPinned(FORUM_CHAT, message(1, chatId = FORUM_CHAT), message(3, chatId = FORUM_CHAT))
            val viewModel = inTopic()

            send(viewModel, ChatEvent.MessagesVisible(setOf(2L)))
            val bar = viewModel.state.pinnedBar
            assertEquals(listOf<Any>(1L, 0, 2), bar?.let { listOf(it.messageId, it.position, it.count) })

            send(viewModel, ChatEvent.MessagesVisible(setOf(3L)))
            send(viewModel, PinnedEvent.ShowCurrent)
            assertEquals(AnchorTarget.Message(3), viewModel.anchor.target)
            assertEquals(1L, viewModel.state.pinnedBar?.messageId)
        }

    @Test
    fun `a closed topic takes messages from administrators only`() =
        runViewModelTest {
            assertEquals(
                ComposerUiState.Unavailable(UiText.resource(ChatR.string.composer_topic_closed)),
                inTopic(isClosed = true).state.composer,
            )
            assertInstanceOf(
                ComposerUiState.Available::class.java,
                inTopic(
                    isClosed = true,
                    status = ChatMemberStatus.Administrator(canPostMessages = false),
                ).state.composer,
            )
        }

    @Test
    fun `a topic that cannot be found is not found`() =
        runViewModelTest {
            chats.setChats(forumChat)
            val viewModel = viewModel(FORUM_CHAT, topic = MessageTopic.Forum(99))

            assertEquals(ChatContent.NotFound, viewModel.state.content)
        }

    private companion object {
        const val TOPIC = 7

        /** Longer than the batch of viewed messages waits (`MessageViews`). */
        const val VIEW_BATCH_MILLIS = 1_000L
    }
}
