package dev.kyralabs.pingogram.feature.chat.ui.content

import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ContentKindTest {
    private fun document(
        fileName: String,
        mimeType: String,
    ) = Document(fileName, mimeType, minithumbnail = null, thumbnail = null, file = FileRef(1, 0))

    @ParameterizedTest
    @CsvSource(
        "report.pdf, application/pdf, Pdf",
        "report.PDF, application/octet-stream, Pdf",
        "photos.zip, application/zip, Archive",
        "backup.tar.gz, application/octet-stream, Archive",
        "scan.heic, image/heic, Image",
        "picture.png, application/octet-stream, Image",
        "song.flac, audio/flac, Audio",
        "voice.opus, application/octet-stream, Audio",
        "clip.mkv, video/x-matroska, Video",
        "notes.txt, text/plain, Other",
        "README, application/octet-stream, Other",
        "'', '', Other",
    )
    fun `documents get an icon by MIME type, then by extension`(
        fileName: String,
        mimeType: String,
        kind: String,
    ) {
        assertEquals(DocumentKind.valueOf(kind), documentKind(document(fileName, mimeType)))
    }

    @Test
    fun `stickers, animated emoji, dice and video messages are bare`() {
        val samples = ContentPreviewData
        assertTrue(samples.sticker(StickerFormat.Webp).isBare)
        assertTrue(MessageContent.AnimatedEmoji("🎉", sticker = null).isBare)
        assertTrue(MessageContent.Dice("🎲", value = 4).isBare)
        assertTrue(samples.videoNote(minithumbnail = null, isViewed = true).isBare)
        assertFalse(samples.voiceNote(isListened = true).isBare)
        assertFalse(samples.photo(minithumbnail = null).isBare)
        assertFalse(MessageContent.Expired(ExpiredMedia.Photo).isBare)
    }

    @Test
    fun `photos, videos and GIFs are edge to edge`() {
        val samples = ContentPreviewData
        assertTrue(samples.photo(minithumbnail = null).isEdgeToEdge)
        assertTrue(samples.video(minithumbnail = null).isEdgeToEdge)
        assertTrue(samples.animation(minithumbnail = null).isEdgeToEdge)
        assertFalse(samples.sticker(StickerFormat.Webp).isEdgeToEdge)
        assertFalse(samples.document("a.pdf", "application/pdf").isEdgeToEdge)
        assertFalse(MessageContent.Text(FormattedText.Empty, linkPreview = null).isEdgeToEdge)
    }
}
