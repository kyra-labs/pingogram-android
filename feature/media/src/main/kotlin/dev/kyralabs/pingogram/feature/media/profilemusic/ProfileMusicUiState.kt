package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.feature.media.storyalbum.AlbumPicture
import dev.kyralabs.pingogram.feature.media.storyalbum.AlbumPictureFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** What a profile's music shows (boards 13a, 13b). */
@Immutable
sealed interface ProfileMusicUiState {
    /** Nothing: loading, a profile without songs that is not the user's own, or songs that failed to load. */
    data object Hidden : ProfileMusicUiState

    /**
     * The songs: [tracks] in their order (the first [MusicLimits.PAGE_SIZE]), of [totalCount]; [current] the one the
     * card shows and plays ([isPlaying]); [isOwn] the user's own profile, whose songs they add, move and remove.
     * [adding] while a picked song is prepared and uploaded, [busy] the song a move or removal waits for, [message]
     * why the last change failed, [cannotPlay] the shown song cannot be played, [sheetOpen] while the playlist shows.
     */
    data class Content(
        val isOwn: Boolean,
        val totalCount: Int,
        val tracks: ImmutableList<MusicTrack> = persistentListOf(),
        val current: Int = 0,
        val isPlaying: Boolean = false,
        val adding: Boolean = false,
        val busy: Int? = null,
        val message: DomainError? = null,
        val cannotPlay: Boolean = false,
        val sheetOpen: Boolean = false,
    ) : ProfileMusicUiState {
        val currentTrack: MusicTrack? get() = tracks.getOrNull(current)
    }
}

/** A profile song: its file [fileId], [title], [performer], length in seconds and album cover, if it has one. */
@Immutable
data class MusicTrack(
    val fileId: Int,
    val title: String,
    val performer: String,
    val durationSeconds: Int,
    val cover: AlbumPicture?,
) {
    override fun toString() = "MusicTrack(fileId=$fileId, …)"
}

/** What the user does to a profile's music. */
sealed interface ProfileMusicEvent {
    data object TogglePlay : ProfileMusicEvent

    data object Next : ProfileMusicEvent

    /** Plays song [index] from its start. */
    data class Play(
        val index: Int,
    ) : ProfileMusicEvent

    /** Moves the shown song to [fraction] (0–1) of its length. */
    data class Seek(
        val fraction: Float,
    ) : ProfileMusicEvent

    data object OpenSheet : ProfileMusicEvent

    data object CloseSheet : ProfileMusicEvent

    /** The file picker gave [uri] to add to the user's own profile music. */
    data class Add(
        val uri: String,
    ) : ProfileMusicEvent

    data class MoveUp(
        val index: Int,
    ) : ProfileMusicEvent

    data class MoveDown(
        val index: Int,
    ) : ProfileMusicEvent

    data class Remove(
        val index: Int,
    ) : ProfileMusicEvent
}

/** The limits of a profile's music on screen. */
object MusicLimits {
    /** How many songs are read: TDLib's pages are cheap, and profiles hold few songs. */
    const val PAGE_SIZE = 100
}

/** [audio] as a profile song. */
internal fun Audio.toTrack(): MusicTrack =
    MusicTrack(
        fileId = file.id,
        title = title.ifBlank { fileName },
        performer = performer,
        durationSeconds = duration,
        cover =
            albumCoverThumbnail?.let { thumbnail ->
                AlbumPicture(
                    minithumbnail = albumCoverMinithumbnail,
                    files =
                        listOf(
                            AlbumPictureFile(thumbnail.file.id, thumbnail.width, thumbnail.height),
                        ).toImmutableList(),
                )
            } ?: albumCoverMinithumbnail?.let { AlbumPicture(it, persistentListOf()) },
    )

/** The songs shown, when there are any to show. */
internal fun MutableStateFlow<ProfileMusicUiState>.content(): ProfileMusicUiState.Content? =
    value as? ProfileMusicUiState.Content

/** Changes the songs shown with [change], when there are any. */
internal inline fun MutableStateFlow<ProfileMusicUiState>.updateContent(
    crossinline change: (ProfileMusicUiState.Content) -> ProfileMusicUiState.Content,
) {
    update { (it as? ProfileMusicUiState.Content)?.let(change) ?: it }
}

/** The index of the song of file [fileId] in [tracks]; 0 when it is not there. */
internal fun indexOf(
    tracks: List<MusicTrack>,
    fileId: Int?,
): Int = tracks.indexOfFirst { it.fileId == fileId }.coerceAtLeast(0)
