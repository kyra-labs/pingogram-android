package dev.kyralabs.pingogram.feature.chats.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ArchiveEntry
import dev.kyralabs.pingogram.feature.chats.ui.ChatListContent
import dev.kyralabs.pingogram.feature.chats.ui.ChatListItemModel
import dev.kyralabs.pingogram.feature.chats.ui.ChatListKind
import dev.kyralabs.pingogram.feature.chats.ui.ChatListUiState
import dev.kyralabs.pingogram.feature.chats.ui.ChatPreview
import dev.kyralabs.pingogram.feature.chats.ui.ChatSelection
import dev.kyralabs.pingogram.feature.chats.ui.DeleteDialog
import dev.kyralabs.pingogram.feature.chats.ui.DeleteForEveryone
import dev.kyralabs.pingogram.feature.chats.ui.DeleteKind
import dev.kyralabs.pingogram.feature.chats.ui.FolderTab
import dev.kyralabs.pingogram.feature.chats.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chats.ui.TitleBadge
import dev.kyralabs.pingogram.feature.chats.ui.rowActions
import dev.kyralabs.pingogram.feature.chats.ui.selectionActions
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * Sample states of the chat list, shared by the previews and the screenshot tests: rows in every look the list has
 * (unread, muted, pinned, typing, draft, Saved Messages, badges, online, failed, read), with and without folders, with
 * the archive row, in selection mode, the archive, and the delete dialogs.
 */
internal object ChatListPreviewData {
    private fun row(
        id: Long,
        title: String,
        preview: ChatPreview,
        time: String,
        unreadCount: Int = 0,
        isMuted: Boolean = false,
        isPinned: Boolean = false,
        isOnline: Boolean = false,
        badge: TitleBadge? = null,
        outgoingStatus: OutgoingStatus? = null,
        isMarkedAsUnread: Boolean = false,
        canMute: Boolean = true,
    ) = ChatListItemModel(
        id = id,
        title = UiText.Plain(title),
        photo = null,
        placeholderIcon = null,
        isSavedMessages = false,
        isOnline = isOnline,
        badge = badge,
        isSecret = false,
        isMuted = isMuted,
        isPinned = isPinned,
        unreadCount = unreadCount,
        hasUnreadMention = false,
        hasUnreadReactions = false,
        isMarkedAsUnread = isMarkedAsUnread,
        time = time,
        outgoingStatus = outgoingStatus,
        preview = preview,
        actions = rowActions(unreadCount > 0 || isMarkedAsUnread, isArchived = false, isPinned, isMuted, canMute),
    )

    private fun message(
        text: String,
        sender: UiText? = null,
    ) = ChatPreview.Message(sender, UiText.Plain(text))

    private val you = UiText.resource(R.string.preview_sender_you)

    val items =
        persistentListOf(
            row(
                id = 1,
                title = "",
                preview = message("Boarding pass, gate B12"),
                time = "9:41 AM",
                isPinned = true,
                canMute = false,
            ).copy(
                title = UiText.resource(R.string.chat_saved_messages),
                placeholderIcon = R.drawable.ic_bookmark_filled,
            ),
            row(
                id = 2,
                title = "Ada Lovelace",
                preview = ChatPreview.Action(UiText.resource(R.string.action_typing)),
                time = "9:38 AM",
                unreadCount = 3,
                isPinned = true,
                isOnline = true,
            ),
            row(
                id = 3,
                title = "Analytical Engine Club",
                preview = message("The new punch cards arrived, come and see them tonight!", UiText.Plain("Charles")),
                time = "9:12 AM",
                unreadCount = 12,
            ).copy(hasUnreadMention = true),
            row(
                id = 4,
                title = "Grace Hopper",
                preview = ChatPreview.Draft(UiText.Plain("Found the moth in relay 70")),
                time = "8:55 AM",
            ),
            row(
                id = 5,
                title = "Orbital Mechanics Weekly",
                preview = message("Issue 42: transfer orbits explained"),
                time = "Mon",
                unreadCount = 1_204,
                isMuted = true,
                badge = TitleBadge.Verified,
            ),
            row(
                id = 6,
                title = "Katherine Johnson",
                preview = message("Trajectory checks out. Go for launch.", you),
                time = "Sun",
                outgoingStatus = OutgoingStatus.Read,
            ).copy(hasUnreadReactions = true),
            row(
                id = 7,
                title = "Alan Turing",
                preview = message("Can machines think?", you),
                time = "Sat",
                outgoingStatus = OutgoingStatus.Failed,
            ).copy(isSecret = true),
            row(
                id = 8,
                title = "Free Crypto Giveaway",
                preview = message("Send 1 coin, get 2 back!"),
                time = "Mar 17",
                badge = TitleBadge.Scam,
            ),
            row(
                id = 9,
                title = "Book club",
                preview = message("Next up: Hidden Figures", UiText.Plain("Mary")),
                time = "Mar 2",
                isMarkedAsUnread = true,
            ),
            row(
                id = 10,
                title = "",
                preview = message("Thanks!"),
                time = "3/17/25",
            ).copy(
                title = UiText.resource(R.string.user_deleted_account),
                placeholderIcon = R.drawable.ic_person_filled,
            ),
        )

