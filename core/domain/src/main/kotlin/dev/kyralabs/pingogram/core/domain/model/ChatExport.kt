package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

// Exporting a chat's history to a folder the user picked (REQUIREMENTS.md §5.9, docs/adr/0021-chat-export.md): what
// the user chose, how far the export is, and what came of it. Only TDLib is asked for the messages and their files,
// and the output goes only to that folder: nothing leaves the device.

/** The file an export writes: one self-contained HTML page to read, or JSON for programs (its schema: ADR 0021). */
enum class ExportFormat {
    Html,
    Json,
}

/**
 * The kinds of media an export may copy into its folder, each a switch on the export page. What is not chosen, and
 * what has no file to copy (stickers, locations, polls), the output only names.
 */
enum class ExportMediaKind {
    /** Photos. */
    Photos,

    /** Videos, GIFs and video messages. */
    Videos,

    /** Voice messages. */
    VoiceMessages,

    /** Files and music. */
    Files,
}

/**
 * What the user chose on the export page.
 *
 * @property media the kinds of media copied into the export's `media` folder; empty for text only.
 * @property maxFileSize files larger than this many bytes are named but not copied; [NO_SIZE_LIMIT] copies all.
 * @property since only messages sent at or after this Unix time (seconds) are exported; null for the whole history.
 * @property folderUri the folder the user picked through the Storage Access Framework (a tree URI, with a persisted
 *   write grant), in which the export makes a folder of its own.
 */
data class ChatExportOptions(
    val format: ExportFormat = ExportFormat.Html,
    val media: ImmutableSet<ExportMediaKind> = persistentSetOf(),
    val maxFileSize: Long = NO_SIZE_LIMIT,
    val since: Long? = null,
    val folderUri: String,
) {
    // The folder's URI names the user's storage, which logs need not show.
    override fun toString() =
        "ChatExportOptions(format=$format, media=$media, maxFileSize=$maxFileSize, since=${since != null}, …)"

    companion object {
        /** [maxFileSize] that copies every file, as large as Telegram allows (4 GB with Telegram Premium). */
        const val NO_SIZE_LIMIT = Long.MAX_VALUE
    }
}

/**
 * How far an export is.
 *
 * @property messages how many messages were read so far, newest first.
 * @property fraction how much of the export is done, from 0 to 1; null while it cannot be told (the chat's size is
 *   not known), shown as indeterminate progress.
 * @property floodWaitSeconds Telegram asked to wait this long before the next page; null while it reads.
 */
data class ChatExportProgress(
    val messages: Int = 0,
    val fraction: Float? = null,
    val floodWaitSeconds: Long? = null,
)

/**
 * A finished export.
 *
 * @property messageCount how many messages it holds.
 * @property mediaCount how many files it copied into its `media` folder.
 * @property skippedMediaCount how many media it only names: kinds not chosen, files over the size limit, or files
 *   that could not be downloaded.
 * @property folderUri the export's own folder, inside the one the user picked: a document URI to open it.
 */
data class ChatExportResult(
    val messageCount: Int,
    val mediaCount: Int,
    val skippedMediaCount: Int,
    val folderUri: String,
) {
    override fun toString() = "ChatExportResult(messageCount=$messageCount, mediaCount=$mediaCount, …)"
}

/** Why an export did not finish. Whatever it wrote is deleted (ADR 0021). */
sealed interface ChatExportError {
    /** The folder the user picked is gone, or Pingogram may no longer write to it. */
    data object FolderUnavailable : ChatExportError

    /** The storage the folder is on is full. */
    data object StorageFull : ChatExportError

    /**
     * The chat cannot be exported: a secret chat, whose messages are meant to stay on the device and in it, or a chat
     * whose owner turned off saving and forwarding its content (`has_protected_content`).
     */
    data object NotAllowed : ChatExportError

