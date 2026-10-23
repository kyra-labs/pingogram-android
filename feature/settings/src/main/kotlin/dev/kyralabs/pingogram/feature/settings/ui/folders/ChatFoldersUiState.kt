package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf

/** What the Chat folders page shows (`REQUIREMENTS.md` FR-L3). */
@Immutable
sealed interface ChatFoldersUiState {
    data object Loading : ChatFoldersUiState

    /** The folders could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : ChatFoldersUiState

    /**
     * The user's [folders] in their order, and the folders Telegram suggests ([recommended]; empty while they load or
     * if none fits).
     */
    data class Content(
        val folders: ImmutableList<FolderItem>,
        val recommended: ImmutableList<RecommendedFolderItem> = persistentListOf(),
    ) : ChatFoldersUiState
}

/**
 * A folder of the list: its [title] (plain text), TDLib's [iconName], and what it includes, for its summary: the
 * kinds of chats ([includedTypes]) and how many chats were chosen ([chatCount]); [isShared] once it has invite links.
 */
@Immutable
data class FolderItem(
    val id: Int,
    val title: String,
    val iconName: String?,
    val includedTypes: ImmutableSet<ChatFolderIncludedType>,
    val chatCount: Int,
    val isShared: Boolean,
)

/** A folder Telegram suggests, by its place in the suggestions ([key]); [isAdding] while it is being created. */
@Immutable
data class RecommendedFolderItem(
    val key: Int,
    val title: String,
    val iconName: String?,
    val description: String,
    val isAdding: Boolean = false,
)

/** What the user does on the Chat folders page, handled by [ChatFoldersViewModel.onEvent]. */
sealed interface ChatFoldersEvent {
    /** Load the folders again after they failed. */
    data object Retry : ChatFoldersEvent

    /** The folders were dragged into the order of [folderIds]. */
    data class Reorder(
        val folderIds: List<Int>,
    ) : ChatFoldersEvent

    /** Move folder [id] one place up (towards the first tab) or down: screen readers' "Move up" and "Move down". */
    data class Move(
        val id: Int,
        val up: Boolean,
    ) : ChatFoldersEvent

    /** Create the suggested folder [key]. */
    data class AddRecommended(
        val key: Int,
    ) : ChatFoldersEvent
}

/** What the Chat folders page does once. */
sealed interface ChatFoldersEffect {
    /** A failure the page shows in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : ChatFoldersEffect
}
