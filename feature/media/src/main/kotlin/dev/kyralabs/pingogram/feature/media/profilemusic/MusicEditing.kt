package dev.kyralabs.pingogram.feature.media.profilemusic

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PickedSongRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileMusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Where profile music comes from: the account's songs ([music]) and the songs picked on the device ([songs]). */
internal class MusicSources(
    val music: ProfileMusicRepository,
    val songs: PickedSongRepository,
)

/**
 * Edits the user's own profile music (board 13b), a part of [ProfileMusicViewModel]. A picked file is prepared on the
 * device and added in the app's [appScope], so leaving the profile does not stop its upload, then the list is read
 * again ([reload]). Moves and removals show at once, go to Telegram in [scope], and come back with why when Telegram
 * refuses them; a song removed leaves the player first ([forget]). One change at a time
 * ([ProfileMusicUiState.Content.busy]).
 */
internal class MusicEditing(
    private val sources: MusicSources,
    private val state: MutableStateFlow<ProfileMusicUiState>,
    private val forget: (fileId: Int) -> Unit,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val reload: suspend () -> Unit,
) {
    private val music get() = sources.music
    private val songs get() = sources.songs

    /** Adds the song at [uri], a file the user picked. */
    fun add(uri: String) {
        val content = state.content() ?: return
        if (!content.isOwn || content.adding) return
        state.updateContent { it.copy(adding = true, message = null) }
        appScope.launch {
            val outcome =
                when (val song = songs.prepareSong(uri)) {
                    is Outcome.Failure -> song
                    is Outcome.Success -> music.add(song.value).also { songs.discardSong(song.value.path) }
                }
            if (outcome is Outcome.Failure) {
                state.updateContent { it.copy(adding = false, message = outcome.error) }
            } else {
                reload()
                state.updateContent { it.copy(adding = false) }
            }
        }
    }

    /** Moves song [from] to place [to]. */
    fun move(
        from: Int,
        to: Int,
    ) {
        val content = editable() ?: return
        if (from !in content.tracks.indices || to !in content.tracks.indices) return
        val moved = content.tracks.toMutableList().apply { add(to, removeAt(from)) }
        val track = moved[to]
        val after = if (to == 0) 0 else moved[to - 1].fileId
        change(content, moved.toImmutableList(), content.totalCount, track.fileId) { music.move(track.fileId, after) }
    }

    /** Removes song [index]. */
    fun remove(index: Int) {
        val content = editable() ?: return
        val track = content.tracks.getOrNull(index) ?: return
        forget(track.fileId)
        val left = content.tracks.filterNot { it.fileId == track.fileId }.toImmutableList()
        change(content, left, content.totalCount - 1, track.fileId) { music.remove(track.fileId) }
    }

    /** The songs shown, when they are the user's own and no other change waits. */
    private fun editable(): ProfileMusicUiState.Content? = state.content()?.takeIf { it.isOwn && it.busy == null }

    /** Shows [tracks] at once, then sends [request]; the list as it was ([before]) comes back if it fails. */
    private fun change(
        before: ProfileMusicUiState.Content,
        tracks: ImmutableList<MusicTrack>,
        totalCount: Int,
        busy: Int,
        request: suspend () -> Outcome<Unit>,
    ) {
        state.updateContent {
            val playingId = it.currentTrack?.fileId
            val stillThere = tracks.any { track -> track.fileId == playingId }
            it.copy(
                tracks = tracks,
                totalCount = totalCount,
                current = indexOf(tracks, playingId),
                isPlaying = it.isPlaying && stillThere,
                busy = busy,
                message = null,
            )
        }
        scope.launch {
            val outcome = request()
            state.updateContent { now ->
                if (outcome is Outcome.Failure) {
                    now.copy(
                        tracks = before.tracks,
                        totalCount = before.totalCount,
                        current = indexOf(before.tracks, now.currentTrack?.fileId),
                        busy = null,
                        message = outcome.error,
                    )
                } else {
                    now.copy(busy = null)
                }
            }
        }
    }
}
