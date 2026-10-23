package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFoldersUiState
import dev.kyralabs.pingogram.feature.settings.ui.folders.FolderItem
import dev.kyralabs.pingogram.feature.settings.ui.folders.RecommendedFolderItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the Chat folders page for previews and screenshot tests. */
internal object ChatFoldersPreviewStates {
    private val recommended =
        persistentListOf(
            RecommendedFolderItem(
                key = 0,
                title = "Unread",
                iconName = "Unread",
                description = "New messages from all chats",
            ),
            RecommendedFolderItem(
                key = 1,
                title = "Personal",
                iconName = "Private",
                description = "Only messages from personal chats",
            ),
        )

    val content =
        ChatFoldersUiState.Content(
            folders =
                persistentListOf(
                    FolderItem(
                        id = 2,
                        title = "Work",
                        iconName = "Custom",
                        includedTypes = persistentSetOf(),
                        chatCount = 6,
                        isShared = false,
                    ),
                    FolderItem(
                        id = 3,
                        title = "Family",
                        iconName = "Favorite",
                        includedTypes = persistentSetOf(),
                        chatCount = 3,
                        isShared = true,
                    ),
                    FolderItem(
                        id = 4,
                        title = "News",
                        iconName = "Channels",
                        includedTypes = persistentSetOf(ChatFolderIncludedType.Channels, ChatFolderIncludedType.Bots),
                        chatCount = 0,
                        isShared = false,
                    ),
                ),
            recommended = recommended,
        )

    val noFolders = ChatFoldersUiState.Content(folders = persistentListOf(), recommended = recommended)

    val adding = content.copy(recommended = persistentListOf(recommended[0].copy(isAdding = true), recommended[1]))
}
