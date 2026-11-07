package dev.kyralabs.pingogram.core.ui.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [gaussianBlur]: the blur placeholders get once, at the minithumbnail's own size, instead of a `RenderEffect` in every
 * frame (`docs/audit/performance-review-2026-11.md`, M1).
 */
class GaussianBlurTest {
    @Test
    fun `a plain picture stays as it is`() {
        val pixels = IntArray(WIDTH * HEIGHT) { GREY }

        val blurred = gaussianBlur(pixels, WIDTH, HEIGHT, SIGMA)

        assertTrue(blurred.all { it == GREY }) { blurred.toList().distinct().toString() }
    }

    @Test
    fun `a bright pixel spreads to its neighbours, evenly, and its light is kept`() {
        val pixels = IntArray(WIDTH * HEIGHT) { BLACK }
        pixels[CENTER] = WHITE

        val blurred = gaussianBlur(pixels, WIDTH, HEIGHT, SIGMA)

        val center = blurred[CENTER].red()
        val left = blurred[CENTER - 1].red()
        val right = blurred[CENTER + 1].red()
        val above = blurred[CENTER - WIDTH].red()
        assertTrue(center in 1 until CHANNEL) { "center $center" }
        assertTrue(left in 1 until center) { "left $left of $center" }
        assertEquals(left, right)
        assertEquals(left, above)
        // Rounding each pass to whole values loses or gains a little.
        val total = blurred.sumOf { it.red() }
        assertTrue(total in CHANNEL - LIGHT_TOLERANCE..CHANNEL + LIGHT_TOLERANCE) { "total $total" }
    }

    @Test
    fun `edges extend as they are, so an edge colour does not darken`() {
        val pixels = IntArray(WIDTH * HEIGHT) { index -> if (index % WIDTH < WIDTH / 2) BLACK else WHITE }

        val blurred = gaussianBlur(pixels, WIDTH, HEIGHT, SIGMA)

        for (y in 0 until HEIGHT) {
            assertEquals(0, blurred[y * WIDTH].red())
            assertEquals(CHANNEL, blurred[y * WIDTH + WIDTH - 1].red())
            assertEquals(OPAQUE, blurred[y * WIDTH + WIDTH / 2] ushr ALPHA_SHIFT)
        }
    }

    @Test
    fun `an empty picture is no picture`() {
        assertEquals(0, gaussianBlur(IntArray(0), 0, 0, SIGMA).size)
    }

    private fun Int.red(): Int = (this shr RED_SHIFT) and CHANNEL

    private companion object {
        const val WIDTH = 15
        const val HEIGHT = 11
        const val CENTER = HEIGHT / 2 * WIDTH + WIDTH / 2
        const val SIGMA = 1.37f
        const val CHANNEL = 0xFF
        const val OPAQUE = 0xFF
        const val RED_SHIFT = 16
        const val ALPHA_SHIFT = 24
        const val LIGHT_TOLERANCE = 12
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFF808080.toInt()
    }
}
