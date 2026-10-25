package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.MemberRole

/**
 * What the current user may do with the topics of a forum (`REQUIREMENTS.md` FR-C9), as Telegram decides it
 * (td_api.tl `createForumTopic`, `editForumTopic`, `toggleForumTopicIsClosed`, `toggleForumTopicIsPinned`,
 * `deleteForumTopic`, `toggleGeneralForumTopicIsHidden`), so the screens offer only what Telegram would allow:
 *
 * - [canCreate]: new topics, with the administrator right to manage topics or the members' permission to create them
 *   (the forum's default permissions, and a restricted member's own).
 * - [canManage]: the administrator right to manage topics (`can_manage_topics`): rename, close and reopen any topic,
 *   pin, unpin and reorder them, hide and show General.
 * - [canDelete]: the administrator right to delete messages, which deleting a topic with all of its messages takes.
 *   Telegram also lets a topic's creator delete it while it holds no messages of others, which the app cannot tell, so
 *   it is not offered.
 * - [isMember]: in the forum, where the creator of a topic renames, closes and reopens it; none before the rights are
 *   known.
 */
@Immutable
data class ForumRights(
    val canCreate: Boolean,
    val canManage: Boolean,
    val canDelete: Boolean,
    val isMember: Boolean = true,
) {
    /** What the user may do with [topic]: their own topics also without the right to manage topics, while a member. */
    fun actionsFor(topic: ForumTopic): TopicActions {
        val own = isMember && topic.isOutgoing && !topic.isGeneral
        return TopicActions(
            // The General topic can be renamed, never closed for good, pinned or deleted: only hidden.
            canEdit = canManage || own,
            canClose = !topic.isGeneral && (canManage || own),
            canPin = !topic.isGeneral && canManage,
            canDelete = !topic.isGeneral && canDelete,
            canHideGeneral = topic.isGeneral && canManage,
        )
    }

    companion object {
        /** Nothing: not a member, or the rights are not known yet. */
        val None = ForumRights(canCreate = false, canManage = false, canDelete = false, isMember = false)
    }
}

/** The current user's [ForumRights] in this forum: none once they left it or were removed. */
internal fun GroupInfo.forumRights(): ForumRights {
    val role = myRole
    if (!role.isMember) return ForumRights.None
    val rights = myRights
    val mayCreate =
        when (role) {
            is MemberRole.Restricted -> role.permissions.canCreateTopics && defaultPermissions.canCreateTopics
            else -> defaultPermissions.canCreateTopics
        }
    return ForumRights(
        canCreate = rights.canManageTopics || mayCreate,
        canManage = rights.canManageTopics,
        canDelete = rights.canDeleteMessages,
    )
}

/** What a topic's menu offers (`docs/DESIGN_SYSTEM.md` §6 "Forum topics"); [isEmpty] when nothing. */
@Immutable
data class TopicActions(
    val canEdit: Boolean = false,
    val canClose: Boolean = false,
    val canPin: Boolean = false,
    val canDelete: Boolean = false,
    val canHideGeneral: Boolean = false,
) {
    val isEmpty: Boolean get() = !canEdit && !canClose && !canPin && !canDelete && !canHideGeneral

    companion object {
        val None = TopicActions()
    }
}

/**
 * A topic as its menu needs it: what it is ([isGeneral], [isClosed], [isPinned], [isHidden]) and what the user may do
 * with it ([actions]).
 */
@Immutable
data class TopicMenuState(
    val topicId: Int,
    val name: String,
    val isGeneral: Boolean,
    val isClosed: Boolean,
    val isPinned: Boolean,
    val isHidden: Boolean,
    val actions: TopicActions,
) {
    override fun toString() = "TopicMenuState(topicId=$topicId, actions=$actions, …)"
}

/** The menu of [this] topic, for [rights]; null where the user may do nothing with it. */
internal fun ForumTopic.menuFor(rights: ForumRights): TopicMenuState? {
    val actions = rights.actionsFor(this)
    if (actions.isEmpty) return null
    return TopicMenuState(id, name, isGeneral, isClosed, isPinned, isHidden, actions)
}
