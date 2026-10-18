package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupModelTest {
    @Test
    fun `owners, administrators, members and restricted members are members`() {
        val statuses =
            listOf(
                ChatMemberStatus.Creator(isMember = true),
                ChatMemberStatus.Administrator,
                ChatMemberStatus.Member,
                ChatMemberStatus.Restricted(isMember = true),
            )

        assertTrue(statuses.all { it.isMember })
    }

    @Test
    fun `an owner or restricted user who left, and left or banned users are not members`() {
        val statuses =
            listOf(
                ChatMemberStatus.Creator(isMember = false),
                ChatMemberStatus.Restricted(isMember = false),
                ChatMemberStatus.Left,
                ChatMemberStatus.Banned,
            )

        assertFalse(statuses.any { it.isMember })
    }

    @Test
    fun `toString of a supergroup leaves the usernames out`() {
        val supergroup =
            Supergroup(
                id = 6,
                usernames = persistentListOf("secret_club"),
                memberCount = 0,
                status = ChatMemberStatus.Member,
                isChannel = true,
                isForum = false,
                isVerified = false,
                isScam = false,
                isFake = false,
            )

        assertEquals("Supergroup(id=6, status=Member, isChannel=true, isForum=false, …)", supergroup.toString())
    }
}
