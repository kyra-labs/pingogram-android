package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PickedSongRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileMusicRepository
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A profile's music (boards 13a, 13b), keyed by the user (`hiltViewModel(key = …)`), read once [start] names them:
 * the first [MusicLimits.PAGE_SIZE] songs. Nothing shows for another user without songs, or while the songs fail to
 * load; the user's own profile shows its music even when empty, to add songs.
 *
 * Songs play through a player of the view model's own ([VideoPlayer], Media3, streamed from TDLib as they download,
 * `docs/adr/0028-streaming-media.md`), only while the profile is started ([onStop] pauses), one after another
 * ([MusicPlayback]); how far the shown song played is [progress], snapshot state read in the draw phase, so playing
 * recomposes nothing but the slider's times. The user's own songs are added, moved and removed ([MusicEditing]).
 */
@HiltViewModel
class ProfileMusicViewModel
    @Inject
    constructor(
        private val music: ProfileMusicRepository,
        songs: PickedSongRepository,
        player: VideoPlayer,
        @ApplicationScope appScope: CoroutineScope,
    ) : ViewModel() {
        private val state = MutableStateFlow<ProfileMusicUiState>(ProfileMusicUiState.Hidden)
        val uiState: StateFlow<ProfileMusicUiState> = state.asStateFlow()

        private val playback = MusicPlayback(player, state, viewModelScope)
        private val editing =
            MusicEditing(MusicSources(music, songs), state, playback::forget, viewModelScope, appScope) {
                userId?.let { load(it, isOwn = true) }
            }

        /** How far the shown song has played, 0–1. */
        val progress: State<Float> get() = playback.progress

        private var userId: Long? = null

        /** Reads user [userId]'s songs, once per user; [isOwn] for the user's own profile. */
        fun start(
            userId: Long,
            isOwn: Boolean,
        ) {
            if (this.userId == userId) return
            this.userId = userId
            viewModelScope.launch { load(userId, isOwn) }
        }

        fun onEvent(event: ProfileMusicEvent) {
            when (event) {
                ProfileMusicEvent.TogglePlay -> playback.toggle()
                ProfileMusicEvent.Next -> playback.next()
                is ProfileMusicEvent.Play -> playback.play(event.index)
                is ProfileMusicEvent.Seek -> playback.seek(event.fraction)
                ProfileMusicEvent.OpenSheet -> state.updateContent { it.copy(sheetOpen = true, message = null) }
                ProfileMusicEvent.CloseSheet -> state.updateContent { it.copy(sheetOpen = false, message = null) }
                is ProfileMusicEvent.Add -> editing.add(event.uri)
                is ProfileMusicEvent.MoveUp -> editing.move(event.index, event.index - 1)
                is ProfileMusicEvent.MoveDown -> editing.move(event.index, event.index + 1)
                is ProfileMusicEvent.Remove -> editing.remove(event.index)
            }
        }

        /** The profile left the screen: nothing plays in the background, which would need a media session. */
        fun onStop() = playback.pause()

        override fun onCleared() = playback.release()

        private suspend fun load(
            userId: Long,
            isOwn: Boolean,
        ) {
            val found = music.tracks(userId, 0, MusicLimits.PAGE_SIZE)
            val previous = state.content()
            state.value =
                when {
                    found !is Outcome.Success -> {
                        previous
                            ?: if (isOwn) {
                                ProfileMusicUiState.Content(
                                    isOwn = true,
                                    totalCount = 0,
                                )
                            } else {
                                ProfileMusicUiState.Hidden
                            }
                    }

                    found.value.tracks.isEmpty() && !isOwn -> {
                        ProfileMusicUiState.Hidden
                    }

                    else -> {
                        val tracks =
                            found.value.tracks
                                .map { it.toTrack() }
                                .toImmutableList()
                        val playingId = previous?.currentTrack?.fileId
                        ProfileMusicUiState.Content(
                            isOwn = isOwn,
                            totalCount = found.value.totalCount,
                            tracks = tracks,
                            current = indexOf(tracks, playingId),
                            isPlaying = previous?.isPlaying == true && tracks.any { it.fileId == playingId },
                            sheetOpen = previous?.sheetOpen == true,
                        )
                    }
                }
        }
    }
