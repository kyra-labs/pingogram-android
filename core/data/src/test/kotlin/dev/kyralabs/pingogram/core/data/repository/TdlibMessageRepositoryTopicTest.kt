package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import dev.kyralabs.pingogram.core.testing.inTopic
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The histories of forum topics and message threads in [TdlibMessageRepository] (`REQUIREMENTS.md` FR-C9): each a
 * window of its own, loaded with TDLib's topic and thread functions, that new messages join only when they are in it.
 */
class TdlibMessageRepositoryTopicTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibMessageRepository(
            client,
            backgroundScope,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
        )

    private fun inTopic(
        id: Long,
        topicId: Int = TOPIC,
    ) = message(id = id, chatId = FORUM).inTopic(TdApi.MessageTopicForum(topicId))

    private fun reply(id: Long) = message(id = id, chatId = GROUP).inTopic(TdApi.MessageTopicThread(THREAD))

    @Test
    fun `a topic's history loads with getForumTopicHistory, apart from the chat's`() =
        runTest {
            client.on(TdApi.GetForumTopicHistory::class, TdResult.Success(messages(inTopic(9), inTopic(4))))
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.loadOlder(FORUM, FORUM_TOPIC))

            assertEquals(
                listOf(9L, 4L),
                repository
                    .history(FORUM, FORUM_TOPIC)
                    .first()
                    .messages
                    .map { it.id },
            )
            val request = client.sentOf<TdApi.GetForumTopicHistory>().single()
            assertEquals(listOf(FORUM, TOPIC.toLong()), listOf(request.chatId, request.forumTopicId.toLong()))
            assertTrue(client.sentOf<TdApi.GetChatHistory>().isEmpty())
            assertTrue(
                repository
                    .history(FORUM)
                    .first()
                    .messages
                    .isEmpty(),
            )
        }

    @Test
    fun `a topic with no messages reaches its start at once`() =
        runTest {
            client.on(TdApi.GetForumTopicHistory::class, TdResult.Success(messages()))
            val repository = repository()

            repository.loadOlder(FORUM, FORUM_TOPIC)

            assertTrue(repository.history(FORUM, FORUM_TOPIC).first().reachedStart)
        }

    @Test
    fun `a new message joins the history of its topic only`() =
        runTest {
            client.on(TdApi.GetForumTopicHistory::class) { request ->
                TdResult.Success(messages(inTopic(4, request.forumTopicId)))
            }
            val repository = repository()
            repository.loadOlder(FORUM, FORUM_TOPIC)
            repository.loadOlder(FORUM, MessageTopic.Forum(OTHER_TOPIC))

            client.emit(updateNewMessage(inTopic(10)))

            assertEquals(
                listOf(10L, 4L),
                repository
                    .history(FORUM, FORUM_TOPIC)
                    .first()
                    .messages
                    .map { it.id },
            )
            val other = repository.history(FORUM, MessageTopic.Forum(OTHER_TOPIC)).first()
            assertEquals(listOf(4L), other.messages.map { it.id })
        }

    @Test
    fun `the General topic holds messages without a topic too`() =
        runTest {
            client.on(TdApi.GetForumTopicHistory::class, TdResult.Success(messages()))
            val repository = repository()
            val general = MessageTopic.Forum(MessageTopic.GENERAL_TOPIC_ID)
            repository.loadOlder(FORUM, general)

            client.emit(updateNewMessage(message(id = 3, chatId = FORUM)))

            assertEquals(
                listOf(3L),
                repository
                    .history(FORUM, general)
                    .first()
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun `a thread's history holds its replies, without the message that starts it`() =
        runTest {
            val root = message(id = THREAD, chatId = GROUP).inTopic(TdApi.MessageTopicThread(THREAD))
            client.on(TdApi.GetMessageThreadHistory::class, TdResult.Success(messages(reply(31), reply(30), root)))
            val repository = repository()

            repository.loadOlder(GROUP, GROUP_THREAD)
            client.emit(updateNewMessage(reply(35)))
            client.emit(updateNewMessage(message(id = 36, chatId = GROUP)))

            assertEquals(
                listOf(35L, 31L, 30L),
                repository
                    .history(GROUP, GROUP_THREAD)
                    .first()
                    .messages
                    .map { it.id },
            )
            val request = client.sentOf<TdApi.GetMessageThreadHistory>().single()
            assertEquals(listOf(GROUP, THREAD), listOf(request.chatId, request.messageId))
        }

    @Test
    fun `the newest page of a topic reaches its end from the topic's last message`() =
        runTest {
            client.on(TdApi.GetForumTopicHistory::class, TdResult.Success(messages(inTopic(9))))
            client.on(TdApi.GetForumTopic::class) {
                TdResult.Success(TdApi.ForumTopic().also { topic -> topic.lastMessage = inTopic(9) })
            }
            val repository = repository()

            repository.loadAround(FORUM, 9, FORUM_TOPIC)

            assertTrue(repository.history(FORUM, FORUM_TOPIC).first().reachedEnd)
        }

    @Test
    fun `a message sent into a topic or thread names it`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Success(inTopic(99)))
            val repository = repository()
            val text = FormattedText("Hi", persistentListOf())

            repository.sendText(FORUM, text, SendOptions(topic = FORUM_TOPIC))
            repository.sendText(GROUP, text, SendOptions(topic = GROUP_THREAD))

            val (forum, thread) = client.sentOf<TdApi.SendMessage>()
            assertEquals(TOPIC, (forum.topicId as TdApi.MessageTopicForum).forumTopicId)
            assertEquals(THREAD, (thread.topicId as TdApi.MessageTopicThread).messageThreadId)
        }

    private companion object {
        const val FORUM = -100L
        const val GROUP = -200L
        const val TOPIC = 7
        const val OTHER_TOPIC = 8
        const val THREAD = 20L
        val FORUM_TOPIC = MessageTopic.Forum(TOPIC)
        val GROUP_THREAD = MessageTopic.Thread(THREAD)
    }
}
