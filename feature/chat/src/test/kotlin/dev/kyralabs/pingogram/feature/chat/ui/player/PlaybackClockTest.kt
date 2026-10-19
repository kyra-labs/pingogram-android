package dev.kyralabs.pingogram.feature.chat.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The position the main thread reads while the player runs on its own thread ([ExoAudioPlayer]). */
class PlaybackClockTest {
    private val playing =
        PlaybackClock(positionMillis = 1_000, advancing = true, atMillis = 50_000, durationMillis = 4_000)

    @Test
    fun `while the player advances, the position moves on in real time from the last report`() {
        assertEquals(1_250, playing.positionAt(nowMillis = 50_250))
    }

    @Test
    fun `while the player does not advance, the position stays where it was reported`() {
        val paused = playing.copy(advancing = false)

        assertEquals(1_000, paused.positionAt(nowMillis = 59_000))
    }

    @Test
    fun `the position never passes the length of the file`() {
        assertEquals(4_000, playing.positionAt(nowMillis = 90_000))
    }

    @Test
    fun `without a known length, the position moves on unbounded`() {
        assertEquals(41_000, playing.copy(durationMillis = null).positionAt(nowMillis = 90_000))
    }

    @Test
    fun `a report from before the moment asked about is never extrapolated backwards`() {
        assertEquals(1_000, playing.positionAt(nowMillis = 49_000))
    }

    @Test
    fun `pausing keeps the position reached, and it stays there`() {
        val paused = playing.pausedAt(nowMillis = 50_400)

        assertFalse(paused.advancing)
        assertEquals(1_400, paused.positionAt(nowMillis = 60_000))
    }

    @Test
    fun `a seek moves the position and keeps advancing from there`() {
        val moved = playing.movedTo(positionMillis = 3_000, nowMillis = 51_000)

        assertEquals(3_100, moved.positionAt(nowMillis = 51_100))
    }

    @Test
    fun `a file about to play stays at its start until the player reports it playing`() {
        val starting = PlaybackClock.startingAt(positionMillis = 2_000, nowMillis = 10_000)

        assertEquals(2_000, starting.positionAt(nowMillis = 10_500))
    }
}
