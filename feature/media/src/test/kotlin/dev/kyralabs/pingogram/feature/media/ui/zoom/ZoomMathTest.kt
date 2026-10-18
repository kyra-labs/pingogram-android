package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZoomMathTest {
    /** A 1000 × 2000 page with a 4:3 photo fitted across its width: 1000 × 750. */
    private val bounds = ZoomBounds(container = Size(1000f, 2000f), content = Size(1000f, 750f))

    /** The same photo in a square page, which it fills at 4/3, less than a double tap zooms. */
    private val square = ZoomBounds(container = Size(1000f, 1000f), content = Size(1000f, 750f))

    private fun assertOffset(
        expected: Offset,
        actual: Offset,
    ) {
        assertEquals(expected.x, actual.x, DELTA)
        assertEquals(expected.y, actual.y, DELTA)
    }

    // Fitting

    @Test
    fun `a photo is fitted within the page, keeping its aspect ratio`() {
        assertEquals(Size(1000f, 750f), fittedSize(1280, 960, Size(1000f, 2000f)))
        assertEquals(Size(500f, 1000f), fittedSize(1000, 2000, Size(1000f, 1000f)))
    }

    @Test
    fun `a photo of unknown size fills the page`() {
        assertEquals(Size(1000f, 2000f), fittedSize(0, 0, Size(1000f, 2000f)))
    }

    // Bounds

    @Test
    fun `fitted, the photo cannot move`() {
        assertOffset(Offset.Zero, maxPan(bounds, scale = 1f))
    }

    @Test
    fun `zoomed, it moves as far as it overflows the page, on each side`() {
        // At 2x: 2000 × 1500, 500 over on each side horizontally, none vertically.
        assertOffset(Offset(500f, 0f), maxPan(bounds, scale = 2f))
        // At 4x: 4000 × 3000, over by 1500 and 500 on each side.
        assertOffset(Offset(1500f, 500f), maxPan(bounds, scale = 4f))
    }

    @Test
    fun `a pan is kept within bounds`() {
        assertOffset(Offset(500f, 0f), clampPan(Offset(800f, 300f), bounds, scale = 2f))
        assertOffset(Offset(-500f, 0f), clampPan(Offset(-800f, -300f), bounds, scale = 2f))
        assertOffset(Offset(200f, 0f), clampPan(Offset(200f, 0f), bounds, scale = 2f))
    }

    // Zooming around a point

    @Test
    fun `zooming around a point keeps it in place`() {
        val focus = Offset(200f, -100f)
        val pan = panAround(Offset.Zero, focus, from = 1f, to = 3f)
        // The point under the focus at 1x is the focus itself; at 3x it is drawn at pan + 3 × focus.
        assertOffset(focus, pan + focus * 3f)
    }

    @Test
    fun `zooming around the center keeps the pan's proportion`() {
        assertOffset(Offset(300f, 0f), panAround(Offset(150f, 0f), Offset.Zero, from = 2f, to = 4f))
    }

    // Double tap

    @Test
    fun `a double tap on a fitted photo zooms in around the tap`() {
        val target = doubleTapTarget(square, Zoom.Fit, tap = Offset(100f, 0f))

        assertEquals(DOUBLE_TAP_SCALE, target.scale)
        // 100 × (1 - 2.5) = -150, within the 750 the photo overflows by on each side.
        assertOffset(Offset(-150f, 0f), target.pan)
    }

    @Test
    fun `a double tap near an edge moves the photo no further than the edge allows`() {
        val target = doubleTapTarget(square, Zoom.Fit, tap = Offset(-600f, 0f))

        assertOffset(Offset(750f, 0f), target.pan)
    }

    @Test
    fun `a double tap zooms in as far as filling the page needs`() {
        // The 4:3 photo fills the portrait page at 2000 / 750.
        assertEquals(2000f / 750f, doubleTapTarget(bounds, Zoom.Fit, Offset.Zero).scale, DELTA)
    }

    @Test
    fun `a double tap zooms in no further than the largest zoom, and at least as far as a double tap does`() {
        // A wide panorama, 1000 × 200 in a 1000 × 2000 page, fills it at 10x, beyond the largest zoom.
        val panorama = ZoomBounds(container = Size(1000f, 2000f), content = Size(1000f, 200f))
        assertEquals(MAX_SCALE, doubleTapTarget(panorama, Zoom.Fit, Offset.Zero).scale)
        // A tall photo, 500 × 1000 in a 1000 × 1000 page, fills it at 2x, less than the double tap's zoom.
        val tall = ZoomBounds(container = Size(1000f, 1000f), content = Size(500f, 1000f))
        assertEquals(DOUBLE_TAP_SCALE, doubleTapTarget(tall, Zoom.Fit, Offset.Zero).scale)
    }

    @Test
    fun `a double tap on a zoomed photo fits it again`() {
        assertEquals(Zoom.Fit, doubleTapTarget(bounds, Zoom(3f, Offset(400f, 100f)), tap = Offset(10f, 10f)))
    }

    // Gestures

    @Test
    fun `a pinch zooms around its centroid`() {
        val zoom = transformed(Zoom.Fit, bounds, centroid = Offset(100f, 0f), scaleChange = 2f, panChange = Offset.Zero)

        assertEquals(2f, zoom.scale)
        assertOffset(Offset(-100f, 0f), zoom.pan)
    }

    @Test
    fun `a pinch stays between fitted and the largest zoom`() {
        assertEquals(MIN_SCALE, transformed(Zoom.Fit, bounds, Offset.Zero, 0.5f, Offset.Zero).scale)
        assertEquals(MAX_SCALE, transformed(Zoom(4f, Offset.Zero), bounds, Offset.Zero, 3f, Offset.Zero).scale)
    }

    @Test
    fun `a drag pans the zoomed photo, within bounds`() {
        val zoom = Zoom(2f, Offset.Zero)

        assertOffset(Offset(120f, 0f), transformed(zoom, bounds, Offset.Zero, 1f, Offset(120f, 80f)).pan)
        assertOffset(Offset(500f, 0f), transformed(zoom, bounds, Offset.Zero, 1f, Offset(900f, 0f)).pan)
    }

    @Test
    fun `zooming out pulls the photo back within bounds`() {
        val zoom = transformed(Zoom(4f, Offset(1500f, 500f)), bounds, Offset.Zero, 0.5f, Offset.Zero)

        assertEquals(2f, zoom.scale)
        assertOffset(Offset(500f, 0f), zoom.pan)
    }

    @Test
    fun `accessibility steps zoom around the center`() {
        val zoomedIn = steppedZoom(Zoom.Fit, bounds, step = 2f)
        assertEquals(Zoom(2f, Offset.Zero), zoomedIn)
        assertEquals(Zoom.Fit, steppedZoom(zoomedIn, bounds, step = 0.5f))
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
