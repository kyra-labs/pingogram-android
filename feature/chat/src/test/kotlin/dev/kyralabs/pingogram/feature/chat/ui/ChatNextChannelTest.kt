package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The next unread channel in [ChatViewModel] (`docs/DESIGN_SYSTEM.md` §6 "Next unread channel"): which channel a read
 * channel offers, when it offers none, and opening it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatNextChannelTest : ChatViewModelHarness() {
    private fun channel(
        id: Long,
        unread: Int,
    ) = chat(id = id, type = ChatType.Supergroup(id, isChannel = true), title = "Channel $id", unreadCount = unread)

    /** The conversation of [current], the main list holding [list], its next channel collected. */
    private fun TestScope.showing(
        current: Chat,
        vararg list: Chat,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(9))
        chats.setChats(current, *list)
        chats.mainList.value = listOf(current, *list)
        messages.setHistory(current.id, history(message(1, current.id)))
        val viewModel = viewModel(current.id, topic = topic)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.nextChannel.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a read channel offers the first other channel of the main list with unread posts`() =
        runViewModelTest {
            val viewModel = showing(channelChat, groupChat.copy(unreadCount = 3), channel(OTHER, 0), channel(NEXT, 4))

            assertEquals(NextChannel(NEXT, "Channel $NEXT", photo = null, unreadCount = 4), viewModel.nextChannel.value)
        }

    @Test
    fun `in a folder the Chats tab shows, the next channel is that folder's`() =
        runViewModelTest {
            chats.folderLists.value = mapOf(FOLDER to listOf(channel(OTHER, 2)))
            shownLists.shown.value = ChatListType.Folder(FOLDER)
            val viewModel = showing(channelChat, channel(NEXT, 4))

            assertEquals(OTHER, viewModel.nextChannel.value?.chatId)

            shownLists.shown.value = ChatListType.Main
            runCurrent()
            assertEquals(NEXT, viewModel.nextChannel.value?.chatId)
        }

    @Test
    fun `the offer follows the list as channels are read`() =
        runViewModelTest {
            val viewModel = showing(channelChat, channel(NEXT, 4), channel(OTHER, 2))

            chats.mainList.value = listOf(channelChat, channel(NEXT, 0), channel(OTHER, 2))
            runCurrent()

            assertEquals(OTHER, viewModel.nextChannel.value?.chatId)
        }

    @Test
    fun `a channel with unread posts, a group and a thread offer none`() =
        runViewModelTest {
            assertNull(showing(channelChat.copy(unreadCount = 1), channel(NEXT, 4)).nextChannel.value)
            assertNull(showing(groupChat, channel(NEXT, 4)).nextChannel.value)
            assertNull(showing(channelChat, channel(NEXT, 4), topic = MessageTopic.Thread(1)).nextChannel.value)
        }

    @Test
    fun `a read channel with no unread one after it offers none`() =
        runViewModelTest {
            assertNull(showing(channelChat, channel(OTHER, 0)).nextChannel.value)
        }

    @Test
    fun `opening the offered channel opens it in this one's place, a stale one not`() =
        runViewModelTest {
            val viewModel = showing(channelChat, channel(NEXT, 4))

            viewModel.effects.test {
                val stale = NextChannel(OTHER, "Channel $OTHER", photo = null, unreadCount = 1)
                send(
                    viewModel,
                    ChatEvent.OpenNextChannel(stale),
                    ChatEvent.OpenNextChannel(checkNotNull(viewModel.nextChannel.value)),
                )
                assertEquals(ChatEffect.OpenNextChannel(NEXT), awaitItem())
                expectNoEvents()
            }
        }

    /** The conversation of [forumChat]'s topic [topicId], the forum holding [topics], its next topic collected. */
    private fun TestScope.showingTopic(
        topicId: Int,
        vararg topics: ForumTopic,
    ): ChatViewModel {
        chats.setChats(forumChat)
        forumTopics.forums.value =
            mapOf(FORUM_CHAT to ForumTopicList(topics.toList().toPersistentList(), reachedEnd = true))
        messages.setHistory(FORUM_CHAT, history(message(1, FORUM_CHAT)))
        val viewModel = viewModel(FORUM_CHAT, topic = MessageTopic.Forum(topicId))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.nextChannel.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a read forum topic offers the forum's next topic with unread messages, which opens in its place`() =
        runViewModelTest {
            val viewModel =
                showingTopic(1, forumTopic(1), forumTopic(2), forumTopic(3, name = "Releases", unreadCount = 5))

            val next = NextChannel(FORUM_CHAT, "Releases", photo = null, unreadCount = 5, topicId = 3)
            assertEquals(next, viewModel.nextChannel.value)
            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenNextChannel(next))
                assertEquals(ChatEffect.OpenNextChannel(FORUM_CHAT, topicId = 3), awaitItem())
            }
        }

    @Test
    fun `a topic with unread messages, or a forum without other unread topics, offers none`() =
        runViewModelTest {
            assertNull(
                showingTopic(1, forumTopic(1, unreadCount = 2), forumTopic(2, unreadCount = 5)).nextChannel.value,
            )
            assertNull(showingTopic(1, forumTopic(1), forumTopic(2)).nextChannel.value)
        }

    private companion object {
        const val NEXT = -300L
        const val FOLDER = 1
        const val OTHER = -400L
    }
}
