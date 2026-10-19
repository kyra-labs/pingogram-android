package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.basicGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.creatorStatus
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.leftStatus
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.memberStatus
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.supergroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [TdlibChatRepository.deleteOptions] and [TdlibChatRepository.deleteChat], which follow [ChatDeletion]. */
class ChatDeletionTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())

    private fun ok() = TdResult.Success(TdApi.Ok())

    private fun failure(error: TdApi.Error) = TdResult.Failure(TdError(error.code, error.message))

    private val privateChat = TdApi.ChatTypePrivate(42)
    private val secretChat = TdApi.ChatTypeSecret(SECRET_CHAT_ID, 42)
    private val basicGroupChat = TdApi.ChatTypeBasicGroup(BASIC_GROUP_ID)
    private val supergroupChat = TdApi.ChatTypeSupergroup(SUPERGROUP_ID, false)
    private val channelChat = TdApi.ChatTypeSupergroup(SUPERGROUP_ID, true)

    /**
     * Chat [CHAT_ID] of [type] as TDLib reports it now, with the current user's [status] in its group; every request
     * that deletes succeeds.
     */
    private fun givenChat(
        type: TdApi.ChatType,
        onlyForSelf: Boolean,
        forAll: Boolean,
        status: TdApi.ChatMemberStatus = memberStatus(),
    ) {
        val chat =
            chat(id = CHAT_ID, type = type, canBeDeletedOnlyForSelf = onlyForSelf, canBeDeletedForAllUsers = forAll)
        client.on(TdApi.GetChat::class, TdResult.Success(chat))
        client.on(TdApi.GetBasicGroup::class, TdResult.Success(basicGroup(id = BASIC_GROUP_ID, status = status)))
        client.on(TdApi.GetSupergroup::class, TdResult.Success(supergroup(id = SUPERGROUP_ID, status = status)))
        client.on(TdApi.DeleteChatHistory::class, ok())
        client.on(TdApi.DeleteChat::class, ok())
        client.on(TdApi.LeaveChat::class, ok())
        client.on(TdApi.CloseSecretChat::class, ok())
    }

    /** The requests sent to delete, without the ones that look the chat up. */
    private fun deletions() =
        client.sent
            .filterNot { it is TdApi.GetChat || it is TdApi.GetBasicGroup || it is TdApi.GetSupergroup }
            .map { it.describe() }

    private val removeFromLists = "DeleteChatHistory($CHAT_ID, removeFromChatList=true, revoke=false)"

    // deleteOptions

    @Test
    fun `deleteOptions reads the chat and the user's status in its group from TDLib`() =
        runTest {
            givenChat(supergroupChat, onlyForSelf = false, forAll = false)

            repository().deleteOptions(CHAT_ID)

            assertEquals(
                listOf("GetChat($CHAT_ID)", "GetSupergroup($SUPERGROUP_ID)"),
                client.sent.map { it.describe() },
            )
        }

    @Test
    fun `a private chat is deleted, for both users where TDLib allows it`() =
        runTest {
            givenChat(privateChat, onlyForSelf = true, forAll = true)

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = true)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `a private chat TDLib deletes only for self, such as Saved Messages, has no option for everyone`() =
        runTest {
            givenChat(privateChat, onlyForSelf = true, forAll = false)

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = false)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `an active secret chat is deleted, for both sides if chosen`() =
        runTest {
            givenChat(secretChat, onlyForSelf = false, forAll = true)

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = true)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `a member leaves a basic group`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = false, status = memberStatus())

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = false)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `the owner of a basic group can leave it or delete it for everyone`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = true, status = creatorStatus())

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = true)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `a basic group the user left is deleted`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = false, status = leftStatus())

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = false)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `the owner of a channel can leave it or delete it for everyone`() =
        runTest {
            givenChat(channelChat, onlyForSelf = false, forAll = true, status = creatorStatus())

            assertEquals(
                Outcome.Success(DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = true)),
                repository().deleteOptions(CHAT_ID),
            )
        }

    @Test
    fun `deleteOptions maps a chat TDLib does not know to NotFound`() =
        runTest {
            client.on(TdApi.GetChat::class, failure(TdApi.Error(400, "Chat not found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().deleteOptions(CHAT_ID))
        }

    @Test
    fun `deleteOptions fails when the group cannot be read`() =
        runTest {
            givenChat(supergroupChat, onlyForSelf = false, forAll = false)
            client.on(TdApi.GetSupergroup::class, failure(TdApiFixtures.floodWait(seconds = 3)))

            assertEquals(Outcome.Failure(DomainError.FloodWait(3)), repository().deleteOptions(CHAT_ID))
        }

    // deleteChat: private and secret chats

    @Test
    fun `deleteChat deletes a private chat's history and removes it from the lists`() =
        runTest {
            givenChat(privateChat, onlyForSelf = true, forAll = true)

            val result = repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf(removeFromLists), deletions())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `deleteChat for everyone deletes a private chat for both users`() =
        runTest {
            givenChat(privateChat, onlyForSelf = true, forAll = true)

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf("DeleteChatHistory($CHAT_ID, removeFromChatList=true, revoke=true)"), deletions())
        }

    @Test
    fun `deleteChat for everyone deletes only for self where TDLib allows nothing else`() =
        runTest {
            givenChat(privateChat, onlyForSelf = true, forAll = false)

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf(removeFromLists), deletions())
        }

    @Test
    fun `deleteChat closes an active secret chat, then deletes its history for self`() =
        runTest {
            givenChat(secretChat, onlyForSelf = false, forAll = true)

            repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf("CloseSecretChat($SECRET_CHAT_ID)", removeFromLists), deletions())
        }

    @Test
    fun `deleteChat for everyone deletes a secret chat on both sides`() =
        runTest {
            givenChat(secretChat, onlyForSelf = false, forAll = true)

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf("DeleteChat($CHAT_ID)"), deletions())
        }

    @Test
    fun `deleteChat only deletes the history of a closed secret chat`() =
        runTest {
            givenChat(secretChat, onlyForSelf = true, forAll = false)

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf(removeFromLists), deletions())
        }

    @Test
    fun `deleteChat keeps the history when closing a secret chat gets no answer in time`() =
        runTest {
            givenChat(secretChat, onlyForSelf = false, forAll = true)
            val never = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.CloseSecretChat::class) { never.await() }

            // runTest skips the 30 s timeout in virtual time.
            val result = repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf("CloseSecretChat($SECRET_CHAT_ID)"), deletions())
            assertEquals(Outcome.Failure(DomainError.Network), result)
        }

    // deleteChat: groups and channels

    @Test
    fun `deleteChat leaves a basic group, then deletes its history from the lists`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = false, status = memberStatus())

            repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf("LeaveChat($CHAT_ID)", removeFromLists), deletions())
        }

    @Test
    fun `deleteChat only deletes the history of a basic group the user left`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = false, status = leftStatus())

            repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf(removeFromLists), deletions())
        }

    @Test
    fun `deleteChat for everyone deletes the owner's basic group`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = true, status = creatorStatus())

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf("DeleteChat($CHAT_ID)"), deletions())
        }

    @Test
    fun `deleteChat lets the owner leave a basic group without deleting it`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = true, status = creatorStatus())

            repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf("LeaveChat($CHAT_ID)", removeFromLists), deletions())
        }

    @Test
    fun `deleteChat leaves a supergroup, which removes it from the lists`() =
        runTest {
            givenChat(supergroupChat, onlyForSelf = true, forAll = false, status = memberStatus())

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf("LeaveChat($CHAT_ID)"), deletions())
        }

    @Test
    fun `deleteChat for everyone deletes the owner's channel`() =
        runTest {
            givenChat(channelChat, onlyForSelf = false, forAll = true, status = creatorStatus())

            repository().deleteChat(CHAT_ID, forEveryone = true)

            assertEquals(listOf("DeleteChat($CHAT_ID)"), deletions())
        }

    @Test
    fun `deleteChat hides a listed channel the user is not a member of, such as an announcement`() =
        runTest {
            givenChat(channelChat, onlyForSelf = true, forAll = false, status = leftStatus())

            repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf(removeFromLists), deletions())
        }

    @Test
    fun `deleteChat sends nothing for a channel the user is not in, as it is in no list`() =
        runTest {
            givenChat(channelChat, onlyForSelf = false, forAll = false, status = leftStatus())

            val result = repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(emptyList<String>(), deletions())
            assertEquals(Outcome.Success(Unit), result)
        }

    @Test
    fun `deleteChat stops at the first request that fails`() =
        runTest {
            givenChat(basicGroupChat, onlyForSelf = true, forAll = false, status = memberStatus())
            client.on(TdApi.LeaveChat::class, failure(TdApiFixtures.floodWait(seconds = 20)))

            val result = repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(listOf("LeaveChat($CHAT_ID)"), deletions())
            assertEquals(Outcome.Failure(DomainError.FloodWait(20)), result)
        }

    @Test
    fun `deleteChat of a chat TDLib does not know deletes nothing`() =
        runTest {
            client.on(TdApi.GetChat::class, failure(TdApi.Error(400, "Chat not found")))

            val result = repository().deleteChat(CHAT_ID, forEveryone = false)

            assertEquals(emptyList<String>(), deletions())
            assertEquals(Outcome.Failure(DomainError.NotFound), result)
        }

    private companion object {
        const val CHAT_ID = -100L
        const val SECRET_CHAT_ID = 9
        const val BASIC_GROUP_ID = 5L
        const val SUPERGROUP_ID = 6L
    }
}
