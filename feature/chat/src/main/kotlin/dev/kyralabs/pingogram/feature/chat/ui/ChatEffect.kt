package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.text.UiText

/** One-off effects of [ChatViewModel], handled by the screen once each (`docs/ARCHITECTURE.md`, ViewModels). */
sealed interface ChatEffect {
    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. Errors are never dialogs. */
    data class ShowError(
        val error: DomainError,
    ) : ChatEffect

    /**
     * Open the downloaded document at [path] in another app, as [mimeType] (the document's, null if it names none); a
     * snackbar if no app takes it.
     */
    data class OpenFile(
        val path: String,
        val mimeType: String?,
    ) : ChatEffect {
        // The path ends in the document's name, which can be personal.
        override fun toString() = "OpenFile(mimeType=$mimeType, …)"
    }

    /** The file of a voice message or song could not be played: a snackbar says so. */
    data object PlaybackFailed : ChatEffect

    /**
     * The message of a tapped quote, or the one the conversation was opened at, no longer exists: a snackbar says so,
     * and the list shows where it was.
     */
    data object MessageUnavailable : ChatEffect

    /** A message was scheduled for [at] (Unix time, seconds): a snackbar says when it will be sent. */
    data class MessageScheduled(
        val at: Long,
    ) : ChatEffect

    /** Open the profile of user [userId] (`REQUIREMENTS.md` FR-P2). */
    data class OpenProfile(
        val userId: Long,
    ) : ChatEffect

    /** Open chat [chatId], a group or channel a mention named. */
    data class OpenChat(
        val chatId: Long,
    ) : ChatEffect

    /** Open the photo, video or GIF of message [messageId] of this chat in the media viewer. */
    data class OpenMedia(
        val messageId: Long,
    ) : ChatEffect

    /**
     * Put [text] on the clipboard, as plain text. Android 13 and later confirm it themselves; earlier versions get a
     * snackbar.
     */
    data class CopyText(
        val text: String,
    ) : ChatEffect {
        override fun toString() = "CopyText(length=${text.length})"
    }

    /** The user started a reply or an edit: the composer's text field takes the focus, which opens the keyboard. */
    data object FocusComposer : ChatEffect

    /** The action asked for is not allowed for the message (any more): a snackbar says so. */
    data object ActionUnavailable : ChatEffect

    /** "Join" sent a request to join the chat, which an administrator accepts: a snackbar says so. */
    data object JoinRequestSent : ChatEffect

    /** Telegram did not let the user join, or the chat checks new members with a bot first: a snackbar says so. */
    data object JoinRefused : ChatEffect

    /** The pinned messages bar was hidden on this device: a snackbar says it returns with a newer pinned message. */
    data object PinnedHidden : ChatEffect

    /** A bot's menu button opens a Mini App, which Pingogram cannot open yet: a snackbar says so, never a WebView. */
    data object MiniAppsUnsupported : ChatEffect

    /** An inline bot's result is a game, which Pingogram cannot play yet: a snackbar says so, nothing is sent. */
    data object GamesUnsupported : ChatEffect

    /** A day picked to jump to has no messages on it or before it (the chat starts later): a snackbar says so. */
    data object NoMessagesForDay : ChatEffect

    /**
     * Open the conversation of [topic] of chat [chatId]: a message's thread (`REQUIREMENTS.md` FR-C9), whose chat is
     * the channel's discussion group for a post's comments.
     */
    data class OpenThread(
        val chatId: Long,
        val topic: MessageTopic,
    ) : ChatEffect

    /**
     * Messages were forwarded to [count] chats: a snackbar says so, naming the chat if there was one ([chatTitle]),
     * with an action that opens it if it is another chat ([openChatId]).
     */
    data class Forwarded(
        val count: Int,
        val chatTitle: UiText? = null,
        val openChatId: Long? = null,
    ) : ChatEffect

    /**
     * Forwarding to [failed] chats failed, the first time with [error] (a flood wait stops it, counting the chats left
     * as failed); [forwarded] chats got the messages. A snackbar says so.
     */
    data class ForwardFailed(
        val error: DomainError,
        val failed: Int,
        val forwarded: Int,
    ) : ChatEffect

    /**
     * A message being sent failed while the screen was shown: a `Reject` haptic on Android 14 and later; the message's
     * "Not sent" label says the rest.
     */
    data object SendFailed : ChatEffect

    /**
     * Open the camera app to take a photo into the file at [path] (in the app's cache, handed over as a content URI);
     * the screen reports what came of it with `ChatEvent.PhotoTaken`, also when no camera app opens.
     */
    data class LaunchCamera(
        val path: String,
    ) : ChatEffect {
        override fun toString() = "LaunchCamera(…)"
    }

    /** Show the point at [latitude], [longitude] (in degrees) in a maps app, named [label] if there is one. */
    data class OpenLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?,
    ) : ChatEffect {
        override fun toString() = "OpenLocation(…)"
    }

    /**
     * Open [url], a sponsored message's link or a bot's button's, with the app's link handler (Telegram links open in
     * Pingogram, others in the platform's handler); a snackbar if no app on the device can.
     */
    data class OpenUrl(
        val url: String,
    ) : ChatEffect {
        override fun toString() = "OpenUrl(…)"
    }

    /** What came of reporting a sponsored message: a snackbar says it. */
    data class SponsoredNotice(
        val notice: SponsoredReportNotice,
    ) : ChatEffect
}

/** What a report of a sponsored message came to ([ChatEffect.SponsoredNotice]). */
enum class SponsoredReportNotice {
    /** Telegram has the report; the ad is gone from the list. */
    Reported,

    /** Telegram hid sponsored messages for the user. */
    AdsHidden,

    /** Hiding ads needs Telegram Premium; said, never offered. */
    PremiumRequired,

    /** Telegram did not take the report. */
    ReportFailed,
}
