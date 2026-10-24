package dev.kyralabs.pingogram.core.ui.sticker

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.platform.WindowInfo
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AnimatedStickers] with Lottie ([LottieStickerDecoder]) on Robolectric's native graphics: downloading the file,
 * decoding it once, its first frame in the frame cache, and what fails.
 */
@RunWith(RobolectricTestRunner::class)
class AnimatedStickersTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files = FakeStickerFiles()
    private var decodes = 0
    private val decoder = StickerAnimationDecoder { file -> decodes++.let { LottieStickerDecoder().decode(file) } }
    private val stickers = AnimatedStickers(files, decoder, renderDispatcher = Dispatchers.Unconfined)

    @Test
    fun `Lottie reads a TGS file's frames and rate, and draws its frames scaled into the bitmap`() {
        val animation = checkNotNull(LottieStickerDecoder().decode(pulseTgs(folder.root)))
        val first = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val last = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)

        animation.newRenderer().render(0, first)
        animation.newRenderer().render(animation.frameCount - 1, last)

        assertEquals(60, animation.frameCount)
        assertEquals(60f, animation.frameRate)
        // The yellow dot in the middle, transparent corners, and the ring further out at the end.
        assertTrue(Color.alpha(first.getPixel(64, 64)) == 255)
        assertEquals(Color.TRANSPARENT, first.getPixel(1, 1))
        assertNotEquals(first.getPixel(64, 12), last.getPixel(64, 12))
    }

    @Test
    fun `a file that is not a TGS decodes to nothing`() {
        val text = folder.newFile("not.tgs").apply { writeText("{}") }

        assertNull(LottieStickerDecoder().decode(text))
    }

    @Test
    fun `a sticker not on the device yet is downloaded at visible priority, then decoded once`() =
        runTest {
            files.state.value = FileState.NotDownloaded(size = 100, canBeDownloaded = true)

            val first = async { stickers.animation(FILE_ID) }
            runCurrent()
            files.state.value = FileState.Downloading(downloadedSize = 50, size = 100, isActive = true)
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val animation = first.await()
            val again = stickers.animation(FILE_ID)

            assertEquals(listOf(FILE_ID to DownloadPriority.Visible), files.downloads)
            assertNotNull(animation)
            assertSame(animation, again)
            assertEquals(1, decodes)
        }

    @Test
    fun `a file that cannot be downloaded, or whose download stops, has no animation`() =
        runTest {
            files.state.value = FileState.NotDownloaded(size = 100, canBeDownloaded = false)
            assertNull(stickers.animation(FILE_ID))

            files.state.value = FileState.NotDownloaded(size = 100, canBeDownloaded = true)
            files.downloadResult = Outcome.Failure(DomainError.Network)
            assertNull(stickers.animation(FILE_ID))
        }

    @Test
    fun `first frames are cached by sticker and size, and forgotten when TDLib closes`() =
        runTest {
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val animation = checkNotNull(stickers.animation(FILE_ID))

            val small = stickers.firstFrame(FILE_ID, animation, 64, 64)
            val smallAgain = stickers.firstFrame(FILE_ID, animation, 64, 64)
            val large = stickers.firstFrame(FILE_ID, animation, 128, 128)
            stickers.clear()
            stickers.animation(FILE_ID)

            assertSame(small, smallAgain)
            assertEquals(128, large.width)
            assertEquals(2, decodes)
        }

    @Test
    fun `power saving holds stickers still, and they play on once it is off`() =
        runTest {
            val still = MutableStateFlow(true)
            val saving = AnimatedStickers(files, decoder, renderDispatcher = Dispatchers.Unconfined, still = still)
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val draws = MutableStateFlow(0L)
            var shown = 0
            val player =
                StickerPlayer(saving, checkNotNull(saving.animation(FILE_ID)), 64, 64, draws) {
                    shown++
                    draws.value++
                }
            val window =
                object : WindowInfo {
                    override val isWindowFocused = true
                }
            val playing = launch { player.playOnScreen(window) { draws.value++ } }

            advanceTimeBy(1_000)
            assertEquals(0, shown)
            still.value = false
            advanceTimeBy(1_000)
            assertTrue(shown > 1)
            still.value = true
            advanceTimeBy(100)
            val stopped = shown
            advanceTimeBy(1_000)

            assertEquals(stopped, shown)
            playing.cancel()
        }

    private companion object {
        const val FILE_ID = 42
    }
}
