package dev.kyralabs.pingogram.core.ui.theme

import dev.kyralabs.pingogram.core.ui.theme.mcu.quantizerTestImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A wallpaper's seed: Material's pick for a photo, its grey for a colorless one, the preferred color of a fill. */
class WallpaperSeedTest {
    @Test
    fun `a photo's seed is the color material-color-utilities picks`() {
        // Celebi then Score on the same image gave this in material-color-utilities 0.4.0 (ScoreTest).
        assertEquals(0xFF1FF526.toInt(), wallpaperSeed(quantizerTestImage()))
    }

    @Test
    fun `a black and white photo gives its own grey, not Google Blue`() {
        val greys = listOf(0xFF202020.toInt(), 0xFF9A9A9A.toInt())
        val pixels = IntArray(64 * 64) { greys[it % 2] }

        assertTrue(wallpaperSeed(pixels) in greys)
    }

    @Test
    fun `a photo without pixels gives Google Blue`() {
        assertEquals(0xFF4285F4.toInt(), wallpaperSeed(IntArray(0)))
    }

    @Test
    fun `a fill gives the color Score prefers among its own, greys included`() {
        assertEquals(0xFF2156C4.toInt(), fillSeed(listOf(0xFF2156C4.toInt())))
        assertEquals(0xFF808080.toInt(), fillSeed(listOf(0xFF808080.toInt())))
        assertEquals(0xFF4285F4.toInt(), fillSeed(emptyList()))
    }
}
