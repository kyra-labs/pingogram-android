package dev.kyralabs.pingogram.feature.profile.invite

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the join sheet of an invite link shows (`REQUIREMENTS.md` FR-P5). */
@Immutable
sealed interface ChatInviteUiState {
    /** Telegram is asked what the link leads to. */
    data object Loading : ChatInviteUiState

    /**
     * The link could not be checked: [error], an invalid link ([canRetry] false, as trying again changes nothing) or no
     * network ([canRetry] true).
     */
    data class Failed(
        val error: DomainError,
        val canRetry: Boolean,
    ) : ChatInviteUiState

    /**
     * The chat the link leads to, and the way in.
     *
     * @property isJoining "Join" was tapped and Telegram has not answered yet.
     * @property joinError why joining failed (a full group, too many chats, a flood wait); null while it did not.
     * @property notice what came of joining when it did not let the user in, other than an error.
     */
    data class Content(
        val invite: InviteCard,
        val isJoining: Boolean = false,
        val joinError: DomainError? = null,
        val notice: JoinNotice? = null,
    ) : ChatInviteUiState {
        /** Whether "Join" shows: not for a paid chat, and not while it joins. */
        val canJoin: Boolean get() = !invite.needsSubscription && !isJoining
    }

    /** A request to join was sent; an administrator accepts it, and Telegram tells the user. */
    data class RequestSent(
        val kind: GroupKind,
    ) : ChatInviteUiState
}

/**
 * The chat of an invitation, as the sheet shows it.
 *
 * @property avatarId what the avatar placeholder's colors come from: the chat, or the link where TDLib names no chat.
 * @property members a few members' avatars and names, contacts first, as TDLib names them.
 */
@Immutable
data class InviteCard(
    val title: String,
    val kind: GroupKind,
    val isPublic: Boolean,
    val memberCount: Int,
    val description: String,
    val photo: ProfilePhoto?,
    val avatarId: Long,
    val members: ImmutableList<InviteMember> = persistentListOf(),
    val isVerified: Boolean = false,
    val isScam: Boolean = false,
    val isFake: Boolean = false,
    val createsJoinRequest: Boolean = false,
    val needsSubscription: Boolean = false,
) {
    override fun toString() = "InviteCard(kind=$kind, memberCount=$memberCount, members=${members.size}, …)"
}

/** A member of the chat, shown as an avatar. */
@Immutable
data class InviteMember(
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "InviteMember(id=$id, …)"
}

/** What came of "Join" without joining or an error. */
enum class JoinNotice {
    /** The chat checks new members with a bot's mini app first, which Pingogram does not show. */
    NeedsBotApproval,

    /** Telegram refused to let the user in. */
    Declined,
}

/** What the user does on the join sheet, handled by [ChatInviteViewModel.onEvent]. */
sealed interface ChatInviteEvent {
    data object Retry : ChatInviteEvent

    /** "Join" (or "Request to join"). */
    data object Join : ChatInviteEvent
}

/** One-off side effects of [ChatInviteViewModel], handled once by its route. */
sealed interface ChatInviteEffect {
    /** The user is in chat [chatId] (now, or already): the sheet closes and the chat opens. */
    data class OpenChat(
        val chatId: Long,
    ) : ChatInviteEffect
}
