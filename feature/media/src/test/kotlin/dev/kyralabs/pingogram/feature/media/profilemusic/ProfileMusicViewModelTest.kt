package dev.kyralabs.pingogram.feature.media.profilemusic

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.media.ui.FakeVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A profile's music: shown for a user with songs and on the user's own profile, played one after another while the
 * profile is started, and on the user's own profile added, moved and removed, a refused change coming back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMusicViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val music = FakeProfileMusicRepository()
    private val songs = FakePickedSongRepository()
    private val player = FakeVideoPlayer()

    init {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        userId: Long = OTHER,
        isOwn: Boolean = false,
    ): ProfileMusicViewModel =
        ProfileMusicViewModel(music, songs, player, backgroundScope).also {
            it.start(userId, isOwn)
            runCurrent()
        }

    private val ProfileMusicViewModel.content: ProfileMusicUiState.Content
        get() = uiState.value as ProfileMusicUiState.Content

    @Test
    fun `shows another user's songs, read once`() =
        runTest(dispatcher) {
            music.songs += listOf(song(1, "Nachtfahrt"), song(2, "Monsoon Tape"))
            val viewModel = viewModel()
            viewModel.start(OTHER, isOwn = false)
            runCurrent()

            assertEquals(listOf("Nachtfahrt", "Monsoon Tape"), viewModel.content.tracks.map { it.title })
            assertEquals(2, viewModel.content.totalCount)
            assertFalse(viewModel.content.isOwn)
            assertEquals(listOf("tracks:$OTHER"), music.calls)
        }

    @Test
    fun `shows nothing for another user without songs or when they fail, but the user's own music`() =
        runTest(dispatcher) {
            assertEquals(ProfileMusicUiState.Hidden, viewModel().uiState.value)
            music.tracksFail = Outcome.Failure(DomainError.Network)
            assertEquals(ProfileMusicUiState.Hidden, viewModel(userId = 7).uiState.value)

            val own = viewModel(userId = ME, isOwn = true)

            assertTrue(own.content.isOwn)
            assertTrue(own.content.tracks.isEmpty())
        }

    @Test
    fun `plays, pauses, resumes and goes on to the next song, stopping after the last`() =
        runTest(dispatcher) {
            music.songs += listOf(song(1, "Nachtfahrt"), song(2, "Monsoon Tape"))
            val viewModel = viewModel()

            viewModel.onEvent(ProfileMusicEvent.TogglePlay)
            assertTrue(viewModel.content.isPlaying)
            player.durationMillis = 220_000
            player.positionMillis = 110_000
            advanceTimeBy(200)
            assertEquals(0.5f, viewModel.progress.value)

            viewModel.onEvent(ProfileMusicEvent.TogglePlay)
            player.pauseBySystem()
            viewModel.onEvent(ProfileMusicEvent.TogglePlay)
            player.listener?.onPlayingChanged(true)
            player.end()
            assertEquals(1, viewModel.content.current)
            player.end()
            runCurrent()

            assertEquals(
                listOf("play:stream:1:Video", "pause", "resume", "play:stream:2:Video", "stop"),
                player.calls,
            )
            assertEquals(0, viewModel.content.current)
            assertFalse(viewModel.content.isPlaying)
        }

    @Test
    fun `pauses when the profile stops, and says when a song cannot be played`() =
        runTest(dispatcher) {
            music.songs += song(1, "Nachtfahrt")
            val viewModel = viewModel()
            viewModel.onEvent(ProfileMusicEvent.Play(0))

            viewModel.onStop()
            assertFalse(viewModel.content.isPlaying)
            player.listener?.onError()

            assertEquals(listOf("play:stream:1:Video", "pause"), player.calls)
            assertTrue(viewModel.content.cannotPlay)
        }

    @Test
    fun `adds a picked song, throws its copy away and reads the songs again`() =
        runTest(dispatcher) {
            val viewModel = viewModel(userId = ME, isOwn = true)

            viewModel.onEvent(ProfileMusicEvent.Add("content://song"))
            assertTrue(viewModel.content.adding)
            runCurrent()

            assertEquals(listOf("tracks:$ME", "add:Static Bloom", "tracks:$ME"), music.calls)
            assertEquals(listOf("/uploads/song.mp3"), songs.discarded)
            assertEquals(listOf("Static Bloom"), viewModel.content.tracks.map { it.title })
            assertFalse(viewModel.content.adding)
        }

    @Test
    fun `a file that is no song says why and adds nothing`() =
        runTest(dispatcher) {
            songs.prepared = Outcome.Failure(DomainError.FileUnavailable)
            val viewModel = viewModel(userId = ME, isOwn = true)

            viewModel.onEvent(ProfileMusicEvent.Add("content://notes"))
            runCurrent()

            assertEquals(DomainError.FileUnavailable, viewModel.content.message)
            assertEquals(listOf("tracks:$ME"), music.calls)
        }

    @Test
    fun `moves a song at once, and puts it back with why when Telegram refuses`() =
        runTest(dispatcher) {
            music.songs += listOf(song(1, "Nachtfahrt"), song(2, "Monsoon Tape"), song(3, "Static Bloom"))
            val viewModel = viewModel(userId = ME, isOwn = true)

            viewModel.onEvent(ProfileMusicEvent.MoveUp(2))
            assertEquals(listOf(1, 3, 2), viewModel.content.tracks.map { it.fileId })
            runCurrent()
            assertEquals("move:3:1", music.calls.last())
            assertNull(viewModel.content.busy)

            music.answer = Outcome.Failure(DomainError.Network)
            viewModel.onEvent(ProfileMusicEvent.MoveDown(0))
            runCurrent()

            assertEquals("move:1:3", music.calls.last())
            assertEquals(listOf(1, 3, 2), viewModel.content.tracks.map { it.fileId })
            assertEquals(DomainError.Network, viewModel.content.message)
        }

    @Test
    fun `removing the playing song stops it`() =
        runTest(dispatcher) {
            music.songs += listOf(song(1, "Nachtfahrt"), song(2, "Monsoon Tape"))
            val viewModel = viewModel(userId = ME, isOwn = true)
            viewModel.onEvent(ProfileMusicEvent.Play(1))

            viewModel.onEvent(ProfileMusicEvent.Remove(1))
            runCurrent()

            assertEquals(listOf("play:stream:2:Video", "stop"), player.calls)
            assertEquals("remove:2", music.calls.last())
            assertEquals(listOf(1), viewModel.content.tracks.map { it.fileId })
            assertEquals(1, viewModel.content.totalCount)
            assertFalse(viewModel.content.isPlaying)
        }

    @Test
    fun `another user's songs cannot be changed`() =
        runTest(dispatcher) {
            music.songs += listOf(song(1, "Nachtfahrt"), song(2, "Monsoon Tape"))
            val viewModel = viewModel()

            viewModel.onEvent(ProfileMusicEvent.Remove(0))
            viewModel.onEvent(ProfileMusicEvent.MoveDown(0))
            viewModel.onEvent(ProfileMusicEvent.Add("content://song"))
            runCurrent()

            assertEquals(listOf("tracks:$OTHER"), music.calls)
            assertEquals(2, viewModel.content.tracks.size)
        }

    private companion object {
        const val OTHER = 5L
        const val ME = 9L
    }
}
