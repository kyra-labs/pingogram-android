package dev.kyralabs.pingogram.feature.media.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which side of a page a tap is near ([sideOf]): the outer 15% of each side, the end mirrored right to left. */
class SideTapTest {
    @Test
    fun `left to right, the right side is the end and the left the start`() {
        assertEquals(true, sideOf(x = 950f, width = 1_000, rtl = false))
        assertEquals(false, sideOf(x = 50f, width = 1_000, rtl = false))
    }

    @Test
    fun `right to left, the left side is the end`() {
        assertEquals(true, sideOf(x = 50f, width = 1_000, rtl = true))
        assertEquals(false, sideOf(x = 950f, width = 1_000, rtl = true))
    }

    @Test
    fun `the middle is neither side`() {
        assertEquals(null, sideOf(x = 500f, width = 1_000, rtl = false))
        assertEquals(null, sideOf(x = 151f, width = 1_000, rtl = false))
    }
}
