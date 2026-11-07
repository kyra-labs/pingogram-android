package dev.kyralabs.pingogram.core.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * [blurredMinithumbnail]: a minithumbnail decoded and blurred once at its own size, then the same bitmap for every
 * placeholder showing it, also from another copy of its bytes (a message mapped again).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlurredMinithumbnailTest {
    @Test
    fun `is blurred at the minithumbnail's own size`() {
        val minithumbnail = minithumbnail(Color.BLUE)

        val bitmap = blurredMinithumbnail(minithumbnail)

        assertNotNull(bitmap)
        assertEquals(WIDTH, bitmap!!.width)
        assertEquals(HEIGHT, bitmap.height)
    }

    @Test
    fun `is made once per minithumbnail, also from a copy of its bytes`() {
        val minithumbnail = minithumbnail(Color.RED)
        val copy = Minithumbnail(minithumbnail.width, minithumbnail.height, minithumbnail.jpeg.copyOf())

        assertSame(blurredMinithumbnail(minithumbnail), blurredMinithumbnail(copy))
    }

    @Test
    fun `bytes that are no picture give none`() {
        assertNull(blurredMinithumbnail(Minithumbnail(WIDTH, HEIGHT, byteArrayOf(1, 2, 3))))
    }

    private fun minithumbnail(color: Int): Minithumbnail {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        bitmap.setPixel(0, 0, Color.WHITE)
        return Minithumbnail(WIDTH, HEIGHT, encoded(bitmap))
    }

    private fun encoded(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out) }.toByteArray()

    private companion object {
        const val WIDTH = 40
        const val HEIGHT = 30
        const val QUALITY = 80
    }
}
