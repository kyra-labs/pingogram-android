package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A group's or channel's own settings, which its administrators change on its settings page (`ChatSettingsRepository`,
 * `REQUIREMENTS.md` FR-P3).
 *
 * @property kind a basic group, a supergroup or a channel.
 * @property myRole the user's role in it, which says what they may change.
 * @property reactions which reactions members may add.
 * @property hasProtectedContent its messages cannot be forwarded or saved.
 * @property signMessages a channel's posts carry their author's name.
 * @property showMessageSender a channel's signed posts link to their author's profile.
 * @property isForum a supergroup's messages are in topics.
 * @property hasForumTabs a forum shows its topics as tabs rather than a list.
 * @property linkedChatId a channel's discussion group, or the channel a group discusses; 0 for none.
 */
data class ChatSettings(
    val kind: GroupKind,
    val myRole: MemberRole,
    val reactions: ChatReactions,
    val hasProtectedContent: Boolean,
    val signMessages: Boolean = false,
    val showMessageSender: Boolean = false,
    val isForum: Boolean = false,
    val hasForumTabs: Boolean = false,
    val linkedChatId: Long = 0,
)

/** The reactions a chat allows (td_api.tl `chatAvailableReactions`). */
sealed interface ChatReactions {
    /** At most [maxCount] reactions per message. */
    val maxCount: Int

    /** Every reaction. */
    data class All(
        override val maxCount: Int,
    ) : ChatReactions

    /** Only [reactions]; none when empty. */
    data class Some(
        val reactions: ImmutableList<ReactionType>,
        override val maxCount: Int,
    ) : ChatReactions
}
