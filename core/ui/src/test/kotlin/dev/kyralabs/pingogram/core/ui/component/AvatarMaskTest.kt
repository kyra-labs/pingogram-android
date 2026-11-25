package dev.kyralabs.pingogram.core.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * [AvatarMask]: a group's, a channel's, Saved Messages' or a community's photo is cut to its polygon once, as Coil
 * keeps it, so the avatar draws it without clipping to a path; a circle's and a forum's are clipped by the GPU and
 * have none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarMaskTest {
    @Test
    fun `only the polygons have a mask, each kept under its own key`() {
        assertNull(AvatarShape.Circle.photoMask())
        assertNull(AvatarShape.RoundedSquare.photoMask())
        val keys = POLYGONS.map { checkNotNull(it.photoMask()).cacheKey }
        assertEquals(keys.distinct(), keys)
        assertEquals(AvatarShape.Group.photoMask(), AvatarShape.Group.photoMask())
    }

    @Test
    fun `cuts the photo's centre square to the polygon, transparent outside it`() =
        runTest {
            val photo = createBitmap(WIDTH, HEIGHT).apply { eraseColor(Color.RED) }

            val masked = checkNotNull(AvatarShape.Group.photoMask()).transform(photo, Size.ORIGINAL)

            assertEquals(HEIGHT, masked.width)
            assertEquals(HEIGHT, masked.height)
            assertEquals(Color.RED, masked.getPixel(HEIGHT / 2, HEIGHT / 2))
            assertEquals(Color.TRANSPARENT, masked.alpha(0, 0))
            assertEquals(Color.TRANSPARENT, masked.alpha(HEIGHT - 1, HEIGHT - 1))
        }

    @Test
    fun `each polygon cuts its own outline`() =
        runTest {
            val photo = createBitmap(HEIGHT, HEIGHT).apply { eraseColor(Color.RED) }

            val cuts = POLYGONS.map { kind -> checkNotNull(kind.photoMask()).transform(photo, Size.ORIGINAL).opaque() }

            cuts.forEach { assertNotNull(it) }
            assertNotEquals(cuts[0], cuts[1])
        }

    private fun Bitmap.alpha(
        x: Int,
        y: Int,
    ) = Color.alpha(getPixel(x, y)).let { if (it == 0) Color.TRANSPARENT else it }

    /** How many of its pixels are fully opaque. */
    private fun Bitmap.opaque(): Int {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.count { Color.alpha(it) == OPAQUE }
    }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 120
        const val OPAQUE = 255
        val POLYGONS = listOf(AvatarShape.Group, AvatarShape.Channel, AvatarShape.SavedMessages, AvatarShape.Community)
    }
}
