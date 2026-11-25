package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.graphics.Canvas as AndroidCanvas
import coil3.size.Size as CoilSize

/** Fills' gradients and the blur of blurred photos. */
@RunWith(RobolectricTestRunner::class)
class WallpaperDrawingTest {
    @Test
    fun `a two-color fill runs from top to bottom, turned clockwise by its rotation`() {
        val down = draw(Wallpaper.Fill(1, persistentListOf(RED, BLUE)))
        assertEquals(Color.Red, down.at(SIDE / 2, 0))
        assertEquals(Color.Blue, down.at(SIDE / 2, SIDE - 1))

        // A quarter turn clockwise moves the top color to the right.
        val turned = draw(Wallpaper.Fill(1, persistentListOf(RED, BLUE), rotationDegrees = 90))
        assertEquals(Color.Red, turned.at(SIDE - 1, SIDE / 2))
        assertEquals(Color.Blue, turned.at(0, SIDE / 2))
    }

    @Test
    fun `one color is solid`() {
        assertEquals(Color.Blue, draw(Wallpaper.Fill(1, persistentListOf(BLUE))).at(SIDE / 3, SIDE / 2))
    }

    @Test
    fun `a blurred photo is shrunk to fit 450 pixels, then as far as its blur allows, and loses its hard edges`() =
        runTest {
            // Black left half, white right half.
            val photo = Bitmap.createBitmap(1800, 900, Bitmap.Config.ARGB_8888)
            AndroidCanvas(photo).apply {
                drawColor(android.graphics.Color.BLACK)
                drawRect(900f, 0f, 1800f, 900f, Paint().apply { color = android.graphics.Color.WHITE })
            }

            val blurred = WallpaperBlur.Photo.transform(photo, CoilSize.ORIGINAL)

            // 450 × 225, then halved: the photo's blur, 7.2 px there, is 3.6 px here.
            assertEquals(225 to 113, blurred.width to blurred.height)
            val edge = android.graphics.Color.red(blurred.getPixel(112, 56))
            assertTrue("the edge is gray, not a step: $edge", edge in 64..192)
            assertEquals(0, android.graphics.Color.red(blurred.getPixel(5, 56)))
        }

    private fun draw(fill: Wallpaper.Fill): ImageBitmap {
        val image = ImageBitmap(SIDE, SIDE)
        val size = Size(SIDE.toFloat(), SIDE.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
            drawRect(fillBrush(fill, size))
        }
        return image
    }

    private fun ImageBitmap.at(
        x: Int,
        y: Int,
    ): Color = toPixelMap()[x, y].let { Color(it.red.round(), it.green.round(), it.blue.round()) }

    // Gradients end on their colors only to within a step of interpolation.
    private fun Float.round() = if (this > HALF) 1f else 0f

    private companion object {
        const val SIDE = 100
        const val HALF = 0.5f
        const val RED = 0xFF0000
        const val BLUE = 0x0000FF
    }
}
