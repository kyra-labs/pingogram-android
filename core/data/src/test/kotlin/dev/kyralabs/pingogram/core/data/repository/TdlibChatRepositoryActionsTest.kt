package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateChatNotificationSettings
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The actions of the chat list on [TdlibChatRepository] but deletion (`ChatDeletionTest`). */
class TdlibChatRepositoryActionsTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())

    private fun ok() = TdResult.Success(TdApi.Ok())

    private fun failure(error: TdApi.Error) = TdResult.Failure(TdError(error.code, error.message))

    /** Every request sent so far, described. */
    private fun sent() = client.sent.map { it.describe() }

    /** Answers every chat action with Ok. */
    private fun acceptActions() {
        client.on(TdApi.ToggleChatIsPinned::class, ok())
        client.on(TdApi.SetChatNotificationSettings::class, ok())
        client.on(TdApi.AddChatToList::class, ok())
        client.on(TdApi.ToggleChatIsMarkedAsUnread::class, ok())
        client.on(TdApi.ViewMessages::class, ok())
        client.on(TdApi.ReadAllChatMentions::class, ok())
        client.on(TdApi.ReadAllChatReactions::class, ok())
    }

    // setPinned

    @Test
    fun `setPinned pins the chat in the list`() =
        runTest {
            acceptActions()

            val result = repository().setPinned(CHAT_ID, ChatListType.Folder(3), isPinned = true)

            assertEquals(listOf("ToggleChatIsPinned(Folder(3), $CHAT_ID, isPinned=true)"), sent())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `setPinned unpins the chat`() =
        runTest {
            acceptActions()

            repository().setPinned(CHAT_ID, ChatListType.Archive, isPinned = false)

            assertEquals(listOf("ToggleChatIsPinned(Archive, $CHAT_ID, isPinned=false)"), sent())
        }

    @Test
    fun `setPinned reports a full list of pinned chats as TooManyPinnedChats`() =
        runTest {
            val limit = TdApi.Error(400, "The maximum number of pinned chats exceeded")
            client.on(TdApi.ToggleChatIsPinned::class, failure(limit))

            val result = repository().setPinned(CHAT_ID, ChatListType.Main, isPinned = true)

            assertEquals(Outcome.Failure(DomainError.TooManyPinnedChats), result)
        }

    @Test
    fun `setPinned maps a flood wait`() =
        runTest {
            client.on(TdApi.ToggleChatIsPinned::class, failure(TdApiFixtures.floodWait(seconds = 30)))

            val result = repository().setPinned(CHAT_ID, ChatListType.Main, isPinned = true)

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), result)
        }

    // setMuted

    @Test
    fun `setMuted mutes the chat forever with a mute of its own`() =
        runTest {
            acceptActions()
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, notificationSettings = chatNotificationSettings())))

            val result = repository.setMuted(CHAT_ID, isMuted = true)

            val request = client.sentOf<TdApi.SetChatNotificationSettings>().single()
            assertEquals(CHAT_ID, request.chatId)
            assertEquals(
                false to Int.MAX_VALUE,
                request.notificationSettings.let { it.useDefaultMuteFor to it.muteFor },
            )
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `setMuted unmutes the chat with a mute of 0 of its own`() =
        runTest {
            acceptActions()
            val repository = repository()
            val muted = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600)
            client.emit(updateNewChat(chat(id = CHAT_ID, notificationSettings = muted)))

            repository.setMuted(CHAT_ID, isMuted = false)

            val settings = client.sentOf<TdApi.SetChatNotificationSettings>().single().notificationSettings
            assertEquals(false to 0, settings.useDefaultMuteFor to settings.muteFor)
        }

    @Test
    fun `setMuted keeps the chat's other settings, as the last update reported them`() =
        runTest {
            acceptActions()
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, notificationSettings = chatNotificationSettings())))
            val custom =
                chatNotificationSettings(
                    useDefaultSound = false,
                    soundId = 7,
                    useDefaultShowPreview = false,
                    showPreview = false,
                )
            client.emit(updateChatNotificationSettings(CHAT_ID, custom))

            repository.setMuted(CHAT_ID, isMuted = true)

            val settings = client.sentOf<TdApi.SetChatNotificationSettings>().single().notificationSettings
            assertEquals(
                listOf<Any>(false, 7L, false, false),
                listOf(
                    settings.useDefaultSound,
                    settings.soundId,
                    settings.useDefaultShowPreview,
                    settings.showPreview,
                ),
            )
        }

    @Test
    fun `setMuted of a chat TDLib has not announced fails with NotFound without a request`() =
        runTest {
            val result = repository().setMuted(CHAT_ID, isMuted = true)

            assertEquals(Outcome.Failure(DomainError.NotFound), result)
            assertEquals(emptyList<String>(), sent())
        }

    @Test
    fun `setMuted fails with Network when TDLib does not answer in time`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetChatNotificationSettings::class) { never.await() }
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID)))

            // runTest skips the 30 s timeout in virtual time.
            assertEquals(Outcome.Failure(DomainError.Network), repository.setMuted(CHAT_ID, isMuted = true))
        }

    // setArchived

    @Test
    fun `setArchived adds the chat to the archive`() =
        runTest {
            acceptActions()

            val result = repository().setArchived(CHAT_ID, isArchived = true)

            assertEquals(listOf("AddChatToList($CHAT_ID, Archive)"), sent())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `setArchived false adds the chat back to the main list`() =
        runTest {
            acceptActions()

            repository().setArchived(CHAT_ID, isArchived = false)

            assertEquals(listOf("AddChatToList($CHAT_ID, Main)"), sent())
        }

    @Test
    fun `setArchived maps a flood wait`() =
        runTest {
            client.on(TdApi.AddChatToList::class, failure(TdApiFixtures.floodWait(seconds = 5)))

            assertEquals(
                Outcome.Failure(DomainError.FloodWait(5)),
                repository().setArchived(CHAT_ID, isArchived = true),
            )
        }

    // markAsUnread and markAsRead

    @Test
    fun `markAsUnread marks the chat as unread`() =
        runTest {
            acceptActions()

            val result = repository().markAsUnread(CHAT_ID)

            assertEquals(listOf("ToggleChatIsMarkedAsUnread($CHAT_ID, true)"), sent())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `markAsRead views the last message from the chat list, then reads all mentions and reactions`() =
        runTest {
            acceptActions()
            val repository = repository()
            client.emit(
                updateNewChat(
                    chat(
                        id = CHAT_ID,
                        lastMessage = TdApiFixtures.message(id = LAST_MESSAGE_ID, chatId = CHAT_ID),
                        unreadCount = 5,
                        unreadMentionCount = 2,
                        unreadReactionCount = 1,
                    ),
                ),
            )

            val result = repository.markAsRead(CHAT_ID)

            assertEquals(
                listOf(
                    "ViewMessages($CHAT_ID, [$LAST_MESSAGE_ID], MessageSourceChatList, forceRead=true)",
                    "ReadAllChatMentions($CHAT_ID)",
                    "ReadAllChatReactions($CHAT_ID)",
                ),
                sent(),
            )
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `markAsRead of a chat with only unread messages only views the last message`() =
        runTest {
            acceptActions()
            val repository = repository()
            val lastMessage = TdApiFixtures.message(id = LAST_MESSAGE_ID, chatId = CHAT_ID)
            client.emit(updateNewChat(chat(id = CHAT_ID, lastMessage = lastMessage, unreadCount = 5)))

            repository.markAsRead(CHAT_ID)

            assertEquals(
                listOf("ViewMessages($CHAT_ID, [$LAST_MESSAGE_ID], MessageSourceChatList, forceRead=true)"),
                sent(),
            )
        }

    @Test
    fun `markAsRead of a marked chat views the last message, which also removes the mark`() =
        runTest {
            acceptActions()
            val repository = repository()
            val lastMessage = TdApiFixtures.message(id = LAST_MESSAGE_ID, chatId = CHAT_ID)
            client.emit(updateNewChat(chat(id = CHAT_ID, lastMessage = lastMessage, isMarkedAsUnread = true)))

            repository.markAsRead(CHAT_ID)

            assertEquals(
                listOf("ViewMessages($CHAT_ID, [$LAST_MESSAGE_ID], MessageSourceChatList, forceRead=true)"),
                sent(),
            )
        }

    @Test
    fun `markAsRead of a marked chat without a last message removes the mark`() =
        runTest {
            acceptActions()
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, lastMessage = null, isMarkedAsUnread = true)))

            repository.markAsRead(CHAT_ID)

            assertEquals(listOf("ToggleChatIsMarkedAsUnread($CHAT_ID, false)"), sent())
        }

    @Test
    fun `markAsRead of a read chat sends nothing`() =
        runTest {
            acceptActions()
            val repository = repository()
            val lastMessage = TdApiFixtures.message(id = LAST_MESSAGE_ID, chatId = CHAT_ID)
            client.emit(updateNewChat(chat(id = CHAT_ID, lastMessage = lastMessage, unreadCount = 0)))

            val result = repository.markAsRead(CHAT_ID)

            assertEquals(emptyList<String>(), sent())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `markAsRead stops at the first request that fails`() =
        runTest {
            acceptActions()
            client.on(TdApi.ViewMessages::class, failure(TdApiFixtures.floodWait(seconds = 10)))
            val repository = repository()
            val lastMessage = TdApiFixtures.message(id = LAST_MESSAGE_ID, chatId = CHAT_ID)
            client.emit(
                updateNewChat(chat(id = CHAT_ID, lastMessage = lastMessage, unreadCount = 1, unreadMentionCount = 1)),
            )

            val result = repository.markAsRead(CHAT_ID)

            assertEquals(listOf("ViewMessages"), client.sent.map { it.javaClass.simpleName })
            assertEquals(Outcome.Failure(DomainError.FloodWait(10)), result)
        }

    @Test
    fun `markAsRead of a chat TDLib has not announced fails with NotFound without a request`() =
        runTest {
            val result = repository().markAsRead(CHAT_ID)

            assertEquals(Outcome.Failure(DomainError.NotFound), result)
            assertEquals(emptyList<String>(), sent())
        }

    // openChat and closeChat

    @Test
    fun `openChat tells TDLib that the chat is open`() =
        runTest {
            client.on(TdApi.OpenChat::class, ok())

            val result = repository().openChat(CHAT_ID)

            assertEquals(listOf(CHAT_ID), client.sentOf<TdApi.OpenChat>().map { it.chatId })
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `closeChat tells TDLib that the chat is closed`() =
        runTest {
            client.on(TdApi.CloseChat::class, ok())

            val result = repository().closeChat(CHAT_ID)

            assertEquals(listOf(CHAT_ID), client.sentOf<TdApi.CloseChat>().map { it.chatId })
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `openChat reports an unknown chat as NotFound`() =
        runTest {
            client.on(TdApi.OpenChat::class, failure(TdApi.Error(400, "Chat not found")))

            val result = repository().openChat(CHAT_ID)

            assertEquals(Outcome.Failure(DomainError.NotFound), result)
        }

    // viewMessages

    @Test
    fun `viewMessages reports the messages as viewed in the open chat's history, without forcing them read`() =
        runTest {
            client.on(TdApi.ViewMessages::class, ok())

            val result = repository().viewMessages(CHAT_ID, listOf(LAST_MESSAGE_ID, LAST_MESSAGE_ID - (1L shl 20)))

            assertEquals(
                listOf(
                    "ViewMessages($CHAT_ID, [$LAST_MESSAGE_ID, ${LAST_MESSAGE_ID - (1L shl 20)}], " +
                        "MessageSourceChatHistory, forceRead=false)",
                ),
                sent(),
            )
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `viewMessages reports a failure`() =
        runTest {
            client.on(TdApi.ViewMessages::class, failure(TdApi.Error(400, "Chat not found")))

            val result = repository().viewMessages(CHAT_ID, listOf(LAST_MESSAGE_ID))

            assertEquals(Outcome.Failure(DomainError.NotFound), result)
        }

    // privateChat

    @Test
    fun `privateChat asks TDLib for the user's chat without forcing it`() =
        runTest {
            client.on(
                TdApi.CreatePrivateChat::class,
            ) { request -> TdResult.Success(chat(id = request.userId, title = "Me")) }

            val result = repository().privateChat(USER_ID)

            val request = client.sentOf<TdApi.CreatePrivateChat>().single()
            assertEquals(USER_ID to false, request.userId to request.force)
            assertEquals(USER_ID, (result as Outcome.Success).value.id)
        }

    @Test
    fun `privateChat reports a user TDLib does not know`() =
        runTest {
            client.on(TdApi.CreatePrivateChat::class, failure(TdApi.Error(400, "User not found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().privateChat(USER_ID))
        }

    private companion object {
        const val USER_ID = 42L
        const val CHAT_ID = 100L
        const val LAST_MESSAGE_ID = 7L shl 20
    }
}
