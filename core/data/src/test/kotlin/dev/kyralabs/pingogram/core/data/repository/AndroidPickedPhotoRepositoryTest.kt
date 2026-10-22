package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.ImageInfo
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoLimits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * [AndroidPickedPhotoRepository] on fake content and decoders: a picked image becomes an upright JPEG copy no larger
 * than a chat photo needs, anything else is refused without leaving a file, and discarding deletes the copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPickedPhotoRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        AndroidPickedPhotoRepository(
            files = files,
            content = content,
            media = media,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private val uploads: List<File> get() = File(cacheDir, "uploads").listFiles().orEmpty().toList()

    @Test
    fun `a picked image becomes an upright copy at most the photo size, which discarding deletes`() =
        runTest {
            val repository = repository()
            content.add("content://photo", "photo.jpg", "image/jpeg", bytes = byteArrayOf(1))
            media.images[1] = ImageInfo(4000, 3000, Orientation.fromExif(EXIF_ROTATE_90))

            val photo = repository.preparePhoto("content://photo")

            val path = (photo as Outcome.Success).value
            assertTrue(File(path).exists())
            val plan = media.plans.single()
            assertEquals(PickedPhotoLimits.MAX_SIDE_PX, maxOf(plan.width, plan.height))
            // Turned upright: taller than wide.
            assertTrue(plan.height > plan.width)

            repository.discardPhoto(path)
            runCurrent()

            assertFalse(File(path).exists())
            assertEquals(0, uploads.size)
        }

    @Test
    fun `a video, a GIF or an image that cannot be decoded is refused and leaves nothing`() =
        runTest {
            val repository = repository()
            content.add("content://clip", "clip.mp4", "video/mp4", bytes = byteArrayOf(2))
            content.add("content://loop", "loop.gif", "image/gif", bytes = byteArrayOf(3))
            content.add("content://broken", "broken.jpg", "image/jpeg", bytes = byteArrayOf(4))

            val results =
                listOf(
                    "content://clip",
                    "content://loop",
                    "content://broken",
                ).map { repository.preparePhoto(it) }

            assertEquals(List(3) { Outcome.Failure(DomainError.PhotoInvalid) }, results)
            assertEquals(0, uploads.size)
        }

    @Test
    fun `an image that cannot be read is refused`() =
        runTest {
            assertEquals(Outcome.Failure(DomainError.PhotoInvalid), repository().preparePhoto("content://gone"))
        }

    private companion object {
        const val EXIF_ROTATE_90 = 6
    }
}
