package dev.kyralabs.pingogram.feature.chat.ui.listen

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SpeechStart
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTextSpeaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reading posts aloud (FR-X26): in order at the voice messages' speed, the pill's pause, next, speed and stop, the
 * engine released at the end, and what is said instead of reading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenReaderTest {
    private val playback = FakePlaybackSettingsRepository()
    private val speaker = FakeTextSpeaker()

    private fun TestScope.reading(vararg posts: String): ListenReader =
        ListenReader(backgroundScope, speaker, playback).also {
            it.read { Outcome.Success(posts.toList()) }
            runCurrent()
        }

    private fun spoken() = speaker.spoken.map { it.first }

    @Test
    fun `reads the posts in order at the voice messages' speed, then puts the pill away`() =
        runTest {
            playback.playbackSpeed.value = PlaybackSpeed.Faster
            val reader = reading("First", "Second")

            assertEquals(ListenPlayer(1, 2, isPaused = false, PlaybackSpeed.Faster), reader.player.value)
            speaker.finish()
            runCurrent()
            assertEquals(2, reader.player.value?.position)
            speaker.finish()
            runCurrent()

            assertEquals(listOf("First" to 1.5f, "Second" to 1.5f), speaker.spoken)
            assertNull(reader.player.value)
            assertEquals(1, speaker.opened)
            assertEquals(1, speaker.closed)
        }

    @Test
    fun `pausing stops the voice and resuming reads the post again from its start`() =
        runTest {
            val reader = reading("First", "Second")

            reader.pause()
            runCurrent()
            assertFalse(speaker.isReading)
            assertEquals(true, reader.player.value?.isPaused)

            reader.resume()
            runCurrent()
            assertEquals(listOf("First", "First"), spoken())
            assertEquals(false, reader.player.value?.isPaused)
            assertEquals(1, speaker.opened)
        }

    @Test
    fun `next skips to the following post, and after the last one stops`() =
        runTest {
            val reader = reading("First", "Second")

            reader.next()
            runCurrent()
            assertEquals(listOf("First", "Second"), spoken())

            reader.next()
            runCurrent()
            assertNull(reader.player.value)
            assertEquals(1, speaker.closed)
        }

    @Test
    fun `next while paused moves on without reading`() =
        runTest {
            val reader = reading("First", "Second")
            reader.pause()

            reader.next()
            runCurrent()

            assertEquals(ListenPlayer(2, 2, isPaused = true, PlaybackSpeed.Normal), reader.player.value)
            assertEquals(listOf("First"), spoken())
        }

    @Test
    fun `a faster speed is kept for voice messages too and the post is read again at it`() =
        runTest {
            val reader = reading("Post")

            reader.changeSpeed()
            runCurrent()

            assertEquals(PlaybackSpeed.Faster, playback.playbackSpeed.first())
            assertEquals(PlaybackSpeed.Faster, reader.player.value?.speed)
            assertEquals(listOf("Post" to 1f, "Post" to 1.5f), speaker.spoken)
        }

    @Test
    fun `stop and closing release the voice`() =
        runTest {
            val reader = reading("First")

            reader.stop()
            runCurrent()

            assertNull(reader.player.value)
            assertFalse(speaker.isReading)
            assertEquals(1, speaker.closed)
        }

    @Test
    fun `a new read stops the one before`() =
        runTest {
            val reader = reading("First")

            reader.read { Outcome.Success(listOf("Other")) }
            runCurrent()

            assertEquals(listOf("First", "Other"), spoken())
            assertEquals(2, speaker.opened)
            assertEquals(1, speaker.closed)
        }

    @Test
    fun `without an offline voice nothing is read and the user is told`() =
        runTest {
            speaker.ready = false
            val reader = reading("First")

            assertEquals(ListenNotice.NoOfflineVoice, reader.notices.first())
            assertTrue(speaker.spoken.isEmpty())
            assertNull(reader.player.value)
        }

    @Test
    fun `nothing to read, posts that cannot load and a missing engine are said`() =
        runTest {
            val reader = reading()
            assertEquals(ListenNotice.NothingToRead, reader.notices.first())
            assertEquals(0, speaker.opened)

            reader.read { Outcome.Failure(DomainError.Network) }
            runCurrent()
            assertEquals(ListenNotice.NotLoaded, reader.notices.first())

            speaker.ready = false
            speaker.unready = SpeechStart.Unavailable
            reader.read { Outcome.Success(listOf("First")) }
            runCurrent()
            assertEquals(ListenNotice.Unavailable, reader.notices.first())
        }

    @Test
    fun `the engine failing midway stops reading and says so`() =
        runTest {
            val reader = reading("First", "Second")

            speaker.finish(read = false)
            runCurrent()

            assertEquals(ListenNotice.Unavailable, reader.notices.first())
            assertEquals(listOf("First"), spoken())
            assertNull(reader.player.value)
            assertEquals(1, speaker.closed)
        }
}
