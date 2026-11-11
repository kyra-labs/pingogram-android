package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.theme.spacing

@Preview(name = "Music on a profile")
@Composable
private fun ProfileMusicPreview() = CardPreview(ProfileMusicPreviewData.other)

@Preview(name = "Music, own profile without songs")
@Composable
private fun ProfileMusicEmptyPreview() = CardPreview(ProfileMusicPreviewData.ownEmpty)

@Preview(name = "Music, a song that cannot be played")
@Composable
private fun ProfileMusicCannotPlayPreview() = CardPreview(ProfileMusicPreviewData.cannotPlay)

@Preview(name = "Playlist, own profile")
@Composable
private fun ProfileMusicSheetPreview() = SheetPreview(ProfileMusicPreviewData.own)

@Preview(name = "Playlist, another profile")
@Composable
private fun ProfileMusicOtherSheetPreview() = SheetPreview(ProfileMusicPreviewData.other)

@Preview(name = "Playlist, a change refused")
@Composable
private fun ProfileMusicRefusedPreview() = SheetPreview(ProfileMusicPreviewData.refused)

@Composable
private fun CardPreview(content: ProfileMusicUiState.Content) =
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val progress = remember { mutableFloatStateOf(PREVIEW_PROGRESS) }
            ProfileMusicContent(content, progress, {}, {}, Modifier.padding(MaterialTheme.spacing.medium))
        }
    }

@Composable
private fun SheetPreview(content: ProfileMusicUiState.Content) =
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProfileMusicSheetContent(content, pickerMissing = false, onEvent = {}, onAdd = {})
        }
    }

/** A third of the way, as on the board. */
internal const val PREVIEW_PROGRESS = 0.33f
