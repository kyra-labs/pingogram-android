package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InviteLinkSettings
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibInviteLinkRepository] on a [FakeTdlibClient]: paging, creating and editing within limits, revoking, deleting.
 */
class TdlibInviteLinkRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibInviteLinkRepository(client)

    private fun link(
        url: String = "https://t.me/+abc",
        date: Int = 1_700_000_000,
        memberLimit: Int = 0,
    ) = TdApi.ChatInviteLink().also {
        it.inviteLink = url
        it.name = "Friends"
        it.creatorUserId = ME
        it.date = date
        it.memberLimit = memberLimit
        it.memberCount = 3
    }

    @Test
    fun `links page by the date and link of the last one`() =
        runTest {
            client.on(TdApi.GetChatInviteLinks::class, TdResult.Success(TdApi.ChatInviteLinks(5, arrayOf(link()))))

            val first = (repository.links(CHAT, ME, revoked = false) as Outcome.Success).value
            repository.links(CHAT, ME, revoked = true, after = first.links.last())

            assertEquals(5, first.totalCount)
            assertEquals("Friends", first.links.single().name)
            val (firstPage, nextPage) = client.sentOf<TdApi.GetChatInviteLinks>()
            assertEquals(0, firstPage.offsetDate)
            assertEquals("", firstPage.offsetInviteLink)
            assertEquals(1_700_000_000, nextPage.offsetDate)
            assertEquals("https://t.me/+abc", nextPage.offsetInviteLink)
            assertTrue(nextPage.isRevoked)
            assertEquals(ME, nextPage.creatorUserId)
        }

    @Test
    fun `a new link's settings are kept within what Telegram accepts`() =
        runTest {
            client.on(TdApi.CreateChatInviteLink::class, TdResult.Success(link(memberLimit = 10)))

            val settings =
                InviteLinkSettings(
                    name = " " + "n".repeat(40),
                    expirationDate = 1_800_000_000,
                    memberLimit = 500_000,
                    createsJoinRequest = false,
                )
            val created = (repository.create(CHAT, settings) as Outcome.Success).value
            repository.create(CHAT, InviteLinkSettings(memberLimit = 10, createsJoinRequest = true))

            assertEquals(10, created.memberLimit)
            val (limited, withRequests) = client.sentOf<TdApi.CreateChatInviteLink>()
            assertEquals(InviteLinkSettings.NAME_LENGTH_MAX, limited.name.length)
            assertEquals(InviteLinkSettings.MEMBER_LIMIT_MAX, limited.memberLimit)
            assertEquals(1_800_000_000, limited.expirationDate)
            assertEquals(0, withRequests.memberLimit)
            assertTrue(withRequests.createsJoinRequest)
        }

    @Test
    fun `reading, editing, revoking and deleting name the link`() =
        runTest {
            client.on(TdApi.EditChatInviteLink::class, TdResult.Success(link()))
            client.on(TdApi.GetChatInviteLink::class, TdResult.Success(link(memberLimit = 5)))
            client.on(TdApi.RevokeChatInviteLink::class, TdResult.Success(TdApi.ChatInviteLinks(0, emptyArray())))
            client.on(TdApi.DeleteRevokedChatInviteLink::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DeleteAllRevokedChatInviteLinks::class, TdResult.Success(TdApi.Ok()))

            assertEquals(5, (repository.link(CHAT, "https://t.me/+abc") as Outcome.Success).value.memberLimit)
            repository.edit(CHAT, "https://t.me/+abc", InviteLinkSettings(name = "Team"))
            assertEquals(Outcome.Success(Unit), repository.revoke(CHAT, "https://t.me/+abc"))
            repository.deleteRevoked(CHAT, "https://t.me/+old")
            repository.deleteAllRevoked(CHAT, ME)

            assertEquals("Team", client.sentOf<TdApi.EditChatInviteLink>().single().name)
            assertEquals("https://t.me/+abc", client.sentOf<TdApi.RevokeChatInviteLink>().single().inviteLink)
            assertEquals("https://t.me/+old", client.sentOf<TdApi.DeleteRevokedChatInviteLink>().single().inviteLink)
            assertEquals(ME, client.sentOf<TdApi.DeleteAllRevokedChatInviteLinks>().single().creatorUserId)
        }

    private companion object {
        const val CHAT = -100L
        const val ME = 1L
    }
}
