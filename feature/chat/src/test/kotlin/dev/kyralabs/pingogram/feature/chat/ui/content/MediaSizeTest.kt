package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MediaSizeTest {
    private fun display(
        width: Int,
        height: Int,
    ) = mediaDisplaySize(width, height, maxWidth = 280.dp, maxHeight = 360.dp, minSize = 120.dp)

    @Test
    fun `a landscape photo takes the full width`() {
        assertEquals(DpSize(280.dp, 210.dp), display(1280, 960))
    }

    @Test
    fun `a portrait photo is limited by the height`() {
        assertEquals(DpSize(270.dp, 360.dp), display(960, 1280))
    }

    @Test
    fun `small photos are scaled up to fit`() {
        assertEquals(DpSize(280.dp, 280.dp), display(100, 100))
    }

    @Test
    fun `panoramas and tall screenshots are kept at the minimum size and cropped`() {
        assertEquals(DpSize(280.dp, 120.dp), display(4000, 400))
        assertEquals(DpSize(120.dp, 360.dp), display(400, 4000))
    }

    @Test
    fun `without a known size it is a square`() {
        assertEquals(DpSize(280.dp, 280.dp), display(0, 0))
        assertEquals(DpSize(280.dp, 280.dp), display(1280, 0))
    }

    @Test
    fun `the minimum gives way to a smaller maximum`() {
        assertEquals(
            DpSize(100.dp, 360.dp),
            mediaDisplaySize(400, 4000, maxWidth = 100.dp, maxHeight = 360.dp, minSize = 120.dp),
        )
    }

    @Test
    fun `stickers fit in their square keeping their shape`() {
        assertEquals(DpSize(160.dp, 160.dp), fitInSquare(512, 512, 160.dp))
        assertEquals(DpSize(160.dp, 80.dp), fitInSquare(512, 256, 160.dp))
        assertEquals(DpSize(80.dp, 160.dp), fitInSquare(256, 512, 160.dp))
        assertEquals(DpSize(160.dp, 160.dp), fitInSquare(0, 0, 160.dp))
    }

    private fun size(
        type: String,
        width: Int,
        height: Int,
    ) = PhotoSize(type, width, height, FileRef(id = width, size = 0))

    private val sizes = listOf(size("m", 320, 240), size("x", 800, 600), size("y", 1280, 960), size("w", 2560, 1920))

    @Test
    fun `picks the smallest size that covers the box`() {
        assertEquals("x", selectPhotoSize(sizes, 800, 600)?.type)
        assertEquals("y", selectPhotoSize(sizes, 840, 630)?.type)
        assertEquals("m", selectPhotoSize(sizes, 120, 90)?.type)
    }

    @Test
    fun `picks the largest size when none covers the box`() {
        assertEquals("w", selectPhotoSize(sizes, 4000, 3000)?.type)
    }

    @Test
    fun `a cropped box needs both sides covered`() {
        // A panorama strip of 840 × 360 px cut from a 4:3 photo needs a size at least 360 px tall and 840 px wide.
        assertEquals("y", selectPhotoSize(sizes, 840, 360)?.type)
        assertEquals("w", selectPhotoSize(sizes, 360, 1080)?.type)
    }

    @Test
    fun `does not depend on the order of the sizes`() {
        assertEquals("x", selectPhotoSize(sizes.reversed(), 800, 600)?.type)
    }

    @Test
    fun `sizes without dimensions are a last resort`() {
        val unknown = size("i", 0, 0)

        assertEquals("m", selectPhotoSize(listOf(unknown, sizes[0]), 800, 600)?.type)
        assertEquals("i", selectPhotoSize(listOf(unknown), 800, 600)?.type)
        assertNull(selectPhotoSize(emptyList(), 800, 600))
    }

    @Test
    fun `the largest size gives the aspect ratio`() {
        assertEquals("w", largestPhotoSize(sizes.shuffled())?.type)
        assertNull(largestPhotoSize(emptyList()))
    }

    @Test
    fun `only image thumbnails are pictures`() {
        val formats = ThumbnailFormat.entries.filter { Thumbnail(it, 1, 1, FileRef(1, 0)).asPicture() != null }

        assertEquals(
            listOf(ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp, ThumbnailFormat.Gif),
            formats,
        )
    }
}
