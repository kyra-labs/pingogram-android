package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatInvite
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesChat
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPhotoInfo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Invitations and joining: `checkChatInviteLink`, `joinChatByInviteLink` and `joinChat`. */
class TdlibChatJoinRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatJoinRepository(client)

    private fun info(
        chatId: Long = 0,
        type: TdApi.InviteLinkChatType = TdApi.InviteLinkChatTypeSupergroup(),
        accessibleFor: Int = 0,
        createsJoinRequest: Boolean = false,
        subscription: TdApi.ChatInviteLinkSubscriptionInfo? = null,
    ) = TdApi.ChatInviteLinkInfo(
        chatId,
        accessibleFor,
        type,
        "Rust Berlin",
        chatPhotoInfo(),
        0,
        "Meetups every month",
        1234,
        longArrayOf(1, 2, 3),
        subscription,
        createsJoinRequest,
        false,
        TdApi.VerificationStatus(true, false, false, 0),
    )

    @Test
    fun `an invitation describes the chat before joining`() =
        runTest {
            client.on(TdApi.CheckChatInviteLink::class, TdResult.Success(info()))

            val invite = (repository.checkInvite("https://t.me/+abc") as Outcome.Success).value

            assertEquals("https://t.me/+abc", client.sentOf<TdApi.CheckChatInviteLink>().single().inviteLink)
            assertEquals(GroupKind.Supergroup, invite.kind)
            assertEquals("Rust Berlin", invite.title)
            assertEquals("Meetups every month", invite.description)
            assertEquals(1234, invite.memberCount)
            assertEquals(persistentListOf(1L, 2L, 3L), invite.memberUserIds)
            assertTrue(invite.isVerified)
            assertFalse(invite.isMember)
            assertFalse(invite.needsSubscription)
            assertTrue(invite.photo != null)
            // Only reads: nothing joined.
            assertEquals(1, client.sent.size)
        }

    @Test
    fun `a chat TDLib knows without a preview time is one the user is in`() =
        runTest {
            client.on(TdApi.CheckChatInviteLink::class, TdResult.Success(info(chatId = 9)))

            assertTrue((repository.checkInvite("https://t.me/+abc") as Outcome.Success).value.isMember)
        }

    @Test
    fun `a chat readable for a while is a preview, not membership`() =
        runTest {
            client.on(TdApi.CheckChatInviteLink::class, TdResult.Success(info(chatId = 9, accessibleFor = 300)))

            val invite = (repository.checkInvite("https://t.me/+abc") as Outcome.Success).value

            assertFalse(invite.isMember)
            assertEquals(300, invite.previewSeconds)
        }

    @Test
    fun `channels, basic groups, join requests and paid subscriptions are told apart`() =
        runTest {
            client.on(
                TdApi.CheckChatInviteLink::class,
                TdResult.Success(info(type = TdApi.InviteLinkChatTypeChannel(), createsJoinRequest = true)),
            )
            val channel = (repository.checkInvite("https://t.me/+c") as Outcome.Success).value
            client.on(
                TdApi.CheckChatInviteLink::class,
                TdResult.Success(
                    info(
                        type = TdApi.InviteLinkChatTypeBasicGroup(),
                        subscription = TdApi.ChatInviteLinkSubscriptionInfo(),
                    ),
                ),
            )
            val group = (repository.checkInvite("https://t.me/+g") as Outcome.Success).value

            assertEquals(GroupKind.Channel, channel.kind)
            assertTrue(channel.createsJoinRequest)
            assertEquals(GroupKind.BasicGroup, group.kind)
            assertTrue(group.needsSubscription)
        }

    @Test
    fun `an expired link is invalid`() =
        runTest {
            client.on(TdApi.CheckChatInviteLink::class, TdResult.Failure(TdError(400, "INVITE_HASH_EXPIRED")))

            assertEquals(Outcome.Failure(DomainError.InviteLinkInvalid), repository.checkInvite("https://t.me/+old"))
        }

    @Test
    fun `joining by link returns the chat joined`() =
        runTest {
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Success(TdApi.ChatJoinResultSuccess(9)))

            assertEquals(Outcome.Success(JoinResult.Joined(9)), repository.joinByInvite("https://t.me/+abc"))
            assertEquals("https://t.me/+abc", client.sentOf<TdApi.JoinChatByInviteLink>().single().inviteLink)
        }

    @Test
    fun `a request to join is sent, however Telegram says so`() =
        runTest {
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Success(TdApi.ChatJoinResultRequestSent()))
            assertEquals(Outcome.Success(JoinResult.RequestSent), repository.joinByInvite("https://t.me/+abc"))

            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Failure(TdError(400, "INVITE_REQUEST_SENT")))
            assertEquals(Outcome.Success(JoinResult.RequestSent), repository.joinByInvite("https://t.me/+abc"))
        }

    @Test
    fun `a bot's check and a refusal are their own results`() =
        runTest {
            client.on(TdApi.JoinChat::class, TdResult.Success(TdApi.ChatJoinResultGuardBotApprovalRequired(1, 2)))
            assertEquals(Outcome.Success(JoinResult.NeedsBotApproval), repository.join(9))

            client.on(TdApi.JoinChat::class, TdResult.Success(TdApi.ChatJoinResultDeclined()))
            assertEquals(Outcome.Success(JoinResult.Declined), repository.join(9))
            assertEquals(9L, client.sentOf<TdApi.JoinChat>().last().chatId)
        }

    @Test
    fun `joining fails with too many chats, a full group or a flood wait`() =
        runTest {
            client.on(TdApi.JoinChat::class, TdResult.Failure(TdError(400, "CHANNELS_TOO_MUCH")))
            assertEquals(Outcome.Failure(DomainError.TooManyChats), repository.join(9))

            client.on(TdApi.JoinChat::class, TdResult.Failure(TdError(400, "USERS_TOO_MUCH")))
            assertEquals(Outcome.Failure(DomainError.ChatFull), repository.join(9))

            client.on(TdApi.JoinChat::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")))
            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.join(9))
        }

    @Test
    fun `an invitation's text leaves the chat's title out`() {
        val invite = ChatInvite(1, GroupKind.Channel, "Secret plans", null, "Plans", 2)

        assertFalse("Secret plans" in invite.toString())
    }

    @Test
    fun `a channel's direct messages chat is the one its full info names, made known to TDLib`() =
        runTest {
            client.on(
                TdApi.GetSupergroupFullInfo::class,
                TdResult.Success(TdApi.SupergroupFullInfo().also { it.directMessagesChatId = DIRECT_MESSAGES }),
            )
            client.on(
                TdApi.GetChat::class,
                TdResult.Success(
                    TdApi.Chat().also {
                        it.id = DIRECT_MESSAGES
                        it.type = TdApi.ChatTypeSupergroup(DIRECT_MESSAGES_GROUP, false)
                    },
                ),
            )

            assertEquals(
                Outcome.Success(DirectMessagesChat(DIRECT_MESSAGES, DIRECT_MESSAGES_GROUP)),
                repository.directMessagesChat(CHANNEL),
            )
            assertEquals(CHANNEL, client.sentOf<TdApi.GetSupergroupFullInfo>().single().supergroupId)
            assertEquals(DIRECT_MESSAGES, client.sentOf<TdApi.GetChat>().single().chatId)
            assertTrue(client.sentOf<TdApi.JoinChat>().isEmpty())
        }

    @Test
    fun `a channel without direct messages has none`() =
        runTest {
            client.on(TdApi.GetSupergroupFullInfo::class, TdResult.Success(TdApi.SupergroupFullInfo()))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.directMessagesChat(CHANNEL))
            assertTrue(client.sentOf<TdApi.GetChat>().isEmpty())
        }

    private companion object {
        const val CHANNEL = 77L
        const val DIRECT_MESSAGES = -1_002_000_000_001L
        const val DIRECT_MESSAGES_GROUP = 2_000_000_001L
    }
}
