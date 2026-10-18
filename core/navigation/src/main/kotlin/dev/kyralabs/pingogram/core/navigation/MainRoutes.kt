package dev.kyralabs.pingogram.core.navigation

import kotlinx.serialization.Serializable

/**
 * The graph of the signed-in app (`docs/ARCHITECTURE.md`, Navigation): the three top-level destinations
 * reached from the navigation bar or rail. [ChatsRoute] is its start destination.
 */
@Serializable
data object MainGraph

/** The chat list, `:feature:chats`. */
@Serializable
data object ChatsRoute

/** The contacts list, `:feature:contacts`. */
@Serializable
data object ContactsRoute

/** Settings, `:feature:settings`. */
@Serializable
data object SettingsRoute

/**
 * Appearance settings (`REQUIREMENTS.md` FR-S2): theme, dynamic color, AMOLED black and contrast, `:feature:settings`,
 * pushed above the Settings tab.
 */
@Serializable
data object AppearanceRoute

/**
 * Data and storage settings (`REQUIREMENTS.md` FR-S1): how much space Pingogram takes and clearing its cache,
 * `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object DataStorageRoute

/**
 * About Pingogram (`REQUIREMENTS.md` FR-S1): version, source code and license, `:feature:settings`, pushed above the
 * Settings tab.
 */
@Serializable
data object AboutRoute

/**
 * Global search of chats, contacts and messages (`REQUIREMENTS.md` FR-L6), `:feature:chats`, opened from the search
 * button of the navigation bar or rail.
 */
@Serializable
data object SearchRoute

/** The archived chats (`REQUIREMENTS.md` FR-L9), `:feature:chats`, pushed above the Chats tab from its archive row. */
@Serializable
data object ArchiveRoute

/**
 * The conversation of chat [chatId] (`REQUIREMENTS.md` FR-C1–C8), `:feature:chat`, pushed above the Chats tab from a
 * row of the chat list, the archive or search. With [messageId] (a message found by search) it opens at that message,
 * highlighted; without, at the newest messages or the first unread one.
 */
@Serializable
data class ChatRoute(
    val chatId: Long,
    val messageId: Long? = null,
)

/**
 * The full-screen media viewer (`REQUIREMENTS.md` FR-C10), `:feature:media`, opened on the photo, video or GIF
 * [messageId] of chat [chatId] from the conversation, and paging through the chat's media of that kind. It is pushed
 * above everything: no navigation bar or rail, and full screen on top of the list-detail layout of the Chats tab.
 */
@Serializable
data class MediaViewerRoute(
    val chatId: Long,
    val messageId: Long,
)
