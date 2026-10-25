package dev.kyralabs.pingogram.feature.chat.ui.topics

import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** What the user may do with a forum's topics, from their role and the forum's permissions, as Telegram decides it. */
class TopicRightsTest {
    @Test
    fun `the owner and administrators who manage topics may do everything, deleting with the right to delete`() {
        val owner = forumInfo(MemberRole.Owner(isMember = true)).forumRights()
        val admin = forumInfo(topicAdmin(canDelete = false)).forumRights()

        assertEquals(ForumRights(canCreate = true, canManage = true, canDelete = true), owner)
        assertEquals(ForumRights(canCreate = true, canManage = true, canDelete = false), admin)
        assertEquals(
            TopicActions(canEdit = true, canClose = true, canPin = true, canDelete = false),
            admin.actionsFor(forumTopic(7)),
        )
    }

    @Test
    fun `General can be renamed and hidden, never closed, pinned or deleted`() {
        val owner = forumInfo(MemberRole.Owner(isMember = true)).forumRights()

        assertEquals(
            TopicActions(canEdit = true, canHideGeneral = true),
            owner.actionsFor(forumTopic(1, isGeneral = true)),
        )
    }

    @Test
    fun `members create topics where the forum lets them, and manage only their own`() {
        val member = forumInfo(permissions = MemberPermissions(canCreateTopics = true)).forumRights()

        assertEquals(ForumRights(canCreate = true, canManage = false, canDelete = false), member)
        assertEquals(TopicActions(canEdit = true, canClose = true), member.actionsFor(forumTopic(7, isOutgoing = true)))
        assertNull(forumTopic(8).menuFor(member))
    }

    @Test
    fun `a restricted member may create topics only where both the forum and the restriction let them`() {
        val restricted =
            MemberRole.Restricted(
                isMember = true,
                untilDate = 0,
                permissions = MemberPermissions(canCreateTopics = false),
            )
        val rights = forumInfo(restricted, MemberPermissions(canCreateTopics = true)).forumRights()

        assertEquals(ForumRights(canCreate = false, canManage = false, canDelete = false), rights)
    }

    @Test
    fun `nobody who left or was removed may do anything`() {
        val left = forumInfo(MemberRole.Left, MemberPermissions(canCreateTopics = true)).forumRights()
        val banned = forumInfo(MemberRole.Banned(untilDate = 0)).forumRights()
        val ownerWhoLeft = forumInfo(MemberRole.Owner(isMember = false)).forumRights()

        assertEquals(ForumRights.None, left)
        assertEquals(ForumRights.None, banned)
        assertEquals(ForumRights.None, ownerWhoLeft)
    }
}
