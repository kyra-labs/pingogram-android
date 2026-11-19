package dev.kyralabs.pingogram.feature.chat.ui.player

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * Releasing the voice player on its own thread ([releaseAndQuitLooper]): ExoPlayer's last events are handled before the
 * thread's looper quits, so their flushes (Media3's `ListenerSet`) reach no dead thread, and the thread ends. Quitting
 * right after `release()`, as before, logged "sending message to a Handler on a dead thread" for each.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerReleaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a player released on its thread flushes no event to a dead thread, and the thread ends`() {
        val thread = HandlerThread("ChatAudioPlayer").apply { start() }
        Handler(thread.looper).post { ExoPlayer.Builder(context).build().releaseAndQuitLooper() }

        thread.join(TIMEOUT_MILLIS)

        assertFalse(thread.isAlive)
        val lateFlushes =
            ShadowLog.getLogs().filter { log ->
                DEAD_THREAD in log.msg.orEmpty() &&
                    log.throwable
                        ?.stackTrace
                        .orEmpty()
                        .any { it.className.endsWith(LISTENER_SET) }
            }
        assertEquals(emptyList<String>(), lateFlushes.map { it.msg })
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val DEAD_THREAD = "sending message to a Handler on a dead thread"
        const val LISTENER_SET = ".ListenerSet"
    }
}
