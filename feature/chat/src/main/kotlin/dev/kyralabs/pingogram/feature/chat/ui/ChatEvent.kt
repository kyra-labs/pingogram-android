package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.SendOptions

/** What the user and the screen's lifecycle do on the conversation screen, sent to [ChatViewModel.onEvent]. */
sealed interface ChatEvent {
    /** The screen is started (visible): the chat is opened in TDLib until [Stopped]. */
    data object Started : ChatEvent

    /** The screen is stopped, or left: the chat is closed in TDLib. */
    data object Stopped : ChatEvent

    /**
     * Whether the list shows its oldest loaded message or is within about a page of it; reported on every change,
     * and false when the list leaves the screen. While true, older messages load, page after page, until the start.
     */
    data class NearOldestChanged(
        val isNearOldest: Boolean,
    ) : ChatEvent

    /**
     * Whether the list shows its newest loaded message or is within about a page of it; reported on every change, and
     * false when the list leaves the screen. While true, and the items do not reach the chat's newest message, newer
     * messages load, page after page.
     */
    data class NearNewestChanged(
        val isNearNewest: Boolean,
    ) : ChatEvent

    /**
     * Show the chat's newest message: the jump button, or a message sent while the list shows older messages that do
     * not reach it. The newest messages load in place of the loaded ones, and the list goes to them.
     */
    data object ShowNewest : ChatEvent

    /**
     * The messages the list shows now, wholly or in part, by id (every item of an album); reported whenever that
     * changes, and empty when the list leaves the screen. They go to TDLib as viewed, which reads them.
     */
    data class MessagesVisible(
        val messageIds: Set<Long>,
    ) : ChatEvent

    /**
     * The reply quote of a bubble was tapped: show the replied message [messageId] of this chat, loading the messages
     * around it if needed.
     */
    data class OpenReply(
        val messageId: Long,
    ) : ChatEvent

    /** "Try again" after finding the chat or loading messages failed. */
    data object Retry : ChatEvent

