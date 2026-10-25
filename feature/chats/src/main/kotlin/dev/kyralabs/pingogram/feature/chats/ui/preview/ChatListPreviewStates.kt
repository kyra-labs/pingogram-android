package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.chats.ui.ChatListUiState

/** Every state of the chat list for its previews, named in the preview pane. */
internal class ChatListPreviewStates : PreviewParameterProvider<ChatListUiState> {
    private val states =
        listOf(
            "Content with folders" to ChatListPreviewData.contentWithFolders,
            "Content" to ChatListPreviewData.content,
            "Archive row" to ChatListPreviewData.contentWithArchive,
            "Compact archive row" to ChatListPreviewData.contentWithCompactArchive,
            "Hidden archive row" to ChatListPreviewData.contentWithHiddenArchive,
            "Selecting" to ChatListPreviewData.selecting,
            "Connecting" to ChatListPreviewData.connecting,
            "Loading" to ChatListPreviewData.loading,
            "Empty" to ChatListPreviewData.empty,
            "Empty folder" to ChatListPreviewData.emptyFolder,
            "Error" to ChatListPreviewData.error,
            "Archive" to ChatListPreviewData.archived,
            "Archive empty" to ChatListPreviewData.archivedEmpty,
            "Delete private chat" to ChatListPreviewData.deletePrivateChatDialog,
            "Leave group" to ChatListPreviewData.leaveGroupDialog,
        )

    override val values: Sequence<ChatListUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
