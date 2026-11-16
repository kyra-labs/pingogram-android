package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.AudioInfo
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.attachment.VideoInfo
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
 * [AndroidPickedSongRepository] on fake content and decoders: a picked song becomes a copy with its length and tags,
 * a file without a title takes its name, anything that is no song is refused without leaving a file, and discarding
 * deletes the copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPickedSongRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        AndroidPickedSongRepository(
            files = files,
            content = content,
            media = media,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private val uploads: List<File> get() = File(cacheDir, "uploads").listFiles().orEmpty().toList()

    @Test
    fun `a picked song becomes a copy with its length and tags, which discarding deletes`() =
        runTest {
            val repository = repository()
            content.add("content://song", "nachtfahrt.mp3", "audio/mpeg", bytes = byteArrayOf(1))
            media.songs[1] = AudioInfo(durationMillis = 220_400, title = "Nachtfahrt", artist = "Lena Fischer")

            val song = (repository.prepareSong("content://song") as Outcome.Success).value

            assertTrue(File(song.path).exists())
            assertEquals(220, song.durationSeconds)
            assertEquals("Nachtfahrt", song.title)
            assertEquals("Lena Fischer", song.performer)

            repository.discardSong(song.path)
            runCurrent()

            assertFalse(File(song.path).exists())
            assertEquals(0, uploads.size)
        }

    @Test
    fun `a song without a title takes its file's name`() =
        runTest {
            val repository = repository()
            content.add("content://song", "Static Bloom.ogg", "audio/ogg", bytes = byteArrayOf(2))
            media.songs[2] = AudioInfo(durationMillis = 201_000, title = "", artist = "")

            val song = (repository.prepareSong("content://song") as Outcome.Success).value

            assertEquals("Static Bloom", song.title)
            assertEquals("", song.performer)
        }

    @Test
    fun `a video or a file that is no audio is refused and leaves nothing`() =
        runTest {
            val repository = repository()
            content.add("content://clip", "clip.mp4", "video/mp4", bytes = byteArrayOf(3))
            content.add("content://notes", "notes.txt", "text/plain", bytes = byteArrayOf(4))
            media.videos[3] = VideoInfo(1920, 1080, 0, 4_000)

            val results = listOf("content://clip", "content://notes").map { repository.prepareSong(it) }

            assertEquals(List(2) { Outcome.Failure(DomainError.FileUnavailable) }, results)
            assertEquals(0, uploads.size)
        }
}