    /** A hidden spoiler in message [messageId] was tapped: every spoiler of its text shows. */
    data class RevealSpoilers(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The bubble of message [messageId] was tapped where nothing else takes the tap: its time and state show, or hide
     * again, where its group shows them on its last bubble only.
     */
    data class ToggleTime(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The photo, video, GIF or video message [messageId] (an album's item, by its own message) was tapped: a photo,
     * video or GIF opens in the media viewer ([ChatEffect.OpenMedia]). Video messages and self-destructing media do not
     * open there.
     */
    data class OpenMedia(
        val messageId: Long,
    ) : ChatEvent

    /** Download the file [fileId] of a document, at visible priority. */
    data class DownloadFile(
        val fileId: Int,
    ) : ChatEvent

    /** Stop downloading the file [fileId]; what was downloaded stays. */
    data class CancelDownload(
        val fileId: Int,
    ) : ChatEvent

    /** The downloaded document [fileId] of message [messageId] was tapped, to open it with another app. */
    data class OpenFile(
        val messageId: Long,
        val fileId: Int,
    ) : ChatEvent

    /** The location or venue of message [messageId] was tapped, to show it in a maps app. */
    data class OpenLocation(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The play button of the voice message or song [messageId], whose file is [fileId], was tapped: it plays, or
     * pauses, downloading the file first if it is not on the device.
     */
    data class PlayPause(
        val messageId: Long,
        val fileId: Int,
    ) : ChatEvent

    /**
     * The voice message or song [messageId] is to play from [fraction] (0–1) of its length: the one in the player
     * moves there, another one starts there.
     */
    data class Seek(
        val messageId: Long,
        val fraction: Float,
    ) : ChatEvent

    /** Send the composer's text as [options] say: now, without sound, or at a time. */
    data class Send(
        val options: SendOptions = SendOptions(),
    ) : ChatEvent

    /** The composer's text field gained or lost the focus; a draft from another device only fills it unfocused. */
    data class ComposerFocusChanged(
        val isFocused: Boolean,
    ) : ChatEvent

    // The attach menu (REQUIREMENTS.md FR-C5). Pickers return content URIs, readable while their grant lasts.

    /** Photos and videos picked in the Photo Picker, by their URIs: they open in the preview, being prepared. */
    data class AttachMedia(
        val uris: List<String>,
    ) : ChatEvent {
        override fun toString() = "AttachMedia(${uris.size})"
    }

    /** Files picked in the document picker, by their URIs: they open in the preview as documents. */
    data class AttachFiles(
        val uris: List<String>,
    ) : ChatEvent {
        override fun toString() = "AttachFiles(${uris.size})"
    }

    /** "Camera" in the attach menu: a file for the photo is made, then the camera app opens. */
    data object TakePhoto : ChatEvent

    /** The camera app returned, with a photo if [taken]: it joins the preview. */
    data class PhotoTaken(
        val taken: Boolean,
    ) : ChatEvent

    /** A phone number picked in the contact picker, at [uri]: it is sent as a contact. */
    data class AttachContact(
        val uri: String,
    ) : ChatEvent {
        override fun toString() = "AttachContact(…)"
    }

    /** The "Location" sheet's send button: [location], the device's, is sent as [options] say. */
    data class SendLocation(
        val location: DeviceLocation,
        val options: SendOptions = SendOptions(),
    ) : ChatEvent

    /** The remove button of item [key] of the preview. */
    data class RemoveAttachment(
        val key: Long,
    ) : ChatEvent

    /** The preview was dismissed: nothing is sent. */
    data object DismissAttachments : ChatEvent

    /** Send the preview's items with their caption as [options] say. */
    data class SendAttachments(
        val options: SendOptions = SendOptions(),
    ) : ChatEvent

    /** "Poll" in the attach menu: the form of a new poll opens. */
    data object OpenPoll : ChatEvent

    /** The poll form was dismissed: nothing is sent, the form empties. */
    data object DismissPoll : ChatEvent

    /** Send the poll of the form as [options] say. */
    data class SendPoll(
        val options: SendOptions = SendOptions(),
    ) : ChatEvent

    /** The cancel button on the upload of messages [messageIds], still being sent: they are deleted. */
    data class CancelUpload(
        val messageIds: List<Long>,
    ) : ChatEvent

    // Message actions (REQUIREMENTS.md FR-C6, FR-C7). Each closes the actions sheet, and does nothing where TDLib does
    // not allow it (any more).

    /** Message [messageId] was long-pressed, or a screen reader asked for its actions: open the actions sheet. */
    data class ShowMessageActions(
        val messageId: Long,
    ) : ChatEvent

    /**
     * The "+N" badge on message [messageId]'s bubble, or a screen reader's action for it: open the actions sheet at
     * its reactions picker, which shows every reaction of the message.
     */
    data class ShowAllReactions(
        val messageId: Long,
    ) : ChatEvent

    /** The button at the end of the actions sheet's quick reactions: the sheet shows the reactions picker. */
    data object ExpandReactions : ChatEvent

    /** The reactions picker's back button: the sheet shows the quick reactions and the actions again. */
    data object CollapseReactions : ChatEvent

    /** The actions sheet was dismissed without an action. */
    data object DismissMessageActions : ChatEvent

    /** Reply to message [messageId]: the composer shows it in its reply bar. */
    data class Reply(
        val messageId: Long,
    ) : ChatEvent

    /** The reply bar's close button: the next message replies to nothing. */
    data object CancelReply : ChatEvent

    /** Copy the text or caption of message [messageId] to the clipboard, as plain text. */
    data class Copy(
        val messageId: Long,
    ) : ChatEvent

    /** Edit message [messageId]: its text or caption fills the composer, which sends the edit instead of a message. */
    data class Edit(
        val messageId: Long,
    ) : ChatEvent

    /** The edit bar's close button: the composer gets back what it held before editing. */
    data object CancelEdit : ChatEvent

    /** Pin message [messageId]: for the user alone if [onlyForSelf] (private chats), else for everyone. */
    data class Pin(
        val messageId: Long,
        val onlyForSelf: Boolean = false,
    ) : ChatEvent

    data class Unpin(
        val messageId: Long,
    ) : ChatEvent

    /** Delete message [messageId], an album's every item: asks first ([ChatUiState.deleteDialog]). */
    data class Delete(
        val messageId: Long,
    ) : ChatEvent

    /** The delete confirmation's button, with whether its check box asks to delete for everyone too. */
    data class ConfirmDelete(
        val forEveryone: Boolean,
    ) : ChatEvent

    /** The delete confirmation was dismissed: nothing is deleted. */
    data object DismissDelete : ChatEvent

    /**
     * Forward message [messageId], an album's every item: the forward sheet opens with the chats it can go to
     * ([ChatViewModel.forwardSheet]).
     */
    data class Forward(
        val messageId: Long,
    ) : ChatEvent

    /** Send the failed message [messageId] again (an album's failed items). */
    data class Resend(
        val messageId: Long,
    ) : ChatEvent

    /**
     * A reaction chip of message [messageId], or a reaction of its actions sheet (quick row or picker), was tapped: the
     * user's reaction [label] goes on the message, or comes off it if they had added it.
     */
    data class ToggleReaction(
        val messageId: Long,
        val label: ReactionLabel,
    ) : ChatEvent
}
