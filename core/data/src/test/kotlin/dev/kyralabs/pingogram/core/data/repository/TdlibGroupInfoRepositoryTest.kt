package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibGroupInfoRepository] on a [FakeTdlibClient]: a supergroup's and a basic group's info and its changes, the
 * current user's rights, failures, and the members' pages for each filter.
 */
class TdlibGroupInfoRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibGroupInfoRepository(client)

    private val adminRights =
        TdApi.ChatAdministratorRights().also {
            it.canChangeInfo = true
            it.canRestrictMembers = true
            it.canInviteUsers = true
        }

    private fun supergroupFullInfo(
        description: String = "Rust in Berlin, t.me/rustberlin",
        members: Int = 1_024,
    ) = TdApi.SupergroupFullInfo().also {
        it.description = description
        it.memberCount = members
        it.administratorCount = 3
        it.restrictedCount = 1
        it.bannedCount = 2
        it.canGetMembers = true
        it.slowModeDelay = 30
        it.inviteLink = inviteLink()
    }

    private fun inviteLink() =
        TdApi.ChatInviteLink().also {
            it.inviteLink = "https://t.me/+abc"
            it.name = ""
            it.isPrimary = true
        }

    private fun answerSupergroup(
        status: TdApi.ChatMemberStatus = TdApi.ChatMemberStatusAdministrator(true, adminRights),
        full: TdResult<TdApi.SupergroupFullInfo> = TdResult.Success(supergroupFullInfo()),
        isChannel: Boolean = false,
    ) {
        client.on(
            TdApi.GetChat::class,
            TdResult.Success(TdApiFixtures.chat(id = CHAT, type = TdApi.ChatTypeSupergroup(SUPERGROUP, isChannel))),
        )
        client.on(
            TdApi.GetSupergroup::class,
            TdResult.Success(
                TdApiFixtures.supergroup(
                    id = SUPERGROUP,
                    usernames = listOf("rustberlin"),
                    memberCount = 1_000,
                    status = status,
                    isChannel = isChannel,
                ),
            ),
        )
        client.on(TdApi.GetSupergroupFullInfo::class, full)
        val link = TdApi.TextEntity(16, 15, TdApi.TextEntityTypeUrl())
        client.on(TdApi.GetTextEntities::class, TdResult.Success(TdApi.TextEntities(arrayOf(link))))
    }

    @Test
    fun `a supergroup comes with its full info, the description's links and the current user's rights`() =
        runTest {
            answerSupergroup()

            repository.groupInfo(CHAT).test {
                var info = (awaitItem() as Outcome.Success).value
                if (info.details == null) info = (awaitItem() as Outcome.Success).value
                assertEquals(GroupKind.Supergroup, info.kind)
                assertEquals(listOf("rustberlin"), info.usernames)
                val rights = info.myRights
                assertTrue(rights.canChangeInfo && rights.canRestrictMembers && rights.canInviteUsers)
                assertEquals(false, rights.canPromoteMembers)
                val details = checkNotNull(info.details)
                assertEquals(1_024, details.memberCount)
                assertEquals(3, details.administratorCount)
                assertEquals(30, details.slowModeDelay)
                assertEquals(
                    TextEntityType.Url,
                    details.description.entities
                        .single()
                        .type,
                )
                assertEquals("https://t.me/+abc", details.primaryInviteLink?.link)
                assertTrue(info.defaultPermissions.canSendBasicMessages)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the info follows the group's and its full info's updates`() =
        runTest {
            answerSupergroup()

            repository.groupInfo(CHAT).test {
                skipUntilDetails()
                client.emit(TdApi.UpdateSupergroupFullInfo(SUPERGROUP, supergroupFullInfo(members = 2_000)))
                assertEquals(2_000, (awaitItem() as Outcome.Success).value.details?.memberCount)

                val owner = TdApiFixtures.supergroup(id = SUPERGROUP, status = TdApiFixtures.creatorStatus())
                client.emit(TdApiFixtures.updateSupergroup(owner))
                val info = (awaitItem() as Outcome.Success).value
                assertEquals(MemberRole.Owner(isMember = true), info.myRole)
                assertEquals(AdministratorRights.All, info.myRights)

                val permissions = TdApi.ChatPermissions().also { it.canSendPolls = true }
                client.emit(TdApi.UpdateChatPermissions(CHAT, permissions))
                val restricted = (awaitItem() as Outcome.Success).value.defaultPermissions
                assertTrue(restricted.canSendPolls)
                assertEquals(false, restricted.canSendBasicMessages)

                // Another group's update changes nothing.
                client.emit(TdApi.UpdateSupergroupFullInfo(SUPERGROUP + 1, supergroupFullInfo(members = 1)))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a full info that cannot be read leaves the details out and says why`() =
        runTest {
            answerSupergroup(full = TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")))

            repository.groupInfo(CHAT).test {
                var info = (awaitItem() as Outcome.Success).value
                if (info.detailsError == null) info = (awaitItem() as Outcome.Success).value
                assertNull(info.details)
                assertEquals(DomainError.FloodWait(9), info.detailsError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a private chat has no group info`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(TdApiFixtures.chat(id = CHAT)))

            repository.groupInfo(CHAT).test {
                assertEquals(Outcome.Failure(DomainError.NotFound), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a basic group counts its administrators from the members of its full info`() =
        runTest {
            client.on(
                TdApi.GetChat::class,
                TdResult.Success(TdApiFixtures.chat(id = CHAT, type = TdApi.ChatTypeBasicGroup(BASIC_GROUP))),
            )
            client.on(
                TdApi.GetBasicGroup::class,
                TdResult.Success(TdApiFixtures.basicGroup(id = BASIC_GROUP, status = TdApiFixtures.creatorStatus())),
            )
            val full =
                TdApi.BasicGroupFullInfo().also {
                    it.description = ""
                    it.members =
                        arrayOf(
                            member(1, TdApiFixtures.creatorStatus()),
                            member(2, TdApi.ChatMemberStatusAdministrator(true, adminRights)),
                            member(3, TdApiFixtures.memberStatus()),
                        )
                }
            client.on(TdApi.GetBasicGroupFullInfo::class, TdResult.Success(full))

            repository.groupInfo(CHAT).test {
                var info = (awaitItem() as Outcome.Success).value
                if (info.details == null) info = (awaitItem() as Outcome.Success).value
                assertEquals(GroupKind.BasicGroup, info.kind)
                val details = checkNotNull(info.details)
                assertEquals(3, details.memberCount)
                assertEquals(2, details.administratorCount)
                assertEquals("", details.description.text)
                assertEquals(MessageSender.User(2), details.members[1].sender)
                cancelAndIgnoreRemainingEvents()
            }
            // An empty description needs no entities.
            assertTrue(client.sentOf<TdApi.GetTextEntities>().isEmpty())
        }

    @Test
    fun `a supergroup's members come a page at a time, by filter and query`() =
        runTest {
            answerSupergroup()
            val tagged = member(7, TdApi.ChatMemberStatusAdministrator(false, adminRights)).also { it.tag = "Mod" }
            client.on(TdApi.GetSupergroupMembers::class, TdResult.Success(TdApi.ChatMembers(40, arrayOf(tagged))))

            val page = (repository.members(CHAT, MemberFilter.Recent, offset = 20) as Outcome.Success).value
            repository.members(CHAT, MemberFilter.Recent, query = "ada")
            repository.members(CHAT, MemberFilter.Banned, query = "spam")
            repository.members(CHAT, MemberFilter.Administrators, query = "ignored")

            assertEquals(40, page.totalCount)
            assertEquals("Mod", page.members.single().tag)
            assertInstanceOf(MemberRole.Administrator::class.java, page.members.single().role)
            val sent = client.sentOf<TdApi.GetSupergroupMembers>()
            assertEquals(20, sent[0].offset)
            assertInstanceOf(TdApi.SupergroupMembersFilterRecent::class.java, sent[0].filter)
            assertEquals("ada", (sent[1].filter as TdApi.SupergroupMembersFilterSearch).query)
            assertEquals("spam", (sent[2].filter as TdApi.SupergroupMembersFilterBanned).query)
            assertInstanceOf(TdApi.SupergroupMembersFilterAdministrators::class.java, sent[3].filter)
        }

    @Test
    fun `a basic group's members come at once, and it has no banned members`() =
        runTest {
            client.on(
                TdApi.GetChat::class,
                TdResult.Success(TdApiFixtures.chat(id = CHAT, type = TdApi.ChatTypeBasicGroup(BASIC_GROUP))),
            )
            client.on(
                TdApi.SearchChatMembers::class,
                TdResult.Success(TdApi.ChatMembers(1, arrayOf(member(1, TdApiFixtures.memberStatus())))),
            )

            val first = (repository.members(CHAT, MemberFilter.Recent, query = "a") as Outcome.Success).value
            val next = (repository.members(CHAT, MemberFilter.Recent, offset = 1) as Outcome.Success).value
            val banned = (repository.members(CHAT, MemberFilter.Banned) as Outcome.Success).value

            assertEquals(1, first.members.size)
            assertTrue(next.members.isEmpty() && banned.members.isEmpty())
            val search = client.sentOf<TdApi.SearchChatMembers>().single()
            assertEquals("a", search.query)
            assertInstanceOf(TdApi.ChatMembersFilterMembers::class.java, search.filter)
        }

    @Test
    fun `a member is looked up with their role`() =
        runTest {
            val restricted =
                TdApi.ChatMemberStatusRestricted(
                    true,
                    1_800_000_000,
                    TdApi.ChatPermissions().also { it.canSendBasicMessages = true },
                )
            client.on(TdApi.GetChatMember::class, TdResult.Success(member(9, restricted)))

            val found = (repository.member(CHAT, 9) as Outcome.Success).value

            val role = found.role as MemberRole.Restricted
            assertEquals(1_800_000_000L, role.untilDate)
            assertTrue(role.permissions.canSendBasicMessages)
            assertEquals(false, role.permissions.canSendPhotos)
        }

    private suspend fun ReceiveTurbine<Outcome<GroupInfo>>.skipUntilDetails() {
        while ((awaitItem() as Outcome.Success).value.details == null) {
            // The group before its full info.
        }
    }

    private fun member(
        userId: Long,
        status: TdApi.ChatMemberStatus,
    ) = TdApi.ChatMember(TdApi.MessageSenderUser(userId), "", 0, 1_700_000_000, status)

    private companion object {
        const val CHAT = -100L
        const val SUPERGROUP = 6L
        const val BASIC_GROUP = 5L
    }
}
