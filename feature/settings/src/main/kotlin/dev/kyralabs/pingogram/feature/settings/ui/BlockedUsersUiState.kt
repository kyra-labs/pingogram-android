package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** What the Blocked users page shows (`REQUIREMENTS.md` FR-S1). */
@Immutable
sealed interface BlockedUsersUiState {
    data object Loading : BlockedUsersUiState

    /** Nobody is blocked. */
    data object Empty : BlockedUsersUiState

    /** The first page could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : BlockedUsersUiState

    /**
     * The blocked users and chats loaded so far.
     *
     * @property totalCount how many are blocked in all.
     * @property more whether more can load, are loading, or failed to.
     */
    data class Content(
        val items: ImmutableList<BlockedSenderItem>,
        val totalCount: Int,
        val more: MoreBlockedSenders,
    ) : BlockedUsersUiState
}

/**
 * A blocked user or chat as its row shows it.
 *
 * @property name the user's full name ("Deleted Account" for a deleted one), or the chat's title.
 * @property detail the username (`@ada`); null without one.
 * @property phoneNumber a user's phone number, `+` and its digits, when it has no username; the row formats it.
 * @property avatarId the user's or chat's id, which picks the avatar's placeholder.
 * @property placeholderIcon the avatar placeholder's icon in place of initials, for a deleted account; null otherwise.
 * @property isUnblocking unblocking was asked for, and TDLib has not answered yet.
 */
@Immutable
data class BlockedSenderItem(
    val sender: MessageSender,
    val name: UiText,
    val detail: UiText?,
    val photo: ProfilePhoto?,
    val avatarId: Long,
    @param:DrawableRes val placeholderIcon: Int? = null,
    val isUnblocking: Boolean = false,
    val phoneNumber: String? = null,
) {
    /** The row's key in the list: users and chats have ids of their own kinds. */
    val key: String
        get() =
            when (sender) {
                is MessageSender.User -> "user:${sender.userId}"
                is MessageSender.Chat -> "chat:${sender.chatId}"
            }
}

/** The end of the list of blocked users and chats. */
@Immutable
sealed interface MoreBlockedSenders {
    /** Everything is loaded. */
    data object None : MoreBlockedSenders

    /** More can load, which the list asks for as its end comes near ([BlockedUsersEvent.LoadMore]). */
    data object Available : MoreBlockedSenders

    data object Loading : MoreBlockedSenders

    /** The next page could not be loaded, because of [error]; [BlockedUsersEvent.LoadMore] tries again. */
    data class Failed(
        val error: DomainError,
    ) : MoreBlockedSenders
}

/** What the user does on the Blocked users page, handled by [BlockedUsersViewModel.onEvent]. */
sealed interface BlockedUsersEvent {
    /** Load the first page again after it failed. */
    data object Retry : BlockedUsersEvent

    /** The end of the list is near, or loading the next page failed and the user tries again. */
    data object LoadMore : BlockedUsersEvent

    data class Unblock(
        val sender: MessageSender,
    ) : BlockedUsersEvent

    /** Block [sender] again, right after unblocking it, from the snackbar's "Undo". */
    data class Undo(
        val sender: MessageSender,
    ) : BlockedUsersEvent
}

/** One-off side effects of [BlockedUsersViewModel], handled once by its route. */
sealed interface BlockedUsersEffect {
    /** [sender], named [name], was unblocked: a snackbar says so and offers to undo it. */
    data class Unblocked(
        val sender: MessageSender,
        val name: UiText,
    ) : BlockedUsersEffect

    /** Unblocking or blocking again failed, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : BlockedUsersEffect
}
