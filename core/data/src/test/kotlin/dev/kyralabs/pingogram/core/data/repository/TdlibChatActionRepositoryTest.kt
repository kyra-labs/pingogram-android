package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateChatAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibChatActionRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibChatActionRepository(client, backgroundScope)

    private val ada = TdApi.MessageSenderUser(ADA)
    private val grace = TdApi.MessageSenderUser(GRACE)
    private val cancel = TdApi.ChatActionCancel()

    private fun typing(userId: Long) = SenderAction(MessageSender.User(userId), ChatAction.Typing)

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
    fun `actions of a topic are ignored, as TDLib reports them for the chat too`() =
        runTest {
            val repository = repository()
            client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, ada))

            repository.actionsByChat.test {
                awaitItem()

                client.emit(updateChatAction(TdApi.ChatActionTyping(), CHAT_ID, grace, TdApi.MessageTopicForum(3)))
                // The cancellation TDLib sends for the topic a sender moved away from, who still acts in the chat.
                client.emit(updateChatAction(cancel, CHAT_ID, ada, TdApi.MessageTopicThread(7)))

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

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertEquals(emptyMap<Long, Any>(), repository.actionsByChat.first())
        }

    private companion object {
        const val CHAT_ID = -100L
        const val OTHER_CHAT_ID = -200L
        const val ADA = 42L
        const val GRACE = 43L
    }
}
