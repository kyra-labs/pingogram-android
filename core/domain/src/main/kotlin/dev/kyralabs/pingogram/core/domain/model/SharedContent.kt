package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the user shares into Pingogram (`REQUIREMENTS.md` FR-N2, direct share): from another app's share sheet
 * (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`) or a `t.me/share?url=…&text=…` link ([LinkTarget.MessageDraft]). Nothing of
 * it is sent until the user picked a chat and tapped send (Telegram's API terms).
 *
 * @property text the shared text, links included; empty when only files are shared, the caption when both are.
 * @property files the shared files, by their `content://` URIs, read with the grant they came with and copied into the
 *   upload cache only once a chat is picked; at most [MAX_FILES].
 * @property kind how the files go: [AttachmentKind.Media] (photos and videos, grouped into albums) when the sharing
 *   app said every one is an image or a video ([kindOf]), otherwise [AttachmentKind.Document], as they are.
 * @property story a story the stories viewer shares (td_api.tl `inputMessageStory`), sent to each chat picked; such
 *   a share has no text or files.
 * @property voiceNote nothing is shared, but a voice message is to be recorded in the chat picked: the Quick compose
 *   widget's "Voice" (board 33). The chat opens with a locked recording under way, which the user sends or deletes.
 *
 * `toString()` leaves the text and the URIs out (`REQUIREMENTS.md` §6.2).
 */
data class SharedContent(
    val text: FormattedText = FormattedText.Empty,
    val files: ImmutableList<String> = persistentListOf(),
    val kind: AttachmentKind = AttachmentKind.Document,
    val story: SharedStory? = null,
    val voiceNote: Boolean = false,
) {
    /** Nothing to share: no text, no file, no story, and no voice message to record. */
    val isEmpty: Boolean get() = text.text.isBlank() && files.isEmpty() && story == null && !voiceNote

    override fun toString() =
        "SharedContent(textLength=${text.text.length}, files=${files.size}, kind=$kind, story=${story != null}, " +
            "voiceNote=$voiceNote)"

    companion object {
        /** The most files one share takes; more are left out. */
        const val MAX_FILES = 100

        /**
         * How files of MIME type [mimeType] go, the type the sharing app gave all of them (for several of different
         * types, a wildcard type such as "image" or "any"): as photos and videos for images and videos, else as
         * documents.
         */
        fun kindOf(mimeType: String?): AttachmentKind {
            val type = mimeType?.lowercase().orEmpty()
            return if (type.startsWith("image/") || type.startsWith("video/")) {
                AttachmentKind.Media
            } else {
                AttachmentKind.Document
            }
        }
    }
}

/** Story [storyId] of chat [posterChatId], shared from the stories viewer. */
data class SharedStory(
    val posterChatId: Long,
    val storyId: Int,
)

/**
 * What came of sending a share to several chats (`ShareRepository`):
 * [sent] chats got everything, [failed] did not; [error] is the first refusal, null when every chat got it.
 */
data class ShareResult(
    val sent: Int,
    val failed: Int,
    val error: DomainError? = null,
)
