package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateChatAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibChatActionRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibChatActionRepository(client, backgroundScope)

    private val ada = TdApi.MessageSenderUser(ADA)
    private val grace = TdApi.MessageSenderUser(GRACE)
    private val lin = TdApi.MessageSenderUser(LIN)
    private val cancel = TdApi.ChatActionCancel()

    private fun typing(userId: Long) = SenderAction(MessageSender.User(userId), ChatAction.Typing)

    @Test
    fun `sends what the user does in a chat's topic, and its end`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SendChatAction::class, TdResult.Success(TdApi.Ok()))

            repository.send(CHAT_ID, MessageTopic.Forum(7), ChatAction.Typing)
            repository.send(CHAT_ID, null, ChatAction.UploadingPhoto(40))
            repository.send(CHAT_ID, null, null)

            val sent = client.sentOf<TdApi.SendChatAction>()
            assertEquals(listOf(CHAT_ID, CHAT_ID, CHAT_ID), sent.map { it.chatId })
            assertEquals(7, (sent[0].topicId as TdApi.MessageTopicForum).forumTopicId)
            assertTrue(sent[0].action is TdApi.ChatActionTyping)
            assertNull(sent[1].topicId)
            assertEquals(40, (sent[1].action as TdApi.ChatActionUploadingPhoto).progress)
            assertTrue(sent[2].action is TdApi.ChatActionCancel)
        }

    @Test
    fun `there are no actions before TDLib reports one`() =
        runTest {
            val repository = repository()

            assertEquals(emptyMap<Long, Any>(), repository.actionsByChat.first())
            assertEquals(emptyList<SenderAction>(), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `an action reported right after the repository was created is kept`() =
        runTest {
            val repository = repository()

            // No suspension in between: an update without a subscriber is lost, as it would be from TDLib.
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `keeps the action of a group's anonymous admins, who act as the chat`() =
        runTest {
            val repository = repository()

            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, TdApi.MessageSenderChat(CHAT_ID)))

            assertEquals(
                listOf(SenderAction(MessageSender.Chat(CHAT_ID), ChatAction.Typing)),
                repository.actions(CHAT_ID).first(),
            )
        }

    @Test
    fun `keeps one action per sender, in the order they started`() =
        runTest {
            val repository = repository()

            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, grace))
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            assertEquals(listOf(typing(GRACE), typing(ADA)), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `a sender's new action replaces the old one in its place`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, grace))
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            client.emit(updateChatAction(TdApi.ChatActionUploadingPhoto(40), CHAT_ID, grace))

            assertEquals(
                listOf(SenderAction(MessageSender.User(GRACE), ChatAction.UploadingPhoto(40)), typing(ADA)),
                repository.actions(CHAT_ID).first(),
            )
        }

    @Test
    fun `a cancellation removes only that sender's action`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, grace))
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            client.emit(updateChatAction(cancel, CHAT_ID, grace))

            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `a chat whose last action was cancelled leaves the map`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            client.emit(updateChatAction(cancel, CHAT_ID, ada))

            assertEquals(emptyMap<Long, Any>(), repository.actionsByChat.first())
        }

    @Test
    fun `actionsByChat holds every chat's actions by chat id`() =
        runTest {
            val repository = repository()

            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))
            client.emit(updateChatAction(TdApi.ChatActionTyping(), OTHER_CHAT_ID, grace))

            assertEquals(
                mapOf(CHAT_ID to listOf(typing(ADA)), OTHER_CHAT_ID to listOf(typing(GRACE))),
                repository.actionsByChat.first(),
            )
        }

    @Test
    fun `an update of a topic alone leaves the chats' map as it is`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            repository.actionsByChat.test {
                awaitItem()

                // The cancellation TDLib sends for the topic a sender moved away from, who still acts in the chat.
                client.emit(updateChatAction(cancel, CHAT_ID, ada, TdApi.MessageTopicThread(7)))
                client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, grace, TdApi.MessageTopicForum(3)))

                expectNoEvents()
            }
        }

    @Test
    fun `a topic has only its own actions, the chat every topic's`() =
        runTest {
            val repository = repository()

            typeInTopic(grace, TdApi.MessageTopicForum(3))
            typeInTopic(ada, TdApi.MessageTopicForum(5))

            assertEquals(listOf(typing(GRACE)), repository.actions(CHAT_ID, MessageTopic.Forum(3)).first())
            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID, MessageTopic.Forum(5)).first())
            assertEquals(emptyList<SenderAction>(), repository.actions(CHAT_ID, MessageTopic.Forum(9)).first())
            assertEquals(listOf(typing(GRACE), typing(ADA)), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `a topic's actions are not another chat's`() =
        runTest {
            val repository = repository()

            typeInTopic(grace, TdApi.MessageTopicForum(3))

            assertEquals(emptyList<SenderAction>(), repository.actions(OTHER_CHAT_ID, MessageTopic.Forum(3)).first())
            assertEquals(mapOf(CHAT_ID to listOf(typing(GRACE))), repository.actionsByChat.first())
        }

    @Test
    fun `a thread has only its own actions`() =
        runTest {
            val repository = repository()

            typeInTopic(grace, TdApi.MessageTopicThread(7))
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            assertEquals(listOf(typing(GRACE)), repository.actions(CHAT_ID, MessageTopic.Thread(7)).first())
        }

    @Test
    fun `General has its own actions and those in no topic, never another topic's`() =
        runTest {
            val repository = repository()

            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))
            typeInTopic(grace, TdApi.MessageTopicForum(3))
            typeInTopic(lin, TdApi.MessageTopicForum(MessageTopic.GENERAL_TOPIC_ID))

            assertEquals(
                listOf(typing(ADA), typing(LIN)),
                repository.actions(CHAT_ID, MessageTopic.Forum(MessageTopic.GENERAL_TOPIC_ID)).first(),
            )
        }

    @Test
    fun `a sender who moves to another topic acts only there`() =
        runTest {
            val repository = repository()
            typeInTopic(ada, TdApi.MessageTopicForum(3))

            // What TDLib sends: the old topic's cancellation alone, then the new topic's action for it and the chat.
            client.emit(updateChatAction(cancel, CHAT_ID, ada, TdApi.MessageTopicForum(3)))
            typeInTopic(ada, TdApi.MessageTopicForum(5))

            assertEquals(emptyList<SenderAction>(), repository.actions(CHAT_ID, MessageTopic.Forum(3)).first())
            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID, MessageTopic.Forum(5)).first())
            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID).first())
        }

    @Test
    fun `a topic's cancellation ends its action in the topic and the chat`() =
        runTest {
            val repository = repository()
            typeInTopic(ada, TdApi.MessageTopicForum(3))

            client.emit(updateChatAction(cancel, CHAT_ID, ada, TdApi.MessageTopicForum(3)))
            client.emit(updateChatAction(cancel, CHAT_ID, ada))

            assertEquals(emptyList<SenderAction>(), repository.actions(CHAT_ID, MessageTopic.Forum(3)).first())
            assertEquals(emptyMap<Long, Any>(), repository.actionsByChat.first())
        }

    @Test
    fun `a direct messages topic has the chat's actions, as TDLib names no such topic`() =
        runTest {
            val repository = repository()

            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            assertEquals(listOf(typing(ADA)), repository.actions(CHAT_ID, MessageTopic.DirectMessages(5)).first())
        }

    @Test
    fun `a topic's actions emit only that topic's changes`() =
        runTest {
            val repository = repository()

            repository.actions(CHAT_ID, MessageTopic.Forum(3)).test {
                assertEquals(emptyList<SenderAction>(), awaitItem())

                typeInTopic(ada, TdApi.MessageTopicForum(5))

                expectNoEvents()
            }
        }

    @Test
    fun `repeating the same action emits nothing`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            repository.actionsByChat.test {
                awaitItem()

                client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

                expectNoEvents()
            }
        }

    @Test
    fun `cancelling an action that is not kept emits nothing`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            repository.actionsByChat.test {
                awaitItem()

                client.emit(updateChatAction(cancel, CHAT_ID, grace))
                client.emit(updateChatAction(cancel, OTHER_CHAT_ID, ada))

                expectNoEvents()
            }
        }

    @Test
    fun `actions of one chat emit only that chat's changes`() =
        runTest {
            val repository = repository()

            repository.actions(CHAT_ID).test {
                assertEquals(emptyList<SenderAction>(), awaitItem())

                client.emit(updateChatAction(TdApi.ChatActionTyping(), OTHER_CHAT_ID, ada))
                client.emit(TdApi.UpdateOption("version", TdApi.OptionValueString("1.8.68")))

                expectNoEvents()
            }
        }

    @Test
    fun `forgets every action when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))
            typeInTopic(grace, TdApi.MessageTopicForum(3))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertEquals(emptyMap<Long, Any>(), repository.actionsByChat.first())
            assertEquals(emptyList<SenderAction>(), repository.actions(CHAT_ID, MessageTopic.Forum(3)).first())
        }

    /** [sender] typing in [topic], as TDLib reports it: for the topic, then for the chat. */
    private suspend fun typeInTopic(
        sender: TdApi.MessageSender,
        topic: TdApi.MessageTopic,
    ) {
        client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, sender, topic))
        client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, sender))
    }

    private companion object {
        const val CHAT_ID = -100L
        const val OTHER_CHAT_ID = -200L
        const val ADA = 42L
        const val GRACE = 43L
        const val LIN = 44L
    }
}
