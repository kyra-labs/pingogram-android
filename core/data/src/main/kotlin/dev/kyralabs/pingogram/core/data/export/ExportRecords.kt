package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSender
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import kotlinx.collections.immutable.toImmutableList
import java.io.DataInputStream
import java.io.DataOutputStream

// The temporary records of an export (docs/adr/0021-chat-export.md): TDLib gives the history newest first, the file
// lists it oldest first, and a stream cannot be written backwards. So each page goes to a file of its own in the
// app's cache as it comes, and the output is written from the last page to the first, each page reversed in memory:
// never more than one page of messages is held. A record is the message's fields in order, strings as their UTF-8
// length and bytes; it is read back only by this version of the app, in the same export.

/** Writes [messages], one page, as records. */
internal fun DataOutputStream.writePage(messages: List<ExportMessage>) {
    writeInt(messages.size)
    messages.forEach { writeMessage(it) }
}

/** The page [writePage] wrote, in the same order. */
internal fun DataInputStream.readPage(): List<ExportMessage> = List(readInt()) { readMessage() }

private fun DataOutputStream.writeMessage(message: ExportMessage) {
    writeLong(message.id)
    writeLong(message.date)
    writeLong(message.editDate)
    writeBoolean(message.isOutgoing)
    writeOptional(message.sender) { sender ->
        writeLong(sender.id)
        writeBoolean(sender.isChat)
        writeText(sender.name)
    }
    writeText(message.text.text)
    writeInt(message.text.entities.size)
    for (entity in message.text.entities) {
        writeInt(entity.offset)
        writeInt(entity.length)
        val name = entity.type.toEntityName()
        writeText(name.name)
        writeOptional(name.text) { writeText(it) }
        writeOptional(name.number) { writeLong(it) }
    }
    writeOptional(message.replyToMessageId) { writeLong(it) }
    writeOptional(message.forwardedFrom) { writeText(it) }
    writeLong(message.forwardDate)
    writeOptional(message.media) { media ->
        writeInt(media.type.ordinal)
        writeText(media.fileName)
        writeLong(media.size)
        writeOptional(media.path) { writeText(it) }
        writeOptional(media.skipped) { writeInt(it.ordinal) }
    }
    writeOptional(message.summary) { writeText(it) }
    writeOptional(message.service) { writeText(it) }
}

private fun DataInputStream.readMessage(): ExportMessage {
    val id = readLong()
    val date = readLong()
    val editDate = readLong()
    val isOutgoing = readBoolean()
    val sender = readOptional { ExportSender(readLong(), readBoolean(), readText()) }
    val text = readText()
    val entities =
        List(readInt()) {
            val offset = readInt()
            val length = readInt()
            val name = EntityName(readText(), readOptional { readText() }, readOptional { readLong() })
            TextEntity(offset, length, name.toEntityType())
        }
    return ExportMessage(
        id = id,
        date = date,
        editDate = editDate,
        sender = sender,
        isOutgoing = isOutgoing,
        text = FormattedText(text, entities.toImmutableList()),
        replyToMessageId = readOptional { readLong() },
        forwardedFrom = readOptional { readText() },
        forwardDate = readLong(),
        media =
            readOptional {
                ExportMedia(
                    type = ExportMediaType.entries[readInt()],
                    fileName = readText(),
                    size = readLong(),
                    path = readOptional { readText() },
                    skipped = readOptional { ExportSkipReason.entries[readInt()] },
                )
            },
        summary = readOptional { readText() },
        service = readOptional { readText() },
    )
}

private inline fun <T : Any> DataOutputStream.writeOptional(
    value: T?,
    writeValue: DataOutputStream.(T) -> Unit,
) {
    writeBoolean(value != null)
    if (value != null) writeValue(value)
}

private inline fun <T : Any> DataInputStream.readOptional(readValue: DataInputStream.() -> T): T? =
    if (readBoolean()) readValue() else null

/** Unlike `writeUTF`, any length, and emoji as they are in UTF-8. */
private fun DataOutputStream.writeText(text: String) {
    val bytes = text.encodeToByteArray()
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readText(): String {
    val bytes = ByteArray(readInt())
    readFully(bytes)
    return bytes.decodeToString()
}
