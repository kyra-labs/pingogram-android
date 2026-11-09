package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How the chat list looks and acts (Appearance → Chat list), kept on this device: whether the story rings show above
 * the chats ([ChatListSettings.showStories], on by default; hiding them loads none), what swiping a row does
 * toward each side ([ChatListSettings.startSwipe], [ChatListSettings.endSwipe]), and whether swiping the list changes
 * the folder instead ([ChatListSettings.swipeChangesFolder], off by default), whether the folder tabs end in an
 * "Edit folders" button ([ChatListSettings.showEditFoldersButton], on by default), whether the rows show the kind of
 * chat before its name ([ChatListSettings.showChatTypeIcons], on by default), and how the folder tabs show their
 * folders ([ChatListSettings.folderTabStyle], their names by default).
 */
interface ChatListSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<ChatListSettings>

    /** Saves whether the rings show; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setShowStories(show: Boolean): Outcome<Unit>

    /** Saves the two swipes' actions; fails as [setShowStories] does. */
    suspend fun setSwipeActions(
        start: SwipeAction,
        end: SwipeAction,
    ): Outcome<Unit>

    /** Saves whether swiping the list changes the folder; fails as [setShowStories] does. */
    suspend fun setSwipeChangesFolder(changes: Boolean): Outcome<Unit>

    /** Saves whether the folder tabs end in "Edit folders"; fails as [setShowStories] does. */
    suspend fun setShowEditFoldersButton(show: Boolean): Outcome<Unit>

    /** Saves whether a chat's kind shows before its name; fails as [setShowStories] does. */
    suspend fun setShowChatTypeIcons(show: Boolean): Outcome<Unit>

    /** Saves how the folder tabs show their folders; fails as [setShowStories] does. */
    suspend fun setFolderTabStyle(style: FolderTabStyle): Outcome<Unit>
}

/**
 * The chat list's own settings: whether the story rings show above the chats, and what a swipe toward the end
 * ([startSwipe], from the start edge) and toward the start ([endSwipe]) does; both mirror in right-to-left layouts.
 * With [swipeChangesFolder] a horizontal swipe moves the whole list to the folder tab beside the selected one instead,
 * and rows do not swipe; [startSwipe] and [endSwipe] are kept for when it is turned off. While
 * [showEditFoldersButton] is off, the folder row ends at the tabs; their menus keep "Edit folders". While
 * [showChatTypeIcons] is off, the rows show no group or channel symbol before the chat's name. [folderTabStyle] is what
 * each folder tab shows: its name, its icon and name, or its icon alone.
 */
data class ChatListSettings(
    val showStories: Boolean = true,
    val startSwipe: SwipeAction = SwipeAction.Read,
    val endSwipe: SwipeAction = SwipeAction.Archive,
    val swipeChangesFolder: Boolean = false,
    val showEditFoldersButton: Boolean = true,
    val showChatTypeIcons: Boolean = true,
    val folderTabStyle: FolderTabStyle = FolderTabStyle.Text,
)

/**
 * What a folder tab shows (Appearance → Chat list → "Folder tabs"): its name ([Text], the default), its icon before its
 * name ([IconAndText]), or its icon alone ([Icon]), where the selected tab still shows its name and every tab its
 * unread count.
 */
enum class FolderTabStyle {
    Text,
    IconAndText,
    Icon,
}

/**
 * What swiping a chat row does. Each acts as its row allows: [Read] marks a chat as read, or as unread with nothing
 * unread; [Archive], [Pin] and [Mute] turn into unarchive, unpin and unmute where they apply; [Delete] asks first;
 * [None] leaves the row still. A chat that cannot be muted (Saved Messages) has no [Mute] swipe.
 */
enum class SwipeAction {
    Read,
    Archive,
    Pin,
    Mute,
    Delete,
    None,
}
