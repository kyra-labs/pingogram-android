package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemoTextAndJournalTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `the texts' marks become entities and are dropped from the text`() {
        val text = formattedText("Messages can be **bold**, __italic__ or `code`, with links like https://example.org")

        assertEquals("Messages can be bold, italic or code, with links like https://example.org", text.text)
        val entities = text.entities.map { text.text.substring(it.offset, it.offset + it.length) to it.type.javaClass }
        assertEquals(
            listOf(
                "bold" to TdApi.TextEntityTypeBold::class.java,
                "italic" to TdApi.TextEntityTypeItalic::class.java,
                "code" to TdApi.TextEntityTypeCode::class.java,
                "https://example.org" to TdApi.TextEntityTypeUrl::class.java,
            ),
            entities,
        )
    }

    @Test
    fun `every event comes back from the journal's file as it went in`() {
        val events =
            listOf(
                DemoEvent.Started(1_800_000_000),
                DemoEvent.Sent(1, 2L shl 20, 1_800_000_100, "Tabs\tand\nlines, emoji 🎉", 1L shl 20),
                DemoEvent.Read(1, 2L shl 20),
                DemoEvent.Reaction(1, 2, "❤", added = true),
                DemoEvent.Vote(-200_001, 3, listOf(0, 2)),
                DemoEvent.Deleted(1, listOf(4, 5)),
                DemoEvent.Pinned(1, pinned = true),
                DemoEvent.MarkedUnread(1, unread = false),
                DemoEvent.Muted(1, 3_600),
                DemoEvent.Archived(1, archived = true),
            )
        val file = dir.resolve("demo/journal.txt")
        val journal = DemoJournal(FileDemoJournalStorage(file))

        events.forEach(journal::append)

        assertEquals(events, DemoJournal(FileDemoJournalStorage(file)).read())
    }

    @Test
    fun `a line cut short by a crash is left out`() {
        val file = dir.resolve("journal.txt").apply { writeText("read\t1\t2\nsent\t1\n") }

        assertEquals(listOf(DemoEvent.Read(1, 2)), DemoJournal(FileDemoJournalStorage(file)).read())
    }

    @Test
    fun `the voice message is a WAV file of its length`() {
        val file = dir.resolve("voice.wav")

        writeWav(file, tones())

        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        // 16-bit mono at 16 kHz: two bytes a sample after the 44-byte header.
        assertTrue((bytes.size - 44) / 2 / 16_000 in VOICE_SECONDS - 1..VOICE_SECONDS)
    }
}
