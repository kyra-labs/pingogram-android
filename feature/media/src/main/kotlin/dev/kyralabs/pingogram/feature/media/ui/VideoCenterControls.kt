package dev.kyralabs.pingogram.feature.media.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A video's play or pause in the middle of the viewer (board 25), a large [FilledIconButton] in
 * `MaterialShapes.Cookie9Sided`, between "Back 10 seconds" and "Forward 10 seconds" as tonal icon buttons on the media
 * overlay. They keep their places in right-to-left layouts, as media timelines do not mirror. Nothing for a photo,
 * nor while the video waits for its first tap (its page shows that play button) or [streams] before its first frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoCenterControls(
    item: MediaItem,
    playback: VideoPlayback?,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
    streams: Boolean = false,
) {
    val controls = playback?.takeIf { item.media is ViewerMedia.Clip && it.messageId == item.messageId }
    if (controls == null || controls.waitsForTap || streams) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekButton(R.drawable.ic_replay_10, stringResource(R.string.media_back_10)) {
                onEvent(MediaViewerEvent.SeekBy(-SEEK_STEP_SECONDS))
            }
            FilledIconButton(
                onClick = { onEvent(MediaViewerEvent.PlayPause) },
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                shape = MaterialShapes.Cookie9Sided.toShape(),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (controls.isPlaying) UiR.drawable.ic_pause_filled else UiR.drawable.ic_play_arrow_filled,
                        ),
                    contentDescription =
                        stringResource(if (controls.isPlaying) R.string.media_pause else R.string.media_play),
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
            SeekButton(R.drawable.ic_forward_10, stringResource(R.string.media_forward_10)) {
                onEvent(MediaViewerEvent.SeekBy(SEEK_STEP_SECONDS))
            }
        }
    }
}

@Composable
private fun SeekButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.pingogramColors
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.mediaOverlay,
                contentColor = colors.onMediaOverlay,
            ),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/** Mute or unmute videos: an icon toggle named for screen readers. */
@Composable
internal fun MuteButton(
    muted: Boolean,
    onClick: () -> Unit,
) {
    IconToggleButton(checked = muted, onCheckedChange = { onClick() }, shapes = IconButtonDefaults.toggleableShapes()) {
        Icon(
            painter = painterResource(if (muted) R.drawable.ic_volume_off_filled else R.drawable.ic_volume_up_filled),
            contentDescription = stringResource(R.string.media_mute),
        )
    }
}

/** The video's play or pause and ±10 s in the middle ([VideoCenterControls]), shown and hidden with the bars. */
@Composable
internal fun BoxScope.CenterChrome(
    content: MediaViewerUiState.Content,
    chrome: ChromeState,
    dismiss: DismissState,
    height: Float,
    onEvent: (MediaViewerEvent) -> Unit,
) {
    Chrome(chrome.visible, dismiss, height, afterEnter = true, modifier = Modifier.align(Alignment.Center)) {
        // Until a streaming video's first frame, its status takes the middle (StreamingStatus).
        val media = content.current.media
        val streams =
            content.playback?.showsFrame == false && content.files[media.fileId] is FileState.Downloading
        VideoCenterControls(content.current, content.playback, onEvent, streams = streams)
    }
}

/** How far "Back 10 seconds" and "Forward 10 seconds" move a video, and the seek bar's step for screen readers. */
internal const val SEEK_STEP_SECONDS = 10
