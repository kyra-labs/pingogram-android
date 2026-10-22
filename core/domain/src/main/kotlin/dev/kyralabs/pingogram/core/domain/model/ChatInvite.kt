package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What an invite link (`t.me/+…`, `t.me/joinchat/…`) leads to, as TDLib's `checkChatInviteLink` describes it before
 * joining (`REQUIREMENTS.md` FR-P5): the join sheet shows it and asks. Plain data from `core:data`; `toString()` leaves
 * the chat's texts out, so it can be logged.
 *
 * @property chatId the chat, where the user can open it already: as a member ([isMember]), or for a while before
 *   joining ([previewSeconds]); 0 where they cannot.
 * @property previewSeconds for how long the chat stays readable without joining; 0 for none (td_api.tl
 *   `accessible_for`).
 * @property memberUserIds a few members TDLib knows (contacts first), for their avatars; may be empty.
 * @property createsJoinRequest joining sends a request that an administrator accepts, instead of joining at once.
 * @property isPublic the chat has a username, so anyone can find it.
 * @property needsSubscription joining costs a paid subscription (Telegram Stars), which Pingogram does not offer.
 */
data class ChatInvite(
    val chatId: Long,
    val kind: GroupKind,
    val title: String,
    val photo: ProfilePhoto?,
    val description: String,
    val memberCount: Int,
    val memberUserIds: ImmutableList<Long> = persistentListOf(),
    val previewSeconds: Int = 0,
    val createsJoinRequest: Boolean = false,
    val isPublic: Boolean = false,
    val isVerified: Boolean = false,
    val isScam: Boolean = false,
    val isFake: Boolean = false,
    val needsSubscription: Boolean = false,
) {
    /** The user is in the chat already: TDLib knows it, and it is no preview. */
    val isMember: Boolean get() = chatId != 0L && previewSeconds == 0

    override fun toString() =
        "ChatInvite(chatId=$chatId, kind=$kind, memberCount=$memberCount, createsJoinRequest=$createsJoinRequest, …)"
}

/** What came of joining a chat (`ChatJoinRepository`). */
sealed interface JoinResult {
    /** The user is a member of chat [chatId] now. */
    data class Joined(
        val chatId: Long,
    ) : JoinResult

    /** An administrator has to accept the request first; Telegram tells the user once they do. */
    data object RequestSent : JoinResult

    /**
     * The chat checks new members through a bot's mini app first, which Pingogram does not show (td_api.tl
     * `chatJoinResultGuardBotApprovalRequired`).
     */
    data object NeedsBotApproval : JoinResult

    /** Telegram refused the request (td_api.tl `chatJoinResultDeclined`). */
    data object Declined : JoinResult
}
