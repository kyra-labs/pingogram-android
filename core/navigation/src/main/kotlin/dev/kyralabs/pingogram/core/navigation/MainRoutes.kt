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
 * Editing the user's own profile (`REQUIREMENTS.md` FR-S1, Account): photo, name, username and bio,
 * `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object EditProfileRoute

/**
 * Appearance settings (`REQUIREMENTS.md` FR-S2): theme, dynamic color, AMOLED black and contrast, `:feature:settings`,
 * pushed above the Settings tab.
 */
@Serializable
data object AppearanceRoute

/**
 * The app's language (`REQUIREMENTS.md` FR-S1, §6.4): the system's or one of the languages Pingogram ships,
 * `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object LanguageRoute

/**
 * Data and storage settings (`REQUIREMENTS.md` FR-S1): how much space Pingogram takes and clearing its cache,
 * `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object DataStorageRoute

/**
 * About Pingogram (`REQUIREMENTS.md` FR-S1): version, source code, license and the open-source licenses,
 * `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object AboutRoute

/**
 * The open-source licenses (`REQUIREMENTS.md` FR-S1, About): the libraries, fonts and icons Pingogram is built from,
 * `:feature:settings`, pushed above [AboutRoute].
 */
@Serializable
data object OpenSourceLicensesRoute

/**
 * One library of [OpenSourceLicensesRoute] with the full texts of its licenses, pushed above it. [libraryId] is the
 * id of an `OpenSourceLibrary` of `:core:domain`, which this module does not depend on.
 */
@Serializable
data class OpenSourceLibraryRoute(
    val libraryId: String,
)

/**
 * Privacy and security (`REQUIREMENTS.md` FR-S1): the privacy settings, blocked users, active sessions and 2-step
 * verification, `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object PrivacySecurityRoute

/**
 * One privacy setting, who can see or do something, pushed above [PrivacySecurityRoute]. [setting] is the name of a
 * `PrivacySetting` of `:core:domain`, which this module does not depend on.
 */
@Serializable
data class PrivacySettingRoute(
    val setting: String,
)

/** The users and chats the user blocked, pushed above [PrivacySecurityRoute]. */
@Serializable
data object BlockedUsersRoute

/** The sessions of the account (devices logged in to it), pushed above [PrivacySecurityRoute]. */
@Serializable
data object ActiveSessionsRoute

/** The passcode lock's settings (`REQUIREMENTS.md` FR-S1), pushed above [PrivacySecurityRoute]. */
@Serializable
data object PasscodeLockRoute

/** Setting a new passcode, entered twice, pushed above [PasscodeLockRoute]. */
@Serializable
data object SetPasscodeRoute

/**
 * Notifications (`REQUIREMENTS.md` FR-S1): the defaults of private chats, groups and channels, "Keep connected" and
 * resetting them, `:feature:settings`, pushed above the Settings tab.
 */
@Serializable
data object NotificationSettingsRoute

/**
 * The chats of one kind with notification settings of their own, pushed above [NotificationSettingsRoute]. [scope] is
 * the name of a `NotificationScope` of `:core:domain`, which this module does not depend on.
 */
@Serializable
data class NotificationExceptionsRoute(
    val scope: String,
)

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
 * Starting a chat (`REQUIREMENTS.md` FR-L7): new group, new channel and new contact, then the contacts,
 * `:feature:chats`, pushed above the Chats tab from its "New chat" button or its empty state.
 */
@Serializable
data object NewChatRoute

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
