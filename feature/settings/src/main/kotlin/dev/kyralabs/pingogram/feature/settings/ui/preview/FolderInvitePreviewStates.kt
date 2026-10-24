package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteChat
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteUiState
import kotlinx.collections.immutable.persistentListOf

/** The states of the shared folder sheet for previews and screenshot tests. */
internal object FolderInvitePreviewStates {
    private val design = FolderInviteChat(chatId = 11, title = "Design team", photo = null, channel = false)
    private val news = FolderInviteChat(chatId = 12, title = "Pingogram News", photo = null, channel = true)
    private val android = FolderInviteChat(chatId = 13, title = "Android devs", photo = null, channel = false)

    val newFolder =
        FolderInviteUiState.Content(
            title = "Work",
            iconName = "Custom",
            isAdded = false,
            newChats = persistentListOf(design, news.copy(picked = false)),
            joinedChats = persistentListOf(android),
        )

    val newChats =
        FolderInviteUiState.Content(
            title = "Work",
            iconName = "Custom",
            isAdded = true,
            newChats = persistentListOf(news),
            joinedChats = persistentListOf(design, android),
        )

    val added =
        FolderInviteUiState.Content(
            title = "Work",
            iconName = "Custom",
            isAdded = true,
            joinedChats = persistentListOf(design, news, android),
        )

    val adding = newFolder.copy(isAdding = true)

    val addFailed = newFolder.copy(addError = DomainError.TooManyChatFolders)

    val invalid = FolderInviteUiState.Failed(DomainError.InviteLinkInvalid, canRetry = false)

    val offline = FolderInviteUiState.Failed(DomainError.Network, canRetry = true)
}
