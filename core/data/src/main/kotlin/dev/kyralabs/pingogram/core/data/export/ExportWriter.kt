package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import java.time.Instant

/**
 * Writes an export's one file, message by message, oldest first, into a stream it never holds whole
 * (`docs/adr/0021-chat-export.md`): [begin] once with the chat and how many messages follow, [message] for each,
 * [end] once. Plain Kotlin on an [Appendable], tested on strings.
 */
internal interface ExportWriter {
    fun begin(
        chat: ExportChat,
        exportedAt: Long,
        messageCount: Int,
    )

    fun message(message: ExportMessage)

    fun end()
}

/** The file's name in the export's folder. */
internal val ExportFormat.fileName: String
    get() =
        when (this) {
            ExportFormat.Html -> "messages.html"
            ExportFormat.Json -> "messages.json"
        }

internal val ExportFormat.mimeType: String
    get() =
        when (this) {
            ExportFormat.Html -> "text/html"
            ExportFormat.Json -> "application/json"
        }

/**
 * The writer of this format into [out]. [texts] words the HTML page; [isExported] says whether a message is in the
 * export, so a reply links to it only then.
 */
internal fun ExportFormat.writer(
    out: Appendable,
    texts: ExportTexts,
    isExported: (messageId: Long) -> Boolean,
): ExportWriter =
    when (this) {
        ExportFormat.Html -> HtmlExportWriter(out, texts, isExported)
        ExportFormat.Json -> JsonExportWriter(out)
    }

/** Unix time [seconds] as ISO 8601 in UTC: "2026-10-23T09:41:00Z". */
internal fun isoTime(seconds: Long): String = Instant.ofEpochSecond(seconds).toString()

/** The kind of chat as the JSON schema names it. */
internal val ExportChatKind.schemaName: String
    get() =
        when (this) {
            ExportChatKind.Private -> "private"
            ExportChatKind.Bot -> "bot"
            ExportChatKind.SavedMessages -> "saved_messages"
            ExportChatKind.Group -> "group"
            ExportChatKind.Channel -> "channel"
        }

internal val ExportMediaType.schemaName: String
    get() =
        when (this) {
            ExportMediaType.Photo -> "photo"
            ExportMediaType.Video -> "video"
            ExportMediaType.Animation -> "animation"
            ExportMediaType.VideoNote -> "video_message"
            ExportMediaType.VoiceNote -> "voice_message"
            ExportMediaType.Audio -> "audio"
            ExportMediaType.Document -> "file"
            ExportMediaType.Sticker -> "sticker"
        }

internal val ExportSkipReason.schemaName: String
    get() =
        when (this) {
            ExportSkipReason.NotChosen -> "not_chosen"
            ExportSkipReason.TooLarge -> "too_large"
            ExportSkipReason.SelfDestructing -> "self_destructing"
            ExportSkipReason.Unavailable -> "unavailable"
        }
