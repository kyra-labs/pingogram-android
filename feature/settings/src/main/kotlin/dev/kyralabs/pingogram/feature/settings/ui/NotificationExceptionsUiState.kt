package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * What the exceptions page of a kind of chats shows (`REQUIREMENTS.md` FR-S1).
 *
 * @property scope the kind of chats, which names the page.
 * @property exceptions the chats with settings of their own.
 * @property muteOptions the chat whose mute options are open, in a sheet; null while none is.
 */
@Immutable
data class NotificationExceptionsUiState(
    val scope: NotificationScope,
    val exceptions: ExceptionsState = ExceptionsState.Loading,
    val muteOptions: NotificationExceptionItem? = null,
)

/** The chats of an exceptions page. */
@Immutable
sealed interface ExceptionsState {
    data object Loading : ExceptionsState

    /** No chat of the scope has settings of its own. */
    data object Empty : ExceptionsState

    /** The list could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : ExceptionsState

    data class Content(
        val items: ImmutableList<NotificationExceptionItem>,
    ) : ExceptionsState
}

/**
 * A chat with settings of its own, as its row shows it.
 *
 * @property title the chat's title; for private and secret chats the other user's name.
 * @property status whether it notifies: "Muted", "Muted until 6:30 PM", "Notifications on", "Uses the default".
 * @property mute its mute, which its options start from.
 * @property isChanging a change was asked for, and TDLib has not answered yet.
 */
@Immutable
data class NotificationExceptionItem(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val status: UiText,
    val mute: ChatMute,
    val isChanging: Boolean = false,
) {
    override fun toString() = "NotificationExceptionItem(chatId=$chatId, mute=$mute, isChanging=$isChanging, …)"
}

/** What the user does on an exceptions page, handled by [NotificationExceptionsViewModel.onEvent]. */
sealed interface NotificationExceptionsEvent {
    /** Load the list again after it failed. */
    data object Retry : NotificationExceptionsEvent

    /** A row was tapped: show the mute options of chat [chatId]. */
    data class OpenMuteOptions(
        val chatId: Long,
    ) : NotificationExceptionsEvent

    data object DismissMuteOptions : NotificationExceptionsEvent

    /** An option of the sheet, or a row's remove button ([MuteChange.UseDefault]), for chat [chatId]. */
    data class Change(
        val chatId: Long,
        val change: MuteChange,
    ) : NotificationExceptionsEvent
}

/** One-off side effects of [NotificationExceptionsViewModel], handled once by its route. */
sealed interface NotificationExceptionsEffect {
    /** A change could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : NotificationExceptionsEffect
}
