package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A photo's dim and wash as one color filter on its draw (`docs/PERFORMANCE.md`, "Overdraw"): the filtered photo has
 * the pixels the photo had with the scrim and the wash drawn over it, so folding them into its draw changes nothing.
 */
class DimAndWashTest {
    @Test
    fun `the matrix gives what drawing the dim and the wash over the photo gives`() {
        val cases =
            PHOTO_PIXELS.flatMap { photo ->
                listOf(0.2f, 0.5f, 1f).flatMap { dim -> listOf(Color.Unspecified, WASH).map { Triple(photo, dim, it) } }
            }
        for ((photo, dim, wash) in cases) {
            val matrix = requireNotNull(dimAndWashMatrix(SCRIM, dim, wash))
            val drawn = SCRIM.copy(alpha = dim).compositeOver(photo)
            val expected = if (wash == Color.Unspecified) drawn else wash.compositeOver(drawn)
            assertSameColor(expected, matrix.applyTo(photo), "photo $photo dim $dim wash $wash")
        }
    }

    @Test
    fun `the wash alone is the wash over the photo`() {
        val matrix = requireNotNull(dimAndWashMatrix(SCRIM, dim = 0f, wash = WASH))
        for (photo in PHOTO_PIXELS) assertSameColor(WASH.compositeOver(photo), matrix.applyTo(photo), "$photo")
    }

    @Test
    fun `nothing to fold gives no filter`() {
        assertNull(dimAndWashMatrix(SCRIM, dim = 0f, wash = Color.Unspecified))
        assertNull(dimAndWashMatrix(SCRIM, dim = 0f, wash = Color.Transparent))
    }

    /** [this] matrix applied to [color], as Android applies a color matrix: channels and offsets in 0 to 255. */
    private fun ColorMatrix.applyTo(color: Color): Color {
        val v = values
        val channels = floatArrayOf(color.red, color.green, color.blue, color.alpha).map { it * MAX }

        fun row(index: Int) =
            ((0 until CHANNELS).sumOf { (v[index * ROW + it] * channels[it]).toDouble() } + v[index * ROW + CHANNELS])
                .toFloat() / MAX
        return Color(red = row(0), green = row(1), blue = row(2), alpha = row(CHANNELS - 1))
    }

    private fun assertSameColor(
        expected: Color,
        actual: Color,
        message: String,
    ) {
        assertEquals(expected.red, actual.red, TOLERANCE, message)
        assertEquals(expected.green, actual.green, TOLERANCE, message)
        assertEquals(expected.blue, actual.blue, TOLERANCE, message)
        assertEquals(expected.alpha, actual.alpha, TOLERANCE, message)
    }

    private companion object {
        val SCRIM = Color.Black
        val WASH = Color(0xFF1D1B20).copy(alpha = 0.75f)
        val PHOTO_PIXELS = listOf(Color.White, Color.Black, Color(0xFFB4744B), Color(0xFF2156C4), Color(0xFF80C080))
        const val MAX = 255f
        const val ROW = 5
        const val CHANNELS = 4

        /** One step of 8-bit color: Compose keeps an sRGB color in 8 bits a channel, so each side rounds to a step. */
        const val TOLERANCE = 1.01f / 255f
    }
}
