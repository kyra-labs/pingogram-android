package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the folder editor shows (`REQUIREMENTS.md` FR-L3). */
@Immutable
sealed interface ChatFolderEditUiState {
    data object Loading : ChatFolderEditUiState

    /** The folder could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : ChatFolderEditUiState

    /**
     * The folder as the user edits it: its [name] (at most `ChatFolderSettings.MAX_TITLE_LENGTH` characters), its icon
     * ([iconName], TDLib's name; null lets Telegram choose), the kinds of chats and the chats it includes and leaves
     * out. [isNew] for a folder that does not exist yet. [nameMissing] and [nothingIncluded] say why "Save" was
     * refused; [isSaving] and [isDeleting] while Telegram is asked, [confirmingDelete] while the delete question shows.
     */
    data class Content(
        val isNew: Boolean,
        val name: String = "",
        val iconName: String? = null,
        val includedTypes: ImmutableSet<ChatFolderIncludedType> = persistentSetOf(),
        val excludedTypes: ImmutableSet<ChatFolderExcludedType> = persistentSetOf(),
        val includedChats: ImmutableList<FolderChatItem> = persistentListOf(),
        val excludedChats: ImmutableList<FolderChatItem> = persistentListOf(),
        val nameMissing: Boolean = false,
        val nothingIncluded: Boolean = false,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        val confirmingDelete: Boolean = false,
    ) : ChatFolderEditUiState {
        val isBusy: Boolean get() = isSaving || isDeleting
    }
}

/** A chat of a folder or of the picker: its avatar, its [title] (user content), and whether the picker picked it. */
@Immutable
data class FolderChatItem(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val selected: Boolean = false,
) {
    override fun toString() = "FolderChatItem(chatId=$chatId, selected=$selected, …)"
}

/** Which side of the folder the picker adds to. */
enum class FolderPickerSide { Included, Excluded }

/**
 * The picker sheet while it shows, kept out of [ChatFolderEditUiState] as the conversation's sheets are: its [side],
 * the [query] typed into its search, the kinds of chats on that side ([includedTypes] or [excludedTypes]), and the
 * chats that match the query, those on that side [FolderChatItem.selected].
 */
@Immutable
data class FolderPickerUiState(
    val side: FolderPickerSide,
    val query: String = "",
    val includedTypes: ImmutableSet<ChatFolderIncludedType> = persistentSetOf(),
    val excludedTypes: ImmutableSet<ChatFolderExcludedType> = persistentSetOf(),
    val chats: ImmutableList<FolderChatItem> = persistentListOf(),
)

/** What the user does in the folder editor, handled by [ChatFolderEditViewModel.onEvent]. */
sealed interface ChatFolderEditEvent {
    data object Retry : ChatFolderEditEvent

    data class NameChanged(
        val name: String,
    ) : ChatFolderEditEvent

    /** An icon was picked: TDLib's name of it, or null for "Automatic". */
    data class SelectIcon(
        val iconName: String?,
    ) : ChatFolderEditEvent

    data class ToggleIncludedType(
        val type: ChatFolderIncludedType,
    ) : ChatFolderEditEvent

    data class ToggleExcludedType(
        val type: ChatFolderExcludedType,
    ) : ChatFolderEditEvent

    /** The chat [chatId] was taken out of the folder's [side]. */
    data class RemoveChat(
        val side: FolderPickerSide,
        val chatId: Long,
    ) : ChatFolderEditEvent

    data class OpenPicker(
        val side: FolderPickerSide,
    ) : ChatFolderEditEvent

    data class PickerQueryChanged(
        val query: String,
    ) : ChatFolderEditEvent

    /** A chat of the picker was tapped: it joins its side of the folder, or leaves it. */
    data class TogglePickedChat(
        val chat: FolderChatItem,
    ) : ChatFolderEditEvent

    data object ClosePicker : ChatFolderEditEvent

    data object Save : ChatFolderEditEvent

    data object RequestDelete : ChatFolderEditEvent

    data object DismissDelete : ChatFolderEditEvent

    data object ConfirmDelete : ChatFolderEditEvent
}

/** What the folder editor does once. */
sealed interface ChatFolderEditEffect {
    /** The folder was saved or deleted: the editor closes. */
    data object Done : ChatFolderEditEffect

    /** "Save" was refused before Telegram was asked, or Telegram refused it ([ShowError]): a `Reject` haptic. */
    data object Rejected : ChatFolderEditEffect

    data class ShowError(
        val error: DomainError,
    ) : ChatFolderEditEffect
}
