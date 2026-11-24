package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * A photo wallpaper's blurred copy is decoded once and kept under a key of its own ([blurKey]): a conversation opened
 * again reads it back ([keptBlur]) and makes no request, so nothing is decoded again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperBlurKeyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var requests = 0
    private val loader =
        ImageLoader
            .Builder(context)
            .components { add(Interceptor { chain -> requests++.let { chain.proceed() } }) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
            .build()

    @Test
    fun `the bar's blurred copy is decoded once, and the next open reads it back without a request`() =
        runTest {
            val photo = folder.newFile("photo.jpg")
            val pixels = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            photo.outputStream().use { pixels.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            val request = { wallpaperRequest(context, PHOTO, photo, WINDOW, barBlur = true) }
            assertNull(keptBlur(loader, request()))

            val decoded = (loader.execute(request()) as SuccessResult).image
            val reopened = keptBlur(loader, request())

            assertSame(decoded, reopened)
            assertEquals(1, requests)
        }

    @Test
    fun `each photo and blur has its own key, and data without a stable key has none`() {
        assertEquals("tdlib-file:5#wallpaper-bar-blur", blurKey(TdlibFileImage(5), WallpaperBlur.Bar))
        assertNotEquals(blurKey(TdlibFileImage(5), WallpaperBlur.Bar), blurKey(TdlibFileImage(5), WallpaperBlur.Photo))
        assertNotEquals(blurKey(TdlibFileImage(5), WallpaperBlur.Bar), blurKey(TdlibFileImage(6), WallpaperBlur.Bar))
        assertNull(blurKey("photo", WallpaperBlur.Bar))
        assertNull(wallpaperRequest(context, PHOTO, TdlibFileImage(5), WINDOW).memoryCacheKey)
    }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(1, 0)), isBlurred = false)
        val WINDOW = IntSize(1440, 3120)
        const val MEMORY_CACHE_BYTES = 64L * 1024 * 1024
        const val JPEG_QUALITY = 90
    }
}
