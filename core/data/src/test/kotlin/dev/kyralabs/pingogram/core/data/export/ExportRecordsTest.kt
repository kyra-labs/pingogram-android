package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The temporary pages of an export read back as they were written. */
class ExportRecordsTest {
    @Test
    fun `a page comes back field by field`() {
        val page =
            listOf(
                exportMessage(
                    id = 3,
                    text = text("😀 ${"long ".repeat(20_000)}", TextEntity(0, 2, TextEntityType.TextUrl("tg://x"))),
                    replyTo = 1,
                    forwardedFrom = "Grace",
                    editDate = 9,
                    media = ExportMedia(ExportMediaType.VoiceNote, "", 12, "media/3_voice.ogg", null),
                    summary = "Location",
                ),
                exportMessage(
                    id = 2,
                    sender = null,
                    isOutgoing = true,
                    text = text(""),
                    service = "Ada joined the group",
                    media = ExportMedia(ExportMediaType.Photo, "x.jpg", 0, null, ExportSkipReason.SelfDestructing),
                ),
            )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writePage(page) }

        val read = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readPage() }

        assertEquals(page, read)
    }
}
