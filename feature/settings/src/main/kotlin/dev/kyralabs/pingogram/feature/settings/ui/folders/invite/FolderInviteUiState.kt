package dev.kyralabs.pingogram.feature.settings.ui.folders.invite

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the sheet of a shared folder's link (`t.me/addlist/…`) shows (`REQUIREMENTS.md` FR-L3). */
@Immutable
sealed interface FolderInviteUiState {
    /** Telegram is asked what the link holds. */
    data object Loading : FolderInviteUiState

    /** The link could not be checked: [error]; [canRetry] where trying again may help (no network, a flood wait). */
    data class Failed(
        val error: DomainError,
        val canRetry: Boolean,
    ) : FolderInviteUiState

    /**
     * The folder the link shares.
     *
     * @property isAdded the user has this folder already; then [newChats] are the chats added to it since.
     * @property newChats the chats the user is not in yet, which they pick to join; all picked at first.
     * @property joinedChats the folder's chats the user is in already.
     * @property isAdding "Add" was tapped and Telegram has not answered yet.
     * @property addError why adding failed; null while it did not.
     */
    data class Content(
        val title: String,
        val iconName: String?,
        val isAdded: Boolean,
        val newChats: ImmutableList<FolderInviteChat> = persistentListOf(),
        val joinedChats: ImmutableList<FolderInviteChat> = persistentListOf(),
        val isAdding: Boolean = false,
        val addError: DomainError? = null,
    ) : FolderInviteUiState {
        /** Whether "Add" may be tapped: at least one chat picked (or a new folder with nothing left to join). */
        val canAdd: Boolean get() = !isAdding && (newChats.any { it.picked } || (!isAdded && newChats.isEmpty()))

        /** Whether there is anything to add: a folder the user lacks, or chats added to it since. */
        val hasSomethingToAdd: Boolean get() = !isAdded || newChats.isNotEmpty()
    }
}

/** A chat of the shared folder: its avatar and [title] (user content), whether it is a [channel], [picked] to join. */
@Immutable
data class FolderInviteChat(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val channel: Boolean,
    val picked: Boolean = true,
) {
    override fun toString() = "FolderInviteChat(chatId=$chatId, channel=$channel, picked=$picked, …)"
}

/** What the user does on the sheet, handled by [FolderInviteViewModel.onEvent]. */
sealed interface FolderInviteEvent {
    data object Retry : FolderInviteEvent

    /** Chat [chatId] was picked or left out. */
    data class TogglePicked(
        val chatId: Long,
    ) : FolderInviteEvent

    /** "Add folder" or "Join chats". */
    data object Add : FolderInviteEvent
}

/** What the sheet does once. */
sealed interface FolderInviteEffect {
    /** The folder was added or its chats joined: the sheet closes. */
    data object Added : FolderInviteEffect
}
