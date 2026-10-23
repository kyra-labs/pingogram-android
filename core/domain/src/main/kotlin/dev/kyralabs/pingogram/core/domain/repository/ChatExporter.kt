package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * Runs one export of a chat's history into the folder the user picked (`REQUIREMENTS.md` §5.9,
 * `docs/adr/0021-chat-export.md`): it reads the history from the newest message back through [ChatExportRepository],
 * copies the chosen media into a `media` folder as it goes, and writes `messages.html` or `messages.json`, oldest
 * message first, into a folder of its own inside the one picked. [ChatExportJobs] runs it in the background.
 *
 * Cancelling the coroutine stops it between two requests; a cancelled or failed export deletes its folder and its
 * temporary files, so nothing half-written stays behind.
 */
interface ChatExporter {
    /**
     * Exports chat [chatId] as [options] say, with [texts] for what needs words, reporting how far it is to
     * [onProgress] after every page and file.
     */
    suspend fun export(
        chatId: Long,
        options: ChatExportOptions,
        texts: ExportTexts,
        onProgress: suspend (ChatExportProgress) -> Unit,
    ): ChatExportOutcome
}

/** What came of [ChatExporter.export]. */
sealed interface ChatExportOutcome {
    data class Success(
        val result: ChatExportResult,
    ) : ChatExportOutcome

    data class Failure(
        val error: ChatExportError,
    ) : ChatExportOutcome
}

/**
 * The words an export writes, in the app's language: what service messages report, what a message without text is,
 * and the HTML page's own labels and dates. JSON keeps keys and Unix times, and words only where a message has no
 * text of its own.
 */
interface ExportTexts {
    /** What service [message] reports, naming people by [name] ("Ada joined the group"). */
    fun service(
        message: Message,
        name: (MessageSender) -> String?,
    ): String

    /** What [content] is when its text does not say: "Location", "Poll: Lunch?", "Sticker 😀"; null for plain text. */
    fun summary(content: MessageContent): String?

    /** The day of Unix time [seconds] in the user's time zone, as a heading: "October 23, 2026". */
    fun day(seconds: Long): String

    /** The time of day of Unix time [seconds]: "9:41 AM". */
    fun time(seconds: Long): String

    /** The HTML page's labels. */
    val labels: ExportLabels
}

/**
 * The fixed words of an exported HTML page; each `%s` is filled in where it is written.
 *
 * @property exportedOn "Exported from Pingogram on %s".
 * @property messageCount "%s messages", with the count formatted.
 * @property forwardedFrom "Forwarded from %s".
 * @property inReplyTo "In reply to this message", the link to the message replied to.
 * @property replyNotExported "In reply to a message not in this export".
 * @property edited "edited", after the time.
 * @property mediaNotExported "%s not exported", with what the media is ("Photo").
 * @property mediaTooLarge "%s not exported: larger than the size limit".
 * @property mediaSelfDestructing "%s not exported: it self-destructs once opened".
 * @property mediaUnavailable "%s not exported: not available".
 * @property noMessages "No messages", for an empty export.
 * @property deletedAccount the name of a sender TDLib does not know.
 * @property savedMessages the name of the user's own chat with themselves.
 * @property mediaName what a file is, in a word: "Photo", "Voice message".
 */
data class ExportLabels(
    val exportedOn: String,
    val messageCount: (Int) -> String,
    val forwardedFrom: String,
    val inReplyTo: String,
    val replyNotExported: String,
    val edited: String,
    val mediaNotExported: String,
    val mediaTooLarge: String,
    val mediaSelfDestructing: String,
    val mediaUnavailable: String,
    val noMessages: String,
    val deletedAccount: String,
    val savedMessages: String,
    val mediaName: (ExportMediaType) -> String,
)
