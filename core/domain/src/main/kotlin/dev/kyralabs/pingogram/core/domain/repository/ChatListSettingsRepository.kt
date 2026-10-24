package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How the chat list looks and acts (Appearance → Chat list), kept on this device: whether the story rings show above
 * the chats ([ChatListSettings.showStories], on by default; hiding them loads none), what swiping a row does
 * toward each side ([ChatListSettings.startSwipe], [ChatListSettings.endSwipe]), and whether swiping the list changes
 * the folder instead ([ChatListSettings.swipeChangesFolder], off by default).
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
}

/**
 * The chat list's own settings: whether the story rings show above the chats, and what a swipe toward the end
 * ([startSwipe], from the start edge) and toward the start ([endSwipe]) does; both mirror in right-to-left layouts.
 * With [swipeChangesFolder] a horizontal swipe moves the whole list to the folder tab beside the selected one instead,
 * and rows do not swipe; [startSwipe] and [endSwipe] are kept for when it is turned off.
 */
data class ChatListSettings(
    val showStories: Boolean = true,
    val startSwipe: SwipeAction = SwipeAction.Read,
    val endSwipe: SwipeAction = SwipeAction.Archive,
    val swipeChangesFolder: Boolean = false,
)

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
