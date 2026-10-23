package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportMessage

/**
 * `messages.json` (`docs/adr/0021-chat-export.md`, where the schema is): an object with the format's name and
 * version, when it was exported, the chat, the message count, then the messages, oldest first. Times are ISO 8601 in
 * UTC with their Unix seconds beside them; entity offsets and lengths count UTF-16 code units, as TDLib's do; fields
 * without a value are left out; a file's path is relative to the file.
 */
internal class JsonExportWriter(
    out: Appendable,
) : ExportWriter {
    private val json = JsonOut(out)

    override fun begin(
        chat: ExportChat,
        exportedAt: Long,
        messageCount: Int,
    ) {
        json
            .beginObject()
            .field("format", FORMAT)
            .field("version", VERSION)
            .field("exported_at", isoTime(exportedAt))
            .name("chat")
            .beginObject()
            .field("id", chat.id)
            .field("title", chat.title)
            .field("type", chat.kind.schemaName)
            .endObject()
            .field("message_count", messageCount.toLong())
            .name("messages")
            .beginArray()
    }

    override fun message(message: ExportMessage) {
        json
            .beginObject()
            .field("id", message.id)
            .field("date", isoTime(message.date))
            .field("date_unix", message.date)
        if (message.editDate > 0) json.field("edited", isoTime(message.editDate)).field("edited_unix", message.editDate)
        message.sender?.let { sender ->
            json
                .name("from")
                .beginObject()
                .field("id", sender.id)
                .field("type", if (sender.isChat) "chat" else "user")
                .field("name", sender.name)
                .endObject()
        }
        json.field("outgoing", message.isOutgoing)
        message.service?.let { json.field("service", it) }
        if (message.text.text.isNotEmpty()) {
            json.field("text", message.text.text)
            if (message.text.entities.isNotEmpty()) entities(message)
        }
        json.field("summary", message.summary)
        json.field("reply_to_message_id", message.replyToMessageId)
        message.forwardedFrom?.let { from ->
            json.name("forwarded").beginObject().field("from", from)
            if (message.forwardDate > 0) {
                json.field("date", isoTime(message.forwardDate)).field("date_unix", message.forwardDate)
            }
            json.endObject()
        }
        message.media?.let { media ->
            json
                .name("media")
                .beginObject()
                .field("type", media.type.schemaName)
                .field("file", media.path)
                .field("file_name", media.fileName.takeIf { it.isNotEmpty() })
                .field("size", media.size.takeIf { it > 0 })
                .field("not_exported", media.skipped?.schemaName)
                .endObject()
        }
        json.endObject()
    }

    private fun entities(message: ExportMessage) {
        json.name("entities").beginArray()
        for (entity in message.text.entities) {
            val name = entity.type.toEntityName()
            json
                .beginObject()
                .field("type", name.name)
                .field("offset", entity.offset.toLong())
                .field("length", entity.length.toLong())
            when (name.name) {
                "text_url" -> json.field("url", name.text)
                "pre_code" -> json.field("language", name.text)
                "mention_name" -> json.field("user_id", name.number)
                "custom_emoji" -> json.field("custom_emoji_id", name.number)
                "media_timestamp" -> json.field("seconds", name.number)
                "date_time" -> json.field("unix_time", name.number)
            }
            json.endObject()
        }
        json.endArray()
    }

    override fun end() {
        json.endArray().endObject()
    }

    private companion object {
        const val FORMAT = "pingogram_chat_export"
        const val VERSION = 1L
    }
}
