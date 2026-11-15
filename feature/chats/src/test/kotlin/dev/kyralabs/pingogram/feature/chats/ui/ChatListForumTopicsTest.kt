package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.supergroup
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A forum's row counting its topics with unread messages rather than its messages ([ForumUnreadTopics], owner decision
 * 2026-11-15), and a community's entry counting its chats with unread messages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListForumTopicsTest : ChatListViewModelHarness() {
    private fun forum(
        id: Long,
        unreadCount: Int = 0,
        lists: List<ChatListType> = listOf(ChatListType.Main),
    ) = chat(id = id, type = ChatType.Supergroup(id * 10, isChannel = false), unreadCount = unreadCount, lists = lists)

    private fun setForums(vararg ids: Long) {
        groups.supergroupMap.value =
            persistentMapOf(*ids.map { it * 10 to supergroup(it * 10, isForum = true) }.toTypedArray())
    }

    @Test
    fun `a forum the list shows counts its topics with unread messages`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 999))
            forumTopics.unreadTopics.value = mapOf(FORUM to 3)
            val viewModel = viewModel()
            assertNull(viewModel.item(FORUM).unreadTopicCount)

            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM)))

            assertEquals(3, viewModel.item(FORUM).unreadTopicCount)
            assertEquals(999, viewModel.item(FORUM).unreadCount)
        }

    @Test
    fun `a forum's count follows its unread messages, which say how many topics to load`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 7))
            forumTopics.unreadTopics.value = mapOf(FORUM to 2)
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM)))
            assertEquals(7, forumTopics.unreadMessages[FORUM])

            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 9))
            runCurrent()

            assertEquals(9, forumTopics.unreadMessages[FORUM])
            // The same forum keeps its one observation as its count changes.
            assertEquals(listOf(FORUM), forumTopics.requested)
        }

    @Test
    fun `a forum the list has not shown asks for nothing, a read one for its topics`() =
        runTest {
            setForums(FORUM, OTHER_FORUM, READ_FORUM)
            chats.setChats(
                ChatListType.Main,
                forum(FORUM, unreadCount = 5),
                forum(OTHER_FORUM, unreadCount = 5),
                forum(READ_FORUM),
                chat(id = GROUP, type = ChatType.Supergroup(GROUP * 10, isChannel = false), unreadCount = 5),
            )
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM, READ_FORUM, GROUP)))

            assertEquals(setOf(FORUM, READ_FORUM), forumTopics.requested.toSet())
        }

    @Test
    fun `a forum keeps its count once shown, and shows none once read`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 5))
            forumTopics.unreadTopics.value = mapOf(FORUM to 2)
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM)))
            send(viewModel, ChatListEvent.RowsShown(emptySet()))
            assertEquals(2, viewModel.item(FORUM).unreadTopicCount)

            chats.setChats(ChatListType.Main, forum(FORUM))
            runCurrent()

            assertNull(viewModel.item(FORUM).unreadTopicCount)
            // Its row still names its topics.
            assertEquals(setOf(FORUM), forumTopics.observed)
        }

    @Test
    fun `a forum's row names its five newest topics, those with unread messages marked`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 4))
            forumTopics.topicLists.value =
                mapOf(
                    FORUM to
                        listOf(
                            forumTopics.forumTopic(FORUM, 7, unreadCount = 0, color = TopicColor.Red),
                            forumTopics.forumTopic(FORUM, 1, unreadCount = 3),
                            forumTopics.forumTopic(FORUM, 5, unreadCount = 0),
                            forumTopics.forumTopic(FORUM, 4, unreadCount = 1),
                            forumTopics.forumTopic(FORUM, 3, unreadCount = 0),
                            forumTopics.forumTopic(FORUM, 2, unreadCount = 0),
                        ),
                )
            val viewModel = viewModel()
            assertNull(viewModel.item(FORUM).topics)

            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM)))

            val topics = viewModel.item(FORUM).topics
            assertEquals(listOf(7, 1, 5, 4, 3), topics?.map { it.id })
            assertEquals(listOf(false, true, false, true, false), topics?.map { it.isUnread })
            // General has a symbol of its own, the others the color of their icon.
            assertEquals(listOf(TopicColor.Red, null), topics?.take(2)?.map { it.color })
            assertEquals(2, viewModel.item(FORUM).unreadTopicCount)
        }

    @Test
    fun `a forum whose topics are all read shows no topic count`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Main, forum(FORUM, unreadCount = 5))
            forumTopics.unreadTopics.value = mapOf(FORUM to 0)
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowsShown(setOf(FORUM)))

            assertNull(viewModel.item(FORUM).unreadTopicCount)
        }

    @Test
    fun `the archive counts no topics`() =
        runTest {
            setForums(FORUM)
            chats.setChats(ChatListType.Archive, forum(FORUM, unreadCount = 5, lists = listOf(ChatListType.Archive)))
            forumTopics.unreadTopics.value = mapOf(FORUM to 2)
            val archive = viewModel(kind = ChatListKind.Archive)

            send(archive, ChatListEvent.RowsShown(setOf(FORUM)))

            assertTrue(forumTopics.requested.isEmpty())
        }

    private companion object {
        const val FORUM = 7L
        const val OTHER_FORUM = 8L
        const val READ_FORUM = 9L
        const val GROUP = 11L
    }
}
