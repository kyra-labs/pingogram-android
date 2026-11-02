package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTopicsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    @Test
    fun `the forum lists its topics, each with its own messages`() =
        runTest {
            val (client, _) = client()

            val topics = client.ask(TdApi.GetForumTopics(DemoData.FORUM_CHAT, "", 0, 0, 0, 20)).topics
            val reading =
                client.ask(TdApi.GetForumTopicHistory(DemoData.FORUM_CHAT, DemoData.READING_TOPIC, 0, 0, 20)).messages

            assertEquals(
                setOf(DemoData.GENERAL_TOPIC, DemoData.READING_TOPIC, DemoData.MEETUPS_TOPIC),
                topics.map { it.info.forumTopicId }.toSet(),
            )
            assertEquals(2, reading.size)
            assertTrue(reading.all { (it.topicId as TdApi.MessageTopicForum).forumTopicId == DemoData.READING_TOPIC })
        }

    @Test
    fun `a new topic is kept with its first message, closes, and takes its messages when deleted`() =
        runTest {
            val (client, _) = client()

            val created =
                client.ask(TdApi.CreateForumTopic(DemoData.FORUM_CHAT, "Swaps", false, TdApi.ForumTopicIcon(0, 0)))
            client.ask(TdApi.ToggleForumTopicIsClosed(DemoData.FORUM_CHAT, created.forumTopicId, true))
            client.send(
                TdApi.SendMessage(
                    DemoData.FORUM_CHAT,
                    TdApi.MessageTopicForum(created.forumTopicId),
                    null,
                    null,
                    null,
                    plainText("Hi"),
                ),
            )
            val (restarted, _) = client()

            val topic = restarted.ask(TdApi.GetForumTopic(DemoData.FORUM_CHAT, created.forumTopicId))
            assertTrue(topic.info.isClosed)
            val history = restarted.ask(TdApi.GetForumTopicHistory(DemoData.FORUM_CHAT, created.forumTopicId, 0, 0, 20))
            assertInstanceOf(TdApi.MessageForumTopicCreated::class.java, history.messages.last().content)
            assertEquals(2, history.totalCount)

            restarted.ask(TdApi.DeleteForumTopic(DemoData.FORUM_CHAT, created.forumTopicId))
            assertInstanceOf(
                TdResult.Failure::class.java,
                restarted.send(TdApi.GetForumTopic(DemoData.FORUM_CHAT, created.forumTopicId)),
            )
            val left = restarted.ask(TdApi.GetForumTopicHistory(DemoData.FORUM_CHAT, created.forumTopicId, 0, 0, 20))
            assertEquals(0, left.totalCount)
        }

    @Test
    fun `a post's comments are in the discussion group, and a new one is counted on the post`() =
        runTest {
            val (client, updates) = client()
            val firstPost = client.ask(TdApi.GetChatHistory(DemoData.CHANNEL_CHAT, 0, 0, 10, false)).messages.last()

            val thread = client.ask(TdApi.GetMessageThread(DemoData.CHANNEL_CHAT, firstPost.id))
            val comments =
                client.ask(TdApi.GetMessageThreadHistory(thread.chatId, thread.messageThreadId, 0, 0, 20)).messages
            client.ask(
                TdApi.SendMessage(
                    thread.chatId,
                    TdApi.MessageTopicThread(thread.messageThreadId),
                    null,
                    null,
                    null,
                    plainText("Nice"),
                ),
            )
            runCurrent()

            assertEquals(DemoData.DISCUSSION_CHAT, thread.chatId)
            assertEquals(2 to 2, thread.replyInfo?.replyCount to comments.size)
            val counted =
                updates.filterIsInstance<TdApi.UpdateMessageInteractionInfo>().last {
                    it.chatId == DemoData.CHANNEL_CHAT
                }
            assertEquals(firstPost.id to 3, counted.messageId to counted.interactionInfo?.replyInfo?.replyCount)
        }

    @Test
    fun `a new channel post can take comments at once`() =
        runTest {
            val (client, _) = client()

            val post = client.ask(TdApi.SendMessage(DemoData.CHANNEL_CHAT, null, null, null, null, plainText("News")))
            val thread = client.ask(TdApi.GetMessageThread(DemoData.CHANNEL_CHAT, post.id))

            assertEquals(
                0,
                client
                    .ask(TdApi.GetMessage(DemoData.CHANNEL_CHAT, post.id))
                    .interactionInfo
                    ?.replyInfo
                    ?.replyCount,
            )
            assertEquals(DemoData.DISCUSSION_CHAT, thread.chatId)
        }

    @Test
    fun `a secret chat is ready at once, takes messages, ends, and is announced before its chat`() =
        runTest {
            val (client, _) = client()

            val chat = client.ask(TdApi.CreateNewSecretChat(DemoData.BEN))
            val secretChatId = (chat.type as TdApi.ChatTypeSecret).secretChatId
            assertInstanceOf(
                TdApi.SecretChatStateReady::class.java,
                client.ask(TdApi.GetSecretChat(secretChatId)).state,
            )
            client.ask(TdApi.SendMessage(chat.id, null, null, null, null, plainText("Hello")))
            client.ask(TdApi.CloseSecretChat(secretChatId))
            val (_, updates) = client()

            val secret = updates.indexOfFirst { it is TdApi.UpdateSecretChat }
            val newChat = updates.indexOfFirst { it is TdApi.UpdateNewChat && it.chat.id == chat.id }
            assertTrue(secret in 0 until newChat)
            val state = (updates[secret] as TdApi.UpdateSecretChat).secretChat.state
            assertInstanceOf(TdApi.SecretChatStateClosed::class.java, state)
            assertEquals(36, (updates[secret] as TdApi.UpdateSecretChat).secretChat.keyHash.size)
        }

    @Test
    fun `Saved Messages' topics load once, the notes and a saved post`() =
        runTest {
            val (client, updates) = client()

            client.ask(TdApi.LoadSavedMessagesTopics(100))
            runCurrent()
            val again = client.send(TdApi.LoadSavedMessagesTopics(100)) as TdResult.Failure
            val notes = client.ask(TdApi.GetSavedMessagesTopicHistory(DemoData.ME, 0, 0, 20))

            val types = updates.filterIsInstance<TdApi.UpdateSavedMessagesTopic>().map { it.topic.type }
            assertEquals(2, types.size)
            assertTrue(types.any { it is TdApi.SavedMessagesTopicTypeMyNotes })
            assertEquals(404, again.error.code)
            assertEquals(2, notes.totalCount)
        }

    @Test
    fun `the user turns their discussion group into a forum, which starts with General`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.ToggleSupergroupIsForum(DemoData.DISCUSSION, true, false))
            val topics = client.ask(TdApi.GetForumTopics(DemoData.DISCUSSION_CHAT, "", 0, 0, 0, 20)).topics

            assertTrue(client.ask(TdApi.GetSupergroup(DemoData.DISCUSSION)).isForum)
            assertEquals(listOf(true), topics.map { it.info.isGeneral })
        }
}
