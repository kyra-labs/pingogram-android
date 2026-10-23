package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSender
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `messages.html` as [HtmlExportWriter] writes it: self-contained, escaped, in the messages' own directions. */
class HtmlExportWriterTest {
    private fun export(
        vararg messages: ExportMessage,
        exported: Set<Long> = messages.map { it.id }.toSet(),
    ): String {
        val out = StringBuilder()
        val writer = HtmlExportWriter(out, TestExportTexts) { it in exported }
        writer.begin(testChat, exportedAt = 1_700_000_000, messageCount = messages.size)
        messages.forEach(writer::message)
        writer.end()
        return out.toString()
    }

    @Test
    fun `a page that loads nothing and runs nothing, the chat's name escaped`() {
        val html = export(exportMessage(id = 1))

        assertTrue(html.startsWith("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">"), html)
        assertTrue(html.contains("<title dir=\"auto\">Ada &amp; &lt;Friends&gt;</title>"), html)
        assertTrue(html.contains("<p>Exported from Pingogram on 2023-11-14, 22:13:20 · 1 messages</p>"), html)
        assertFalse(html.contains("<script"), html)
        assertFalse(Regex("(src|href)=\"(https?:)?//").containsMatchIn(html), html)
        assertTrue(html.endsWith("</main>\n</body>\n</html>\n"), html)
    }

    @Test
    fun `escapes what people wrote, in its own direction`() {
        val html = export(exportMessage(id = 1, text = text("<b>\"x\" & 'y'</b> שלום 👋🏽")))

        assertTrue(
            html.contains(
                "<div class=\"text\" dir=\"auto\">&lt;b&gt;&quot;x&quot; &amp; &#39;y&#39;&lt;/b&gt; שלום 👋🏽</div>",
            ),
            html,
        )
    }

    @Test
    fun `formats entities, nested, and links only http, https and tg links`() {
        val html =
            export(
                exportMessage(
                    id = 1,
                    text =
                        text(
                            "bold italic example.org evil code",
                            TextEntity(0, 11, TextEntityType.Bold),
                            TextEntity(5, 6, TextEntityType.Italic),
                            TextEntity(12, 11, TextEntityType.Url),
                            TextEntity(24, 4, TextEntityType.TextUrl("javascript:alert(1)")),
                            TextEntity(29, 4, TextEntityType.Code),
                        ),
                ),
            )

        assertTrue(
            html.contains(
                "<strong>bold <em>italic</em></strong> " +
                    "<a href=\"http://example.org\" rel=\"noreferrer\">example.org</a> evil <code>code</code>",
            ),
            html,
        )
    }

    @Test
    fun `a day heading per day, the sender's name per run of their messages`() {
        val day = 86_400L
        val html =
            export(
                exportMessage(id = 1, date = 1_700_000_000),
                exportMessage(id = 2, date = 1_700_000_060),
                exportMessage(id = 3, date = 1_700_000_120, sender = ExportSender(7, isChat = false, name = "Grace")),
                exportMessage(id = 4, date = 1_700_000_000 + day),
            )

        assertEquals(2, Regex("<h2 class=\"day\">").findAll(html).count(), html)
        assertEquals(2, Regex("<p class=\"from\" dir=\"auto\">Ada</p>").findAll(html).count(), html)
        assertEquals(1, Regex("<p class=\"from\" dir=\"auto\">Grace</p>").findAll(html).count(), html)
    }

    @Test
    fun `service messages, replies, forwards and edits`() {
        val html =
            export(
                exportMessage(id = 1, service = "Ada joined the group", text = text("")),
                exportMessage(id = 2, replyTo = 1, forwardedFrom = "Grace <3", editDate = 1_700_001_000),
                exportMessage(id = 3, replyTo = 99),
            )

        assertTrue(html.contains("<p class=\"service\" id=\"m1\" dir=\"auto\">Ada joined the group</p>"), html)
        assertTrue(html.contains("<a href=\"#m1\">In reply to this message</a>"), html)
        assertTrue(html.contains("In reply to a message not in this export"), html)
        assertTrue(html.contains("Forwarded from Grace &lt;3"), html)
        assertTrue(html.contains(" · edited</p>"), html)
    }

    @Test
    fun `media copied shows or links to its file, media not copied says why`() {
        val html =
            export(
                exportMessage(
                    id = 1,
                    media = ExportMedia(ExportMediaType.Photo, "", 10, "media/1_photo 1.jpg", null),
                ),
                exportMessage(
                    id = 2,
                    media = ExportMedia(ExportMediaType.Document, "Größe.pdf", 10, "media/2_Größe.pdf", null),
                ),
                exportMessage(
                    id = 3,
                    media = ExportMedia(ExportMediaType.Video, "", 10, null, ExportSkipReason.TooLarge),
                ),
                exportMessage(
                    id = 4,
                    media = ExportMedia(ExportMediaType.Sticker, "", 0, null, ExportSkipReason.NotChosen),
                ),
            )

        assertTrue(html.contains("<img src=\"media/1_photo%201.jpg\" alt=\"Photo\""), html)
        assertTrue(html.contains("<a href=\"media/2_Gr%C3%B6%C3%9Fe.pdf\" dir=\"auto\">Größe.pdf</a>"), html)
        assertTrue(html.contains("Video not exported: larger than the size limit"), html)
        assertTrue(html.contains("Sticker not exported"), html)
    }

    @Test
    fun `an empty chat says so`() {
        val html = export()

        assertTrue(html.contains("<p class=\"service\">No messages</p>"), html)
    }
}
