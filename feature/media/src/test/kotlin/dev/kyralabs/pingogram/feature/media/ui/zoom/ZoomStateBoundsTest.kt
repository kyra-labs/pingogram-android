package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [ZoomState] when its bounds change under a zoomed photo: the phone rotated, or the window was resized. */
class ZoomStateBoundsTest {
    /** A 1000 × 2000 page with a 4:3 photo fitted across its width: 1000 × 750. */
    private val portrait = ZoomBounds(container = Size(1000f, 2000f), content = Size(1000f, 750f))

    /** The same photo after rotating: a 2000 × 1000 page it fits by height, 1333 × 1000. */
    private val landscape = ZoomBounds(container = Size(2000f, 1000f), content = Size(4000f / 3, 1000f))

    @Test
    fun `after rotating, the same part of the zoomed photo stays in the middle, within the new page`() {
        val zoom = ZoomState()
        zoom.bounds = portrait
        // At 4×, panned to the photo's right edge: 4000 wide in a 1000 wide page, 1500 from the center.
        zoom.transform(centroid = Offset.Zero, scaleChange = 4f, panChange = Offset(-1500f, 0f))
        assertEquals(Offset(-1500f, 0f), zoom.zoom.pan)

        zoom.bounds = landscape

        // 5333 wide in a 2000 wide page: 1667 from the center at most, and 2000 to keep the same point.
        assertEquals(4f, zoom.zoom.scale)
        assertEquals(-5000f / 3, zoom.zoom.pan.x, DELTA)
        assertEquals(0f, zoom.zoom.pan.y, DELTA)
    }

    @Test
    fun `a fitted photo stays fitted`() {
        val zoom = ZoomState()
        zoom.bounds = portrait

        zoom.bounds = landscape

        assertEquals(Zoom.Fit, zoom.zoom)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