    val folders =
        persistentListOf(
            FolderTab.AllChats(unreadCount = 4),
            FolderTab.Folder(id = 1, title = "Work", unreadCount = 2),
            FolderTab.Folder(id = 2, title = "Family", unreadCount = 0),
            FolderTab.Folder(id = 3, title = "Channels", unreadCount = 1),
        )

    val contentWithFolders = ChatListUiState(content = ChatListContent.Content(items), folderTabs = folders)

    val content = ChatListUiState(content = ChatListContent.Content(items))

    val connecting = content.copy(connection = ConnectionStatus.Connecting)

    val loading = ChatListUiState()

    val empty = ChatListUiState(content = ChatListContent.Empty(isFolder = false))

    val emptyFolder =
        ChatListUiState(content = ChatListContent.Empty(isFolder = true), folderTabs = folders, selectedFolderId = 2)

    val error = ChatListUiState(content = ChatListContent.Error(DomainError.Network))

    val archive =
        ArchiveEntry(
            chatCount = 14,
            titles = persistentListOf("Rocket Club", "Old Team", "Newsletter"),
            unreadCount = 2,
        )

    val contentWithArchive = contentWithFolders.copy(archive = archive)

    /**
     * Three chats selected: Ada (pinned), the club (unread) and Katherine. Not all are pinned, muted or read, so the
     * toolbar offers pin, mute and mark as read.
     */
    val selecting: ChatListUiState =
        items.filter { it.id == 2L || it.id == 3L || it.id == 6L }.let { selected ->
            contentWithFolders.copy(
                selection = ChatSelection(selected.map { it.id }.toImmutableSet(), selectionActions(selected)),
            )
        }

    private val archivedItems =
        items.drop(ARCHIVED_FROM).map { item ->
            item.copy(
                isPinned = false,
                actions = rowActions(item.isUnread, isArchived = true, isPinned = false, item.isMuted, canMute = true),
            )
        }

    val archived =
        ChatListUiState(kind = ChatListKind.Archive, content = ChatListContent.Content(archivedItems.toImmutableList()))

    val archivedEmpty = ChatListUiState(kind = ChatListKind.Archive, content = ChatListContent.Empty(isFolder = false))

    val deletePrivateChat =
        DeleteDialog.Single(4, UiText.Plain("Grace Hopper"), DeleteKind.DeleteChat, DeleteForEveryone.OtherUser)

    val leaveGroup = DeleteDialog.Single(3, UiText.Plain("Analytical Engine Club"), DeleteKind.LeaveGroup, null)

    val deleteChats = DeleteDialog.Bulk(persistentListOf(2, 3, 4))

    val deletePrivateChatDialog = content.copy(deleteDialog = deletePrivateChat)

    val leaveGroupDialog = content.copy(deleteDialog = leaveGroup)

    /** The archive shows the rows from Orbital Mechanics Weekly on. */
    private const val ARCHIVED_FROM = 4
}
