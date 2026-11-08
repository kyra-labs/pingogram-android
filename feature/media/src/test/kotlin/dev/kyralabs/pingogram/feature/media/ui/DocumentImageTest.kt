package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A picture sent as a file in the viewer: its own file as the photo, in its thumbnail's shape. */
class DocumentImageTest {
    @Test
    fun `a picture sent as a file shows as an image of its file, shaped like its thumbnail`() {
        val media =
            file(
                "image/png",
                thumbnail = Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef(2, 900)),
            ).toViewerMedia()

        val image = media as ViewerMedia.Image
        assertEquals(FILE_ID, image.full.file.id)
        assertEquals(listOf(FILE_ID), image.followedFileIds)
        assertEquals(320f / 180f, image.width.toFloat() / image.height, 0.01f)
    }

    @Test
    fun `other files, and pictures without a thumbnail, are not shown`() {
        assertNull(file("application/pdf", Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef(2, 900))).toViewerMedia())
        assertNull(file("image/jpeg", thumbnail = null).toViewerMedia())
        assertNull(file("image/heic", Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef(2, 900))).toViewerMedia())
    }

    private fun file(
        mimeType: String,
        thumbnail: Thumbnail?,
    ) = MessageContent.Document(
        document = Document("picture", mimeType, null, thumbnail, FileRef(FILE_ID, 4_000_000)),
        caption = FormattedText("", persistentListOf()),
    )

    private companion object {
        const val FILE_ID = 9
    }
}
