package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportSender
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `messages.json` as [JsonExportWriter] writes it: the schema of `docs/adr/0021-chat-export.md`, escaped. */
class JsonExportWriterTest {
    private fun export(vararg messages: dev.kyralabs.pingogram.core.domain.model.ExportMessage): String {
        val out = StringBuilder()
        val writer = JsonExportWriter(out)
        writer.begin(testChat, exportedAt = 1_700_000_000, messageCount = messages.size)
        messages.forEach(writer::message)
        writer.end()
        return out.toString()
    }

    @Suppress("LongMethod") // The whole file as it is written.
    @Test
    fun `writes the chat, then each message with only the fields it has`() {
        val json =
            export(
                exportMessage(
                    id = 1,
                    date = 1_700_000_060,
                    text = text("Hi *you*", TextEntity(3, 5, TextEntityType.Bold)),
                    editDate = 1_700_000_120,
                ),
                exportMessage(
                    id = 2,
                    date = 1_700_000_180,
                    text = text(""),
                    sender = ExportSender(id = 7, isChat = true, name = "News"),
                    isOutgoing = true,
                    replyTo = 1,
                    media = ExportMedia(ExportMediaType.Photo, "", 2_048, "media/2_photo_2.jpg", null),
                ),
            )

        assertEquals(
            """
            {
              "format": "pingogram_chat_export",
              "version": 1,
              "exported_at": "2023-11-14T22:13:20Z",
              "chat": {
                "id": 100,
                "title": "Ada & <Friends>",
                "type": "group"
              },
              "message_count": 2,
              "messages": [
                {
                  "id": 1,
                  "date": "2023-11-14T22:14:20Z",
                  "date_unix": 1700000060,
                  "edited": "2023-11-14T22:15:20Z",
                  "edited_unix": 1700000120,
                  "from": {
                    "id": 42,
                    "type": "user",
                    "name": "Ada"
                  },
                  "outgoing": false,
                  "text": "Hi *you*",
                  "entities": [
                    {
                      "type": "bold",
                      "offset": 3,
                      "length": 5
                    }
                  ]
                },
                {
                  "id": 2,
                  "date": "2023-11-14T22:16:20Z",
                  "date_unix": 1700000180,
                  "from": {
                    "id": 7,
                    "type": "chat",
                    "name": "News"
                  },
                  "outgoing": true,
                  "reply_to_message_id": 1,
                  "media": {
                    "type": "photo",
                    "file": "media/2_photo_2.jpg",
                    "size": 2048
                  }
                }
              ]
            }

            """.trimIndent(),
            json,
        )
    }

    @Test
    fun `escapes quotes, backslashes, control characters and line separators, and keeps emoji and RTL text`() {
        val json = export(exportMessage(id = 1, text = text("\"a\\b\"\n\t\u0001  שלום 👋🏽")))

        assertTrue(json.contains("\"text\": \"\\\"a\\\\b\\\"\\n\\t\\u0001\\u2028 שלום 👋🏽\""), json)
    }

    @Test
    fun `names media that was not copied and why, with entity values and service texts`() {
        val json =
            export(
                exportMessage(
                    id = 1,
                    text = text("site", TextEntity(0, 4, TextEntityType.TextUrl("https://example.org"))),
                    media = ExportMedia(ExportMediaType.Document, "report.pdf", 0, null, ExportSkipReason.TooLarge),
                ),
                exportMessage(id = 2, text = text(""), service = "Ada joined the group", forwardedFrom = "Grace"),
            )

        assertTrue(json.contains("\"url\": \"https://example.org\""), json)
        assertTrue(json.contains("\"file_name\": \"report.pdf\",\n        \"not_exported\": \"too_large\""), json)
        assertTrue(json.contains("\"service\": \"Ada joined the group\""), json)
        assertTrue(json.contains("\"forwarded\": {\n        \"from\": \"Grace\","), json)
    }

    @Test
    fun `an empty chat has an empty list of messages`() {
        val json = export()

        assertTrue(json.endsWith("\"message_count\": 0,\n  \"messages\": []\n}\n"), json)
    }
}
