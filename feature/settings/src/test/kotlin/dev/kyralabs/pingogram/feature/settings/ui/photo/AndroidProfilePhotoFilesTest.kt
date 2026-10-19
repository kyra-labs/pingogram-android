package dev.kyralabs.pingogram.feature.settings.ui.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import coil3.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * [AndroidProfilePhotoFiles] on Robolectric's native graphics: a picked image becomes a JPEG in the cache, no larger
 * than 1280 px on its longest side, decoded by a Coil image loader that runs on the test's dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidProfilePhotoFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.files(): AndroidProfilePhotoFiles {
        Dispatchers.setMain(dispatcher)
        val imageLoader = ImageLoader.Builder(context).coroutineContext(dispatcher).build()
        return AndroidProfilePhotoFiles(context, dispatcher, imageLoader)
    }

    /** A [width] × [height] PNG in the temporary folder, as a file URI. */
    private fun image(
        width: Int,
        height: Int,
    ): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val file = folder.newFile("picked-${width}x$height.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    private fun bounds(path: String): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)
        }

    @Test
    fun `a large image becomes a JPEG in the cache, 1280 px on its longest side`() =
        runTest(dispatcher) {
            val path = files().prepare(image(2560, 1600))

            assertNotNull(path)
            val file = File(path!!)
            assertEquals(File(context.cacheDir, "profile_photos").canonicalPath, file.parentFile?.canonicalPath)
            val bounds = bounds(path)
            assertEquals(1280 to 800, bounds.outWidth to bounds.outHeight)
            assertEquals("image/jpeg", bounds.outMimeType)
        }

    @Test
    fun `a small image keeps its size`() =
        runTest(dispatcher) {
            val path = files().prepare(image(300, 200))!!

            val bounds = bounds(path)
            assertEquals(300 to 200, bounds.outWidth to bounds.outHeight)
        }

    @Test
    fun `an image that cannot be read gives no copy`() =
        runTest(dispatcher) {
            val file = folder.newFile("broken.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            assertNull(files().prepare(Uri.fromFile(file).toString()))
        }

    @Test
    fun `a new photo replaces the copies of earlier ones, and a copy can be deleted`() =
        runTest(dispatcher) {
            val files = files()
            val first = files.prepare(image(300, 200))!!
            val second = files.prepare(image(400, 300))!!

            assertNotEquals(first, second)
            assertFalse(File(first).exists())
            assertTrue(File(second).exists())

            files.delete(second)
            assertFalse(File(second).exists())
        }

    @Test
    fun `sizes are scaled to fit, keeping the aspect ratio`() {
        assertEquals(1280 to 720, fitWithin(1920, 1080, 1280))
        assertEquals(720 to 1280, fitWithin(3024, 5376, 1280))
        assertEquals(1280 to 1, fitWithin(10_000, 3, 1280))
        assertEquals(1280 to 1280, fitWithin(1280, 1280, 1280))
        assertEquals(100 to 50, fitWithin(100, 50, 1280))
    }
}
