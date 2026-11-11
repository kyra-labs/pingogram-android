package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList

/** What a group's welcome message page shows (`docs/DESIGN_SYSTEM.md` §6 "Welcome message setup"). */
@Immutable
sealed interface WelcomeMessagesUiState {
    data object Loading : WelcomeMessagesUiState

    data class Error(
        val error: DomainError,
    ) : WelcomeMessagesUiState

    /**
     * @property messages the welcome messages, in Telegram's order.
     * @property countMax how many the group may have.
     * @property isBusy a change of the whole list (turning it off) is on its way to Telegram.
     * @property deleting the messages being deleted.
     * @property dialog the question shown over the page.
     * @property editor the sheet adding or editing a message, while it is open.
     */
    data class Content(
        val messages: ImmutableList<WelcomeItem>,
        val countMax: Int,
        val isBusy: Boolean = false,
        val deleting: Set<Int> = emptySet(),
        val dialog: WelcomeDialog? = null,
        val editor: WelcomeEditor? = null,
    ) : WelcomeMessagesUiState {
        /** Whether new members are welcomed: the group has a welcome message. */
        val isOn: Boolean get() = messages.isNotEmpty()

        val canAdd: Boolean get() = messages.size < countMax
    }
}

/** A welcome message as its card shows it: [text] (a photo's caption), its [photo], or neither when unsupported. */
@Immutable
data class WelcomeItem(
    val id: Int,
    val text: FormattedText,
    val photo: Photo?,
    val isSupported: Boolean = true,
) {
    @get:StringRes
    val kind: Int
        get() =
            when {
                !isSupported -> R.string.welcome_kind_other
                photo == null -> R.string.welcome_kind_text
                text.text.isBlank() -> R.string.welcome_kind_photo
                else -> R.string.welcome_kind_photo_caption
            }
}

/** The destructive questions of the page: deleting one message, or all of them by turning welcoming off. */
@Immutable
sealed interface WelcomeDialog {
    data class Delete(
        val id: Int,
        val position: Int,
    ) : WelcomeDialog

    data object TurnOff : WelcomeDialog
}

/**
 * The sheet adding a welcome message ([messageId] null) or editing one: its [text] and [photo] as typed and picked,
 * [isFormatted] when the message's text has formatting, which a change of the text drops, [isSaving] while Telegram
 * answers, and [error] why Telegram refused it.
 */
@Immutable
data class WelcomeEditor(
    val messageId: Int?,
    val text: String,
    val photo: EditorPhoto?,
    val isFormatted: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    val canSave: Boolean get() = !isSaving && photo !is EditorPhoto.Preparing && (photo != null || text.isNotBlank())
}

/** The editor's photo: being copied, a picked copy at [EditorPhoto.Picked.path], or the message's own, kept. */
@Immutable
sealed interface EditorPhoto {
    data object Preparing : EditorPhoto

    data class Picked(
        val path: String,
    ) : EditorPhoto

    data class Kept(
        val photo: Photo,
    ) : EditorPhoto
}

sealed interface WelcomeMessagesEvent {
    data object Retry : WelcomeMessagesEvent

    /** The main switch: on opens the editor for a first message, off asks before deleting them all. */
    data class Toggle(
        val on: Boolean,
    ) : WelcomeMessagesEvent

    /** "Add message". */
    data object Add : WelcomeMessagesEvent

    data class Edit(
        val id: Int,
    ) : WelcomeMessagesEvent

    /** Deleting message [id]: asks first. */
    data class Delete(
        val id: Int,
    ) : WelcomeMessagesEvent

    data object ConfirmDialog : WelcomeMessagesEvent

    data object DismissDialog : WelcomeMessagesEvent

    data class TextChanged(
        val text: String,
    ) : WelcomeMessagesEvent

    /** "Add photo" / "Change photo": the photo picker opens. */
    data object PickPhoto : WelcomeMessagesEvent

    /** The photo picker's answer, a content URI. */
    data class PhotoPicked(
        val uri: String,
    ) : WelcomeMessagesEvent

    data object RemovePhoto : WelcomeMessagesEvent

    data object Save : WelcomeMessagesEvent

    /** "Cancel", the scrim, a drag or back: the editor closes without saving. */
    data object CloseEditor : WelcomeMessagesEvent
}

sealed interface WelcomeMessagesEffect {
    /** The photo picker opens, for images only. */
    data object PickPhoto : WelcomeMessagesEffect

    data class Notice(
        @param:StringRes val text: Int,
    ) : WelcomeMessagesEffect

    data class ShowError(
        val error: DomainError,
    ) : WelcomeMessagesEffect
}
