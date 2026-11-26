package dev.kyralabs.pingogram.core.ui.sticker

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.WindowInfo
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.transition.focusedUnless
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.zip.GZIPOutputStream

/**
 * [AnimatedStickers] with Lottie ([LottieStickerDecoder]) on Robolectric's native graphics: downloading the file,
 * decoding it once, its first frame in the frame cache, and what fails.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `a sticker without a size draws blank frames instead of failing`() {
        val json = """{"v":"5.5.2","fr":30,"ip":0,"op":30,"w":0,"h":0,"layers":[]}"""
        val tgs = folder.newFile("empty.tgs")
        GZIPOutputStream(tgs.outputStream()).use { it.write(json.toByteArray()) }
        val animation = checkNotNull(LottieStickerDecoder().decode(tgs))
        val frame = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        animation.newRenderer().render(0, frame)

        assertEquals(Color.TRANSPARENT, frame.getPixel(32, 32))
    }

    @Test
    fun `a sticker whose layers Lottie cannot build draws nothing instead of failing`() {
        // A precomposition layer whose asset is missing: it parses, but Lottie fails building its layers.
        val json =
            """{"v":"5.5.2","fr":30,"ip":0,"op":30,"w":64,"h":64,"assets":[],"layers":[""" +
                """{"ty":0,"refId":"missing","ind":1,"ip":0,"op":30,"st":0,"w":64,"h":64,"ks":{}}]}"""
        val tgs = folder.newFile("broken.tgs")
        GZIPOutputStream(tgs.outputStream()).use { it.write(json.toByteArray()) }
        val animation = checkNotNull(LottieStickerDecoder().decode(tgs))
        val frame = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        assertFalse(animation.newRenderer().render(0, frame))
        assertEquals(Color.TRANSPARENT, frame.getPixel(32, 32))
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
            // The frames' clock is the test's, as the delays between them are.
            val saving =
                AnimatedStickers(
                    files,
                    decoder,
                    renderDispatcher = Dispatchers.Unconfined,
                    still = still,
                    timeSource = testTimeSource,
                )
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

    @Test
    fun `paused under the message menu, a sticker holds its frame, and plays on from it after`() =
        runTest {
            val timed = AnimatedStickers(files, decoder, Dispatchers.Unconfined, timeSource = testTimeSource)
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val draws = MutableStateFlow(0L)
            var shown = 0
            val player =
                StickerPlayer(timed, checkNotNull(timed.animation(FILE_ID)), 64, 64, draws) {
                    shown++
                    draws.value++
                }
            var paused by mutableStateOf(false)
            val window = FocusedWindow.focusedUnless { paused }
            val playing = launch { player.playOnScreen(window) { draws.value++ } }
            advanceTimeBy(HALF_PLAY_MILLIS)

            paused = true
            Snapshot.sendApplyNotifications()
            advanceTimeBy(FRAME_MILLIS)
            val (heldShown, heldFrame) = shown to player.frame
            advanceTimeBy(PLAY_MILLIS)
            assertEquals(heldShown to heldFrame, shown to player.frame)

            paused = false
            Snapshot.sendApplyNotifications()
            advanceTimeBy(HALF_PLAY_MILLIS)
            assertTrue(shown > heldShown)
            assertTrue("frame ${player.frame}, held $heldFrame", player.frame > heldFrame)
            playing.cancel()
        }

    @Test
    fun `frames follow the clock, so a sticker drawn late keeps its pace`() =
        runTest {
            val timed = AnimatedStickers(files, decoder, Dispatchers.Unconfined, timeSource = testTimeSource)
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val animation = checkNotNull(timed.animation(FILE_ID))
            val draws = MutableStateFlow(0L)
            // A busy device: every frame is drawn 100 ms after it is shown.
            val player =
                StickerPlayer(timed, animation, 64, 64, draws) {
                    launch {
                        delay(LATE_DRAW_MILLIS)
                        draws.value++
                    }
                }
            val playing = launch { player.playOnScreen(FocusedWindow) { draws.value++ } }

            advanceTimeBy(PLAY_MILLIS)

            // The pulse runs at 60 frames per second: 550 ms in, about frame 33, not one step of 2 per late draw (12).
            val due = (PLAY_MILLIS * animation.frameRate / MILLIS_PER_SECOND).toInt()
            assertTrue("frame ${player.frame}, due $due", player.frame in due - FRAME_SLACK..due)
            playing.cancel()
        }

    @Test
    fun `a sticker that leaves the screen while waiting for a slot gives up its place`() =
        runTest {
            val oneSlot =
                AnimatedStickers(files, decoder, Dispatchers.Unconfined, maxAnimating = 1, timeSource = testTimeSource)
            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val animation = checkNotNull(oneSlot.animation(FILE_ID))
            val playingDraws = MutableStateFlow(0L)
            val playingOne = StickerPlayer(oneSlot, animation, 64, 64, playingDraws) { playingDraws.value++ }
            val holding = launch { playingOne.playOnScreen(FocusedWindow) { playingDraws.value++ } }
            advanceTimeBy(SHORT_MILLIS)
            var onScreen = true
            var shown = 0
            val waitingDraws = MutableStateFlow(0L)
            val waitingOne =
                StickerPlayer(oneSlot, animation, 64, 64, waitingDraws) {
                    shown++
                    if (onScreen) waitingDraws.value++
                }
            val waiting = launch { waitingOne.playOnScreen(FocusedWindow) { if (onScreen) waitingDraws.value++ } }
            advanceTimeBy(SHORT_MILLIS)

            // It scrolls away while the other plays, then the other stops.
            onScreen = false
            advanceTimeBy(LEAVE_MILLIS)
            holding.cancel()
            advanceTimeBy(LEAVE_MILLIS)

            assertEquals(0, shown)
            waiting.cancel()
        }

    @Test
    fun `a sticker that cannot be drawn tries once, and gives its slot to the next`() =
        runTest {
            val oneSlot =
                AnimatedStickers(files, decoder, Dispatchers.Unconfined, maxAnimating = 1, timeSource = testTimeSource)
            var renders = 0
            val broken =
                object : StickerAnimation {
                    override val frameCount = 60
                    override val frameRate = 60f

                    override fun newRenderer() =
                        StickerFrameRenderer { _, _ ->
                            renders++
                            false
                        }
                }
            val brokenDraws = MutableStateFlow(0L)
            val brokenOne = StickerPlayer(oneSlot, broken, 64, 64, brokenDraws) { brokenDraws.value++ }
            val failing = launch { brokenOne.playOnScreen(FocusedWindow) { brokenDraws.value++ } }
            advanceTimeBy(SHORT_MILLIS)

            files.state.value = FileState.Downloaded(pulseTgs(folder.root).path, size = 100)
            val animation = checkNotNull(oneSlot.animation(FILE_ID))
            var shown = 0
            val draws = MutableStateFlow(0L)
            val next =
                StickerPlayer(oneSlot, animation, 64, 64, draws) {
                    shown++
                    draws.value++
                }
            val playing = launch { next.playOnScreen(FocusedWindow) { draws.value++ } }
            advanceTimeBy(PLAY_MILLIS)

            assertEquals(1, renders)
            assertTrue("shown $shown", shown > 0)
            failing.cancel()
            playing.cancel()
        }

    private object FocusedWindow : WindowInfo {
        override val isWindowFocused = true
    }

    private companion object {
        const val FILE_ID = 42
        const val LATE_DRAW_MILLIS = 100L
        const val PLAY_MILLIS = 550L
        const val HALF_PLAY_MILLIS = 200L
        const val FRAME_MILLIS = 50L
        const val MILLIS_PER_SECOND = 1_000f

        /** The frames since the last draw came: 50 ms at 60 frames per second, shown in steps of 2. */
        const val FRAME_SLACK = 4
        const val SHORT_MILLIS = 50L
        const val LEAVE_MILLIS = 600L
    }
}
