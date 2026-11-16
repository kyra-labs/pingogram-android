package dev.kyralabs.pingogram.feature.chat.ui.pinned

import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Video
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** What the pinned messages bar shows before its text (the owner's board P1). */
class PinnedThumbnailTest {
    private val caption = FormattedText("", persistentListOf())

    private fun photo(hasSpoiler: Boolean = false) =
        MessageContent.Photo(
            Photo(
                persistentListOf(
                    PhotoSize("s", 90, 67, FileRef(1, 0)),
                    PhotoSize("m", 320, 240, FileRef(2, 0)),
                    PhotoSize("x", 800, 600, FileRef(3, 0)),
                ),
                null,
            ),
            caption,
            hasSpoiler,
            false,
            false,
        )

    private fun video(format: ThumbnailFormat) =
        MessageContent.Video(
            Video(42, 1280, 720, null, Thumbnail(format, 320, 180, FileRef(9, 0)), true, FileRef(10, 0)),
            caption,
            false,
            false,
            false,
        )

    @Test
    fun `a photo takes its smallest size large enough for the tile`() {
        assertEquals(PinnedThumbnail.Picture(2), photo().pinnedThumbnail())
    }

    @Test
    fun `a photo behind a spoiler shows no picture`() {
        assertNull(photo(hasSpoiler = true).pinnedThumbnail())
    }

    @Test
    fun `a video shows its still picture with a play symbol, and none where its thumbnail is a video`() {
        assertEquals(PinnedThumbnail.Picture(9, isVideo = true), video(ThumbnailFormat.Jpeg).pinnedThumbnail())
        assertNull(video(ThumbnailFormat.Mpeg4).pinnedThumbnail())
    }

    @Test
    fun `a file shows the file tile, and text none`() {
        val document = Document("notes.pdf", "application/pdf", null, null, FileRef(5, 0))
        assertEquals(PinnedThumbnail.File, MessageContent.Document(document, caption).pinnedThumbnail())
        assertNull(MessageContent.Text(FormattedText("Hi", persistentListOf()), null).pinnedThumbnail())
    }
}
