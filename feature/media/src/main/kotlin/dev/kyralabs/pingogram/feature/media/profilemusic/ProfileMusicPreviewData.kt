package dev.kyralabs.pingogram.feature.media.profilemusic

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.persistentListOf

/** What the previews and goldens of profile music show (boards 13a, 13b). */
internal object ProfileMusicPreviewData {
    private val tracks =
        persistentListOf(
            MusicTrack(1, "Nachtfahrt", "Lena Fischer", 220, cover = null),
            MusicTrack(2, "Monsoon Tape", "The Coorg Collective", 252, cover = null),
            MusicTrack(3, "Chai at 2 AM", "Night Kettle", 178, cover = null),
            MusicTrack(4, "Static Bloom", "", 201, cover = null),
        )

    val other =
        ProfileMusicUiState.Content(
            isOwn = false,
            totalCount = 3,
            tracks = tracks.subList(0, 3),
            isPlaying = true,
        )
    val own = ProfileMusicUiState.Content(isOwn = true, totalCount = 4, tracks = tracks, isPlaying = true)
    val ownEmpty = ProfileMusicUiState.Content(isOwn = true, totalCount = 0)
    val cannotPlay = other.copy(isPlaying = false, cannotPlay = true)
    val refused = own.copy(isPlaying = false, message = DomainError.Network)
}
