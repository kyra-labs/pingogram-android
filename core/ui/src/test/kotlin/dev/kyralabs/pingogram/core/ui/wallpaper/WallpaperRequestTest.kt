package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.ui.unit.IntSize
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * What Coil decodes a photo wallpaper as: at the window's size where it shows sharp; in software and only about the
 * blur's square where it is blurred, since the blur reads its pixels back.
 */
@RunWith(RobolectricTestRunner::class)
class WallpaperRequestTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a photo to blur is decoded in software, fitted to the blur's square`() =
        runTest {
            val requests =
                listOf(
                    wallpaperRequest(context, PHOTO, DATA, WINDOW, barBlur = true),
                    wallpaperRequest(context, PHOTO.copy(isBlurred = true), DATA, WINDOW),
                )
            for (request in requests) {
                assertFalse(request.allowHardware)
                assertEquals(Size(BLUR_SIDE_PX, BLUR_SIDE_PX), request.sizeResolver.size())
                assertEquals(Scale.FIT, request.scale)
            }
        }

    @Test
    fun `a sharp photo is decoded at the window's size, filling it`() =
        runTest {
            val request = wallpaperRequest(context, PHOTO, DATA, WINDOW)

            assertTrue(request.allowHardware)
            assertEquals(Size(WINDOW.width, WINDOW.height), request.sizeResolver.size())
            assertEquals(Scale.FILL, request.scale)
        }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(1, 0)), isBlurred = false)
        val WINDOW = IntSize(1440, 3120)
        const val DATA = "photo"
    }
}
