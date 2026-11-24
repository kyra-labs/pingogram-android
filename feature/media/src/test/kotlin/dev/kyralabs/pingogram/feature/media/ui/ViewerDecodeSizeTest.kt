package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [viewerDecodeSize]: a full photo is decoded at most at twice the window's longer side, and at most 2048 px unless
 * the page shows it larger at rest; never larger than its own pixels (`docs/audit/performance-review-2026-11.md`, M5).
 */
class ViewerDecodeSizeTest {
    @Test
    fun `a photo larger than twice the window is scaled down, its aspect ratio kept`() {
        assertEquals(IntSize(2_000, 1_500), viewerDecodeSize(2_560, 1_920, IntSize(1_000, 600)))
    }

    @Test
    fun `a photo within twice the window keeps its own size`() {
        assertEquals(IntSize(1_280, 960), viewerDecodeSize(1_280, 960, IntSize(1_080, 2_400)))
    }

    @Test
    fun `a portrait photo is limited by its height`() {
        assertEquals(IntSize(1_000, 2_000), viewerDecodeSize(2_000, 4_000, IntSize(500, 1_000)))
    }

    @Test
    fun `on a phone, a photo larger than the cap is decoded at the cap`() {
        assertEquals(IntSize(2_048, 1_536), viewerDecodeSize(2_560, 1_920, IntSize(1_440, 3_120)))
    }

    @Test
    fun `a photo the page shows larger than the cap at rest is decoded at that size`() {
        // A tablet's landscape window: the photo fills its height, 1600 px, so 2133 px wide.
        assertEquals(IntSize(2_133, 1_600), viewerDecodeSize(2_560, 1_920, IntSize(2_560, 1_600)))
    }

    @Test
    fun `without a window size, the photo's own size`() {
        assertEquals(IntSize(2_560, 1_920), viewerDecodeSize(2_560, 1_920, IntSize.Zero))
    }
}
