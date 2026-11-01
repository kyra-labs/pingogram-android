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
        val text = TdApi.InputMessageText(TdApi.FormattedText("Tabs\tand\nlines, emoji 🎉", emptyArray()), null, false)
        val events =
            listOf(
                DemoEvent.Started(1_800_000_000),
                DemoEvent.Call(1_800_000_100, TdApi.SendMessage(1, null, null, null, null, text)),
                DemoEvent.Call(1_800_000_200, TdApi.SetPollAnswer(-200_001, 3L shl 20, intArrayOf(0, 2))),
                DemoEvent.Call(1_800_000_300, TdApi.DeleteMessages(1, longArrayOf(4, 5), true)),
                DemoEvent.Call(1_800_000_400, TdApi.AddChatToList(1, TdApi.ChatListArchive())),
            )
        val file = dir.resolve("demo/journal.txt")
        val journal = DemoJournal(FileDemoJournalStorage(file))

        events.forEach(journal::append)

        assertEquals(events, DemoJournal(FileDemoJournalStorage(file)).read())
    }

    @Test
    fun `TDLib's objects come back from the codec field for field, nested arrays and nulls included`() {
        val keyboard =
            TdApi.ReplyMarkupInlineKeyboard(
                arrayOf(
                    arrayOf(
                        TdApi.InlineKeyboardButton(
                            "Open",
                            0,
                            null,
                            TdApi.InlineKeyboardButtonTypeUrl("https://example.org"),
                        ),
                    ),
                ),
                false,
            )
        val function =
            TdApi.SendMessage(
                -200_001,
                null,
                TdApi.InputMessageReplyToMessage(1L shl 20, null, 0, ""),
                TdApi.MessageSendOptions().apply {
                    schedulingState =
                        TdApi.MessageSchedulingStateSendAtDate(1_900_000_000, 0)
                },
                keyboard,
                TdApi.InputMessageVoiceNote(
                    TdApi.InputVoiceNote(TdApi.InputFileLocal("/data/voice.ogg"), 3, byteArrayOf(1, 2, 3)),
                    null,
                    null,
                ),
            )

        val copy = DemoCodec.decode<TdApi.SendMessage>(DemoCodec.encode(function))

        assertTrue(DemoCodec.encode(copy).contentEquals(DemoCodec.encode(function)))
        val voice = (copy.inputMessageContent as TdApi.InputMessageVoiceNote).voiceNote
        assertEquals("/data/voice.ogg", (voice.voiceNote as TdApi.InputFileLocal).path)
        assertTrue(voice.waveform.contentEquals(byteArrayOf(1, 2, 3)))
        val button = (copy.replyMarkup as TdApi.ReplyMarkupInlineKeyboard).rows[0][0]
        assertEquals("https://example.org", (button.type as TdApi.InlineKeyboardButtonTypeUrl).url)
    }

    @Test
    fun `a line cut short by a crash is left out`() {
        val file = dir.resolve("journal.txt").apply { writeText("started\t5\ncall\t6\tAAAA\n") }

        assertEquals(listOf(DemoEvent.Started(5)), DemoJournal(FileDemoJournalStorage(file)).read())
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
