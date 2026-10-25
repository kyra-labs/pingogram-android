package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import kotlinx.collections.immutable.persistentListOf

/** The Locked chats page's states, for previews and goldens. */
internal object LockedChatsPreviewStates {
    val content =
        LockedChatsUiState.Content(
            persistentListOf(
                LockedChatRow(id = 5, title = "Ada Lovelace", photo = null, unreadCount = 3),
                LockedChatRow(id = -100, title = "Analytical Engine team", photo = null, unreadCount = 0),
            ),
        )

    val empty = LockedChatsUiState.Content(persistentListOf())

    val picker =
        LockedChatsPickerUiState(
            query = "",
            candidates =
                persistentListOf(
                    LockedChatCandidate(id = 6, title = "Grace Hopper", photo = null, selected = true),
                    LockedChatCandidate(id = 7, title = "Alan Turing", photo = null, selected = false),
                ),
        )
}
