package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Vector wallpaper patterns: their shapes, transforms and view box, and the mask they decode to. */
@RunWith(RobolectricTestRunner::class)
class VectorPatternDecoderTest {
    @Test
    fun `reads gzipped shapes in the view box's units, with their groups' transforms`() {
        val pattern = readPattern(gzip(SVG).inputStream())

        assertEquals(100f, pattern.width)
        assertEquals(200f, pattern.height)
        val bounds = pattern.shapes.map { RectF().apply { it.computeBounds(this, true) } }
        // The rect, moved by its group; the circle; the path; the polygon. The unfilled ellipse is left out.
        assertEquals(4, bounds.size)
        assertEquals(RectF(20f, 30f, 40f, 50f), bounds[0])
        assertEquals(RectF(40f, 140f, 60f, 160f), bounds[1])
        assertEquals(RectF(0f, 0f, 10f, 10f), bounds[2])
        assertEquals(RectF(70f, 0f, 90f, 20f), bounds[3])
    }

    @Test
    fun `reads a plain, ungzipped pattern too`() {
        assertEquals(4, readPattern(SVG.byteInputStream()).shapes.size)
    }

    @Test
    fun `transforms apply in SVG's order`() {
        val matrix = transformOf("translate(10 0) scale(2)")
        val point = floatArrayOf(1f, 1f)
        matrix.mapPoints(point)
        // Scaled first, then moved: the list's last transform applies first.
        assertEquals(listOf(12f, 2f), point.toList())
    }

    @Test
    fun `decodes to a mask covering the requested size`() =
        runTest {
            val source = ImageSource(Buffer().write(gzip(SVG)), FileSystem.SYSTEM)
            val options = Options(ApplicationProvider.getApplicationContext(), size = Size(50, 100))
            val decoder =
                VectorPatternDecoder
                    .Factory()
                    .create(SourceFetchResult(source, null, DataSource.DISK), options, ImageLoader(options.context))
            val result = checkNotNull(decoder.decode())
            val bitmap = result.image.toBitmap()

            assertEquals(Bitmap.Config.ALPHA_8, bitmap.config)
            assertEquals(50 to 100, bitmap.width to bitmap.height)
            // Half size: the circle around (50, 150) is at (25, 75); the corner between shapes is empty.
            assertTrue(alpha(bitmap, 25, 75) > OPAQUE)
            assertEquals(0, alpha(bitmap, 45, 95))
        }

    private fun alpha(
        bitmap: Bitmap,
        x: Int,
        y: Int,
    ) = bitmap.getPixel(x, y) ushr ALPHA_SHIFT

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

    private companion object {
        const val OPAQUE = 200
        const val ALPHA_SHIFT = 24

        val SVG =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 200">
              <g transform="translate(10 20)"><rect x="10" y="10" width="20" height="20"/></g>
              <circle cx="50" cy="150" r="10"/>
              <path d="M0 0 L10 0 L10 10 Z"/>
              <polygon points="70,0 90,0 90,20 70,20"/>
              <ellipse cx="50" cy="50" rx="5" ry="5" fill="none"/>
            </svg>
            """.trimIndent()
    }
}
