package dev.kyralabs.pingogram.core.data.attachment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The size math of photos and thumbnails: downscaling, EXIF orientation and the decoder's sample size. */
class PhotoScalingTest {
    @Test
    fun `EXIF orientations turn and mirror as TIFF defines them`() {
        val orientations = (0..9).map { Orientation.fromExif(it) }

        assertEquals(
            listOf(
                Orientation(0, false), // 0: not a TIFF value
                Orientation(0, false), // 1: normal
                Orientation(0, true), // 2: mirrored
                Orientation(180, false), // 3: upside down
                Orientation(180, true), // 4: mirrored vertically
                Orientation(270, true), // 5: transposed
                Orientation(90, false), // 6: turned clockwise
                Orientation(90, true), // 7: transversed
                Orientation(270, false), // 8: turned counterclockwise
                Orientation(0, false), // 9: not a TIFF value
            ),
            orientations,
        )
    }

    @Test
    fun `a large photo is scaled down to 2560 pixels on its longer side, keeping its aspect ratio`() {
        val plan = photoPlan(4000, 3000, Orientation.Normal)

        assertEquals(listOf(2560, 1920), listOf(plan.width, plan.height))
    }

    @Test
    fun `a small photo keeps its size, and is never enlarged`() {
        val plan = photoPlan(1200, 900, Orientation.Normal)

        assertEquals(listOf(1, 1200, 900), listOf(plan.sampleSize, plan.width, plan.height))
    }

    @Test
    fun `a photo turned a quarter is planned upright, its sides swapped`() {
        // A portrait photo from a phone camera, stored landscape with orientation 6.
        val plan = photoPlan(4000, 3000, Orientation.fromExif(6))

        assertEquals(listOf(1920, 2560), listOf(plan.width, plan.height))
        assertEquals(Orientation(90, mirrored = false), plan.orientation)
    }

    @Test
    fun `the sample size halves the decoded image while it stays at least as large as the photo`() {
        // 8160 x 6120 (50 MP) → 2560 x 1920: 1/2 keeps 4080 x 3060, 1/4 would be 2040 x 1530, too small.
        assertEquals(2, photoPlan(8160, 6120, Orientation.Normal).sampleSize)
        // 12000 x 9000 → 2560 x 1920: 1/4 keeps 3000 x 2250.
        assertEquals(4, photoPlan(12000, 9000, Orientation.Normal).sampleSize)
        // Turned: the stored height becomes the width, and is compared with it.
        assertEquals(2, photoPlan(6120, 8160, Orientation.fromExif(8)).sampleSize)
    }

    @Test
    fun `a panorama keeps at least one pixel on its short side`() {
        val plan = photoPlan(100_000, 10, Orientation.Normal)

        assertEquals(listOf(2560, 1), listOf(plan.width, plan.height))
    }

    @Test
    fun `thumbnails fit 320 pixels`() {
        assertEquals(320 to 180, fitWithin(1920, 1080, MAX_THUMBNAIL_SIDE))
        assertEquals(180 to 320, fitWithin(1080, 1920, MAX_THUMBNAIL_SIDE))
        assertEquals(200 to 100, fitWithin(200, 100, MAX_THUMBNAIL_SIDE))
    }
}
