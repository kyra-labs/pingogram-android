package dev.kyralabs.pingogram.core.data.attachment

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.InputStream

/** How [AttachmentPreparer] copies and prepares picked files, and that it leaves nothing behind when it fails. */
class AttachmentPreparerTest {
    @TempDir
    lateinit var cacheDir: File

    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()

    private val uploads get() = File(cacheDir, "uploads")

    private fun preparer() = AttachmentPreparer(UploadFiles { cacheDir }, content, media)

    private fun prepared(
        uri: String,
        kind: AttachmentKind = AttachmentKind.Media,
    ): Outcome<Attachment> = runBlocking { preparer().prepare(uri, kind) }

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Success).value

    /** Every file left in the upload directories. */
    private fun leftFiles(): List<String> =
        uploads
            .walk()
            .filter { it.isFile }
            .map { it.name }
            .sorted()
            .toList()

    @Test
    fun `a photo from the gallery is written anew upright and downscaled, and its copy deleted`() {
        content.add("content://photo", "IMG_1.jpg", "image/jpeg", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(4000, 3000, Orientation.fromExif(6))

        val photo = prepared("content://photo").value() as Attachment.Photo

        assertEquals(listOf(1920, 2560), listOf(photo.width, photo.height))
        assertEquals(Orientation(90, mirrored = false), media.plans.single().orientation)
        assertTrue(File(photo.path).name.endsWith(".jpg"))
        assertEquals(FakeMediaProcessor.writtenBytes.size.toLong(), photo.size)
        // The copy, with its metadata, is gone: only the written photo is left.
        assertEquals(listOf(File(photo.path).name), leftFiles())
    }

    @Test
    fun `an image Android cannot decode goes as a document under its name`() {
        content.add("content://raw", "IMG_2.dng", "image/x-adobe-dng")

        val document = prepared("content://raw").value() as Attachment.Document

        assertEquals(
            listOf<Any>("IMG_2.dng", "image/x-adobe-dng", 3L),
            listOf(document.fileName, document.mimeType, document.size),
        )
        assertEquals("IMG_2.dng", File(document.path).name)
        assertEquals(listOf("IMG_2.dng"), leftFiles())
    }

    @Test
    fun `a photo that fails to be written goes as a document`() {
        content.add("content://photo", "IMG_3.png", "image/png", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(100, 100, Orientation.Normal)
        media.canWritePhotos = false

        val document = prepared("content://photo").value() as Attachment.Document

        assertEquals("IMG_3.png", document.fileName)
        assertEquals(listOf("IMG_3.png"), leftFiles())
    }

    @Test
    fun `without compression a JPEG goes as a file under its name, its image data kept without its metadata`() {
        content.add("content://photo", "IMG_4.jpg", "image/jpeg", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(8000, 6000, Orientation.fromExif(6))
        media.jpegs += PHOTO

        val document = prepared("content://photo", AttachmentKind.Original).value() as Attachment.Document

        assertEquals(
            listOf<Any>("IMG_4.jpg", "image/jpeg", FakeMediaProcessor.strippedBytes.size.toLong()),
            listOf(document.fileName, document.mimeType, document.size),
        )
        assertTrue(media.plans.isEmpty())
        // The picked copy, with its metadata, is gone.
        assertEquals(listOf("IMG_4.jpg"), leftFiles())
        assertEquals(FakeMediaProcessor.strippedBytes.toList(), File(document.path).readBytes().toList())
    }

    @Test
    fun `without compression another photo is written anew at its size and best quality, as a file`() {
        content.add("content://photo", "IMG_5.heic", "image/heic", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(12000, 9000, Orientation.Normal)

        val document = prepared("content://photo", AttachmentKind.Original).value() as Attachment.Document

        assertEquals(listOf("IMG_5.jpg", "image/jpeg"), listOf(document.fileName, document.mimeType))
        assertEquals(listOf(8192, 6144), media.plans.single().let { listOf(it.width, it.height) })
        assertEquals(listOf(ORIGINAL_JPEG_QUALITY), media.qualities)
        assertEquals(listOf("IMG_5.jpg"), leftFiles())
    }

    @Test
    fun `a photo whose metadata cannot be taken out is not sent without compression`() {
        content.add("content://photo", "IMG_6.heic", "image/heic", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(100, 100, Orientation.Normal)
        media.canWritePhotos = false

        assertEquals(Outcome.Failure(DomainError.FileUnavailable), prepared("content://photo", AttachmentKind.Original))
        assertTrue(leftFiles().isEmpty())
    }

    @Test
    fun `without compression a video goes as a file, as it is`() {
        content.add("content://video", "VID_1.mp4", "video/mp4", bytes = byteArrayOf(VIDEO))
        media.videos[VIDEO] = VideoInfo(1920, 1080, 0, 5_000)

        val document = prepared("content://video", AttachmentKind.Original).value() as Attachment.Document

        assertEquals(listOf("VID_1.mp4", "video/mp4"), listOf(document.fileName, document.mimeType))
        assertTrue(media.thumbnails.isEmpty())
    }

    @Test
    fun `a photo longer than Telegram takes as a photo goes as a file, without its metadata`() {
        content.add("content://panorama", "PANO_1.jpg", "image/jpeg", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(21000, 1000, Orientation.Normal)
        media.jpegs += PHOTO

        val document = prepared("content://panorama").value() as Attachment.Document

        assertEquals("PANO_1.jpg", document.fileName)
        assertTrue(media.plans.isEmpty())
    }

    @Test
    fun `a GIF from the gallery goes as a document, so Telegram shows it animated`() {
        content.add("content://gif", "cat.gif", "image/gif", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(100, 100, Orientation.Normal)

        val document = prepared("content://gif").value() as Attachment.Document

        assertEquals(listOf("cat.gif", "image/gif"), listOf(document.fileName, document.mimeType))
        assertTrue(media.plans.isEmpty())
    }

    @Test
    fun `a video keeps its file, with its size as it plays, its length and a thumbnail of at most 320 pixels`() {
        content.add("content://video", "VID_1.mp4", "video/mp4", bytes = byteArrayOf(VIDEO, 1, 2, 3, 4))
        media.videos[VIDEO] = VideoInfo(1920, 1080, rotation = 90, durationMillis = 12_400)

        val video = prepared("content://video").value() as Attachment.Video

        assertEquals(listOf<Any>(1080, 1920, 12, 5L), listOf(video.width, video.height, video.duration, video.size))
        assertEquals("VID_1.mp4", File(video.path).name)
        val thumbnail = video.thumbnail!!
        assertEquals(listOf(180, 320), listOf(thumbnail.width, thumbnail.height))
        assertEquals(File(video.path).parentFile, File(thumbnail.path).parentFile)
    }

    @Test
    fun `a video without a still goes without thumbnail`() {
        content.add("content://video", "VID_2.mp4", "video/mp4", bytes = byteArrayOf(VIDEO))
        media.videos[VIDEO] = VideoInfo(640, 480, rotation = 0, durationMillis = 300)
        media.canWriteThumbnails = false

        val video = prepared("content://video").value() as Attachment.Video

        assertNull(video.thumbnail)
        assertEquals(1, video.duration)
        assertEquals(listOf("VID_2.mp4"), leftFiles())
    }

    @Test
    fun `a file from the file picker goes as a document, also a photo`() {
        content.add("content://photo", "IMG_4.jpg", "image/jpeg", bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(4000, 3000, Orientation.Normal)

        val document = prepared("content://photo", AttachmentKind.Document).value() as Attachment.Document

        assertEquals(listOf("IMG_4.jpg", "image/jpeg"), listOf(document.fileName, document.mimeType))
        assertTrue(media.plans.isEmpty())
    }

    @Test
    fun `without a MIME type from the provider, the extension tells`() {
        content.add("content://photo", "IMG_5.JPG", mimeType = null, bytes = byteArrayOf(PHOTO))
        media.images[PHOTO] = ImageInfo(10, 10, Orientation.Normal)

        assertTrue(prepared("content://photo").value() is Attachment.Photo)
    }

    @Test
    fun `content that cannot be read fails, leaving nothing behind`() {
        val result = prepared("content://gone")

        assertEquals(Outcome.Failure(DomainError.FileUnavailable), result)
        assertEquals(emptyList<File>(), uploads.listFiles().orEmpty().toList())
    }

    @Test
    fun `an empty file fails, as TDLib sends none`() {
        content.add("content://empty", "empty.txt", "text/plain", bytes = byteArrayOf())

        assertEquals(Outcome.Failure(DomainError.FileUnavailable), prepared("content://empty"))
        assertEquals(emptyList<File>(), uploads.listFiles().orEmpty().toList())
    }

    @Test
    fun `a file over TDLib's limit is refused before it is copied`() {
        content.add("content://huge", "huge.bin", "application/octet-stream", size = MAX_FILE_SIZE + 1)
        content.openOverride = { error("Must not be opened") }

        assertEquals(Outcome.Failure(DomainError.FileTooLarge), prepared("content://huge", AttachmentKind.Document))
    }

    @Test
    fun `a file that fails while it is copied fails, leaving nothing behind`() {
        content.add("content://broken", "broken.bin", "application/octet-stream")
        content.openOverride = {
            object : InputStream() {
                override fun read(): Int = throw java.io.IOException("Gone")
            }
        }

        assertEquals(Outcome.Failure(DomainError.FileUnavailable), prepared("content://broken"))
        assertEquals(emptyList<File>(), uploads.listFiles().orEmpty().toList())
    }

    @Test
    fun `cancelling stops the copy at the next buffer and leaves nothing behind`() =
        runTest {
            lateinit var preparing: Job
            var reads = 0
            content.add("content://endless", "endless.bin", "application/octet-stream")
            content.openOverride = {
                object : InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int {
                        // The user closes the preview while the copy runs.
                        if (++reads == 2) preparing.cancel()
                        return len
                    }
                }
            }

            preparing = launch { preparer().prepare("content://endless", AttachmentKind.Document) }
            preparing.join()

            assertEquals(2, reads)
            assertEquals(emptyList<File>(), uploads.listFiles().orEmpty().toList())
        }

    @Test
    fun `a photo from the camera is prepared like one from the gallery, its file left to the caller`() {
        val camera = File(cacheDir, "camera").apply { mkdirs() }
        val shot = File(camera, "shot.jpg").apply { writeBytes(byteArrayOf(PHOTO)) }
        media.images[PHOTO] = ImageInfo(3000, 4000, Orientation.Normal)

        val photo = preparer().prepareCameraPhoto(shot).value() as Attachment.Photo

        assertEquals(listOf(1920, 2560), listOf(photo.width, photo.height))
        assertTrue(shot.exists())
    }

    @Test
    fun `an empty camera file is no photo`() {
        val shot = File(cacheDir, "shot.jpg").apply { createNewFile() }

        assertEquals(Outcome.Failure(DomainError.FileUnavailable), preparer().prepareCameraPhoto(shot))
        assertFalse(uploads.exists() && uploads.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun `file names lose path separators and control characters, and stay within 255 bytes`() {
        assertEquals("a_b_c.txt", safeFileName("a/b\\c.txt"))
        assertEquals("line_break.txt", safeFileName("line\nbreak.txt"))
        assertEquals("file", safeFileName(null))
        assertEquals("file", safeFileName(".."))
        assertEquals("file", safeFileName("  "))
        val long = safeFileName("é".repeat(300) + ".pdf")
        assertTrue(long.toByteArray().size <= 255)
        assertTrue(long.endsWith(".pdf"))
    }

    @Test
    fun `video lengths round to whole seconds, at least one`() {
        assertEquals(listOf(0, 1, 1, 2, 60), listOf(0L, 1L, 1_400L, 1_500L, 60_000L).map(::durationSeconds))
    }

    private companion object {
        const val PHOTO: Byte = 1
        const val VIDEO: Byte = 2
    }
}