    /** TDLib failed, e.g. [DomainError.Network], [DomainError.NotFound] or [DomainError.Unauthorized]. */
    data class Telegram(
        val error: DomainError,
    ) : ChatExportError
}

/** What became of an export the user started; [ChatExportJobs] reports it. */
sealed interface ChatExportStatus {
    /** Waiting to start, or running, with its [progress]. */
    data class Running(
        val progress: ChatExportProgress,
    ) : ChatExportStatus

    data class Done(
        val result: ChatExportResult,
    ) : ChatExportStatus

    data class Failed(
        val error: ChatExportError,
    ) : ChatExportStatus

    /** The user cancelled it, on the export page or in its notification. */
    data object Cancelled : ChatExportStatus
}

/** The latest export of a chat: [id] tells one run from the next, [status] what became of it. */
data class ChatExportJob(
    val id: String,
    val status: ChatExportStatus,
)

/** The chat an export reads, as its output names it. `toString()` leaves out the [title]. */
data class ExportChat(
    val id: Long,
    val title: String,
    val kind: ExportChatKind,
) {
    override fun toString() = "ExportChat(id=$id, kind=$kind, …)"
}

enum class ExportChatKind {
    Private,
    Bot,
    SavedMessages,
    Group,
    Channel,
}

/**
 * One message as an export writes it, with what needs words already in words (the sender's name, a service message's
 * text), so the writers are plain text formatting. Times are Unix timestamps in seconds. `toString()` leaves out what
 * people wrote.
 *
 * @property sender who sent it, null for channel posts without a signature and service events nobody caused.
 * @property text the text or caption, with its entities; empty for media without a caption and service messages.
 * @property replyToMessageId the message of the same chat it replies to; null if none.
 * @property forwardedFrom the name of the original sender of a forwarded message; null if it was not forwarded.
 * @property summary what the message is, in words, when its [text] does not say: "Location", "Poll: Lunch?",
 *   "Sticker 😀"; null for text messages and plain media.
 * @property service what a service message reports ("Ada joined the group"); null for other messages.
 */
data class ExportMessage(
    val id: Long,
    val date: Long,
    val editDate: Long,
    val sender: ExportSender?,
    val isOutgoing: Boolean,
    val text: FormattedText,
    val replyToMessageId: Long?,
    val forwardedFrom: String?,
    val forwardDate: Long,
    val media: ExportMedia?,
    val summary: String?,
    val service: String?,
) {
    override fun toString() = "ExportMessage(id=$id, date=$date, media=$media, …)"
}

/** A message's sender: user [id], or a chat when [isChat], named [name]. */
data class ExportSender(
    val id: Long,
    val isChat: Boolean,
    val name: String,
) {
    override fun toString() = "ExportSender(id=$id, isChat=$isChat, …)"
}

/**
 * A message's file, copied into the export or only named.
 *
 * @property fileName the name it had, or one the export made up (`photo_<message id>.jpg`); may be empty.
 * @property size in bytes, 0 if unknown.
 * @property path where the export copied it, relative to the export's folder (`media/…`); null if not copied.
 * @property skipped why it was not copied; null if it was.
 */
data class ExportMedia(
    val type: ExportMediaType,
    val fileName: String,
    val size: Long,
    val path: String?,
    val skipped: ExportSkipReason?,
) {
    override fun toString() = "ExportMedia(type=$type, size=$size, copied=${path != null}, skipped=$skipped)"
}

enum class ExportMediaType {
    Photo,
    Video,
    Animation,
    VideoNote,
    VoiceNote,
    Audio,
    Document,
    Sticker,
}

/** Why a message's file is only named in an export. */
enum class ExportSkipReason {
    /** Its kind was not chosen, or it has none (stickers). */
    NotChosen,

    /** It is larger than the size limit. */
    TooLarge,

    /** It self-destructs once opened (view-once media), so it is not kept anywhere else. */
    SelfDestructing,

    /** Telegram could not give it: deleted, or no longer available. */
    Unavailable,
}
