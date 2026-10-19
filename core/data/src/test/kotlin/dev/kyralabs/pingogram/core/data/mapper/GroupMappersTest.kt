package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroupMappersTest {
    @Test
    fun `maps a basic group`() {
        val group =
            TdApiFixtures
                .basicGroup(id = 5, memberCount = 12, status = TdApiFixtures.administratorStatus(), isActive = true)
                .toBasicGroup()

        assertEquals(BasicGroup(5, 12, ChatMemberStatus.Administrator(false), isActive = true, null), group)
    }

    @Test
    fun `maps the supergroup a basic group was upgraded to`() {
        val group = TdApiFixtures.basicGroup(isActive = false, upgradedToSupergroupId = 6).toBasicGroup()

        assertEquals(false to 6L, group.isActive to group.upgradedToSupergroupId)
    }

    @Test
    fun `maps a supergroup's active usernames, member count and kind`() {
        val supergroup =
            TdApiFixtures
                .supergroup(usernames = listOf("club", "club_two"), memberCount = 900, isChannel = true, isForum = true)
                .toSupergroup()

        assertEquals(listOf("club", "club_two"), supergroup.usernames)
        assertEquals(listOf(900, true, true), listOf(supergroup.memberCount, supergroup.isChannel, supergroup.isForum))
    }

    @Test
    fun `a supergroup without usernames has none`() {
        val tdSupergroup = TdApiFixtures.supergroup().apply { usernames = null }

        assertEquals(emptyList<String>(), tdSupergroup.toSupergroup().usernames)
    }

    @Test
    fun `maps the verification status of a supergroup to badges`() {
        val supergroup =
            TdApiFixtures.supergroup(verificationStatus = TdApi.VerificationStatus(true, true, true, 0)).toSupergroup()

        assertEquals(listOf(true, true, true), listOf(supergroup.isVerified, supergroup.isScam, supergroup.isFake))
    }

    @Test
    fun `a supergroup without a verification status has no badges`() {
        val supergroup = TdApiFixtures.supergroup(verificationStatus = null).toSupergroup()

        assertEquals(listOf(false, false, false), listOf(supergroup.isVerified, supergroup.isScam, supergroup.isFake))
    }

    @Test
    fun `maps every member status`() {
        val statuses =
            listOf(
                TdApiFixtures.creatorStatus(isMember = true),
                TdApiFixtures.creatorStatus(isMember = false),
                TdApiFixtures.administratorStatus(),
                TdApi.ChatMemberStatusAdministrator(
                    false,
                    TdApi.ChatAdministratorRights().also {
                        it.canPostMessages =
                            true
                    },
                ),
                TdApiFixtures.memberStatus(),
                TdApi.ChatMemberStatusRestricted(
                    true,
                    0,
                    TdApi.ChatPermissions().also { it.canSendBasicMessages = true },
                ),
                TdApiFixtures.restrictedStatus(isMember = false),
                TdApiFixtures.leftStatus(),
                TdApiFixtures.bannedStatus(),
                null,
            ).map { it.toChatMemberStatus() }

        assertEquals(
            listOf(
                ChatMemberStatus.Creator(isMember = true),
                ChatMemberStatus.Creator(isMember = false),
                ChatMemberStatus.Administrator(canPostMessages = false),
                ChatMemberStatus.Administrator(canPostMessages = true),
                ChatMemberStatus.Member,
                ChatMemberStatus.Restricted(isMember = true, canSendBasicMessages = true),
                ChatMemberStatus.Restricted(isMember = false, canSendBasicMessages = false),
                ChatMemberStatus.Left,
                ChatMemberStatus.Banned,
                ChatMemberStatus.Left,
            ),
            statuses,
        )
    }

    @Test
    fun `a basic group that was not upgraded has no supergroup`() {
        assertNull(TdApiFixtures.basicGroup(upgradedToSupergroupId = 0).toBasicGroup().upgradedToSupergroupId)
    }
}
