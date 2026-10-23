package dev.kyralabs.pingogram.feature.profile.invite

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibChatJoinRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.profile.chatinfo.GroupViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The join sheet of an invite link: checked once, joined only on "Join". */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatInviteViewModelTest : GroupViewModelTest() {
    private fun info(
        chatId: Long = 0,
        type: TdApi.InviteLinkChatType = TdApi.InviteLinkChatTypeSupergroup(),
        createsJoinRequest: Boolean = false,
        subscription: TdApi.ChatInviteLinkSubscriptionInfo? = null,
        members: LongArray = longArrayOf(ADA),
    ) = TdApi.ChatInviteLinkInfo(
        chatId,
        0,
        type,
        "Rust Berlin",
        null,
        0,
        "Meetups",
        1_284,
        members,
        subscription,
        createsJoinRequest,
        false,
        null,
    )

    private fun TestScope.viewModel(
        checked: TdResult<TdApi.ChatInviteLinkInfo> = TdResult.Success(info()),
    ): ChatInviteViewModel {
        client.on(TdApi.CheckChatInviteLink::class, checked)
        client.on(TdApi.GetUser::class, TdResult.Success(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        val viewModel = ChatInviteViewModel(LINK, TdlibChatJoinRepository(client), users)
        created += viewModel
        return viewModel
    }

    private val ChatInviteViewModel.content get() = uiState.value as ChatInviteUiState.Content

    @Test
    fun `shows the chat after the first frame, joining nothing`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ChatInviteUiState.Loading, viewModel.uiState.value)

            runCurrent()

            val invite = viewModel.content.invite
            assertEquals("Rust Berlin", invite.title)
            assertEquals(GroupKind.Supergroup, invite.kind)
            assertEquals(1_284, invite.memberCount)
            assertEquals(listOf(ADA), invite.members.map { it.id })
            assertTrue(client.sentOf<TdApi.JoinChatByInviteLink>().isEmpty())
        }

    @Test
    fun `a chat the user is in opens at once`() =
        runTest {
            val viewModel = viewModel(TdResult.Success(info(chatId = CHAT)))

            viewModel.effects.test {
                runCurrent()
                assertEquals(ChatInviteEffect.OpenChat(CHAT), awaitItem())
            }
            assertTrue(client.sentOf<TdApi.JoinChatByInviteLink>().isEmpty())
        }

    @Test
    fun `joining opens the chat joined`() =
        runTest {
            val viewModel = viewModel()
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Success(TdApi.ChatJoinResultSuccess(CHAT)))
            runCurrent()

            viewModel.effects.test {
                viewModel.onEvent(ChatInviteEvent.Join)
                assertTrue(viewModel.content.isJoining)
                runCurrent()
                assertEquals(ChatInviteEffect.OpenChat(CHAT), awaitItem())
            }
            assertEquals(LINK, client.sentOf<TdApi.JoinChatByInviteLink>().single().inviteLink)
        }

    @Test
    fun `a chat that accepts members by request says the request was sent`() =
        runTest {
            val channel = info(type = TdApi.InviteLinkChatTypeChannel(), createsJoinRequest = true)
            val viewModel = viewModel(TdResult.Success(channel))
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Success(TdApi.ChatJoinResultRequestSent()))
            runCurrent()
            assertTrue(viewModel.content.invite.createsJoinRequest)

            viewModel.onEvent(ChatInviteEvent.Join)
            runCurrent()

            assertEquals(ChatInviteUiState.RequestSent(GroupKind.Channel), viewModel.uiState.value)
        }

    @Test
    fun `a paid chat cannot be joined here`() =
        runTest {
            val viewModel = viewModel(TdResult.Success(info(subscription = TdApi.ChatInviteLinkSubscriptionInfo())))
            runCurrent()

            viewModel.onEvent(ChatInviteEvent.Join)
            runCurrent()

            assertFalse(viewModel.content.canJoin)
            assertTrue(client.sentOf<TdApi.JoinChatByInviteLink>().isEmpty())
        }

    @Test
    fun `a failure to join shows on the sheet, which can join again`() =
        runTest {
            val viewModel = viewModel()
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Failure(TdError(400, "CHANNELS_TOO_MUCH")))
            runCurrent()

            viewModel.onEvent(ChatInviteEvent.Join)
            runCurrent()

            assertEquals(DomainError.TooManyChats, viewModel.content.joinError)
            assertTrue(viewModel.content.canJoin)
        }

    @Test
    fun `a flood wait while joining shows its wait`() =
        runTest {
            val viewModel = viewModel()
            val floodWait = TdError(429, "Too Many Requests: retry after 30")
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Failure(floodWait))
            runCurrent()

            viewModel.onEvent(ChatInviteEvent.Join)
            runCurrent()

            assertEquals(DomainError.FloodWait(30), viewModel.content.joinError)
        }

    @Test
    fun `a bot's check says Pingogram cannot show it`() =
        runTest {
            val viewModel = viewModel()
            val needsBot = TdApi.ChatJoinResultGuardBotApprovalRequired(1, 2)
            client.on(TdApi.JoinChatByInviteLink::class, TdResult.Success(needsBot))
            runCurrent()

            viewModel.onEvent(ChatInviteEvent.Join)
            runCurrent()

            assertEquals(JoinNotice.NeedsBotApproval, viewModel.content.notice)
        }

    @Test
    fun `an expired link cannot be tried again`() =
        runTest {
            val viewModel = viewModel(TdResult.Failure(TdError(400, "INVITE_HASH_EXPIRED")))
            runCurrent()

            val failed = ChatInviteUiState.Failed(DomainError.InviteLinkInvalid, canRetry = false)
            assertEquals(failed, viewModel.uiState.value)
        }

    @Test
    fun `without network it checks again on retry`() =
        runTest {
            val viewModel = viewModel(TdResult.Failure(TdError(500, "Network is unreachable")))
            runCurrent()
            assertEquals(ChatInviteUiState.Failed(DomainError.Network, canRetry = true), viewModel.uiState.value)

            client.on(TdApi.CheckChatInviteLink::class, TdResult.Success(info()))
            viewModel.onEvent(ChatInviteEvent.Retry)
            runCurrent()

            assertEquals("Rust Berlin", viewModel.content.invite.title)
        }

    private companion object {
        const val LINK = "https://t.me/+abc"
        const val ADA = 2L
    }
}
