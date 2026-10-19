package dev.kyralabs.pingogram.core.data.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * [AndroidMediaProcessor] on real decoders (Robolectric's native graphics): EXIF orientation applied, metadata
 * dropped, transparency on white. API 34: Robolectric's newer runtimes need JDK 21 (`docs/TESTING.md`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidMediaProcessorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val processor = AndroidMediaProcessor()

    /** A JPEG of [width] × [height], red on its left half and blue on its right half. */
    private fun halves(
        width: Int,
        height: Int,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        return folder.newFile("halves.jpg").apply {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        }
    }

    // The platform's ExifInterface, as the processor reads it (see AndroidMediaProcessor.exifOrientation).
    @Suppress("ExifInterface")
    private fun File.setExif(vararg tags: Pair<String, String>) {
        val exif = ExifInterface(path)
        tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.saveAttributes()
    }

    private fun File.decoded(): Bitmap = BitmapFactory.decodeFile(path)

    private fun isMostlyRed(color: Int) = Color.red(color) > 200 && Color.blue(color) < 60

    private fun isMostlyBlue(color: Int) = Color.blue(color) > 200 && Color.red(color) < 60

    @Test
    fun `a photo turned by EXIF is written upright and scaled down`() {
        val source = halves(400, 200)
        source.setExif(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString())
        val target = File(folder.root, "photo.jpg")

        val info = processor.imageInfo(source)!!
        val plan = photoPlan(info.width, info.height, info.orientation, maxSide = 100)
        val written = processor.writePhoto(source, plan, target)

        val photo = target.decoded()
        assertTrue(written)
        assertEquals(ImageInfo(400, 200, Orientation(90, mirrored = false)), info)
        assertEquals(listOf(50, 100), listOf(photo.width, photo.height))
        // Turned clockwise, the left (red) half is now on top.
        assertTrue(isMostlyRed(photo.getPixel(25, 10)))
        assertTrue(isMostlyBlue(photo.getPixel(25, 90)))
    }

    @Test
    fun `a written photo has no location and no orientation left`() {
        val source = halves(64, 64)
        source.setExif(
            ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_180.toString(),
            ExifInterface.TAG_GPS_LATITUDE to "52/1,31/1,0/1",
            ExifInterface.TAG_GPS_LATITUDE_REF to "N",
        )
        val target = File(folder.root, "photo.jpg")

        processor.writePhoto(source, photoPlan(64, 64, processor.imageInfo(source)!!.orientation), target)

        @Suppress("ExifInterface")
        val written = ExifInterface(target.path)
        assertNull(written.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            written.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
        // Upside down: the left (red) half is now on the right.
        assertTrue(isMostlyBlue(target.decoded().getPixel(8, 32)))
    }

    @Test
    fun `transparency becomes white, which JPEG keeps`() {
        val source =
            folder.newFile("clear.png").apply {
                val clear = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                outputStream().use { clear.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        val target = File(folder.root, "photo.jpg")

        processor.writePhoto(source, photoPlan(32, 32, Orientation.Normal), target)

        val pixel = target.decoded().getPixel(16, 16)
        assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
    }

    @Test
    fun `what is not an image or a video is neither`() {
        val text = folder.newFile("notes.txt").apply { writeText("Not a picture") }

        assertNull(processor.imageInfo(text))
        assertNull(processor.videoInfo(text))
    }
}
