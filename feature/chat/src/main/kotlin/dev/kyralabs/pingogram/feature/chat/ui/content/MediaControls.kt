package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// Controls shared by voice messages, music and files. Playback and downloads are the ViewModel's: the components
// show the state they are given (FileState, Playback) and report taps through callbacks.

/**
 * Play or pause a voice message or a song: a [FilledIconButton] in the bubble's accent whose shape morphs while
 * pressed (`IconButtonDefaults.shapes()`, `docs/DESIGN_SYSTEM.md` §4). While its file downloads ([fileState] is
 * [FileState.Downloading]), a determinate [CircularWavyProgressIndicator] runs around a plain [IconButton] that still
 * plays and pauses.
 */
@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    fileState: FileState?,
    colors: BubbleColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isPlaying) R.string.content_pause else R.string.content_play)
    val icon = if (isPlaying) UiR.drawable.ic_pause_filled else UiR.drawable.ic_play_arrow_filled
    if (fileState is FileState.Downloading) {
        DownloadRing(progress = fileState.progress, colors = colors, modifier = modifier) {
            IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.accent)) {
                Icon(painterResource(icon), contentDescription = label)
            }
        }
    } else {
        Box(modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
            ) {
                Icon(painterResource(icon), contentDescription = label)
            }
        }
    }
}

/**
 * A download's [progress] (0–1) as a determinate [CircularWavyProgressIndicator] in the bubble's accent on its
 * [BubbleColors.track], around [content], typically an icon button.
 */
@Composable
internal fun DownloadRing(
    progress: Float,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = colors.accent,
            trackColor = colors.track,
        )
        content()
    }
}

/**
 * The icon of a document, a location, a game or a story: [icon] in the bubble's `onAccent` on an `accent` circle of
 * `contentIcon`. Decorative; the text next to it says what it is.
 */
@Composable
internal fun ContentIcon(
    @DrawableRes icon: Int,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(MaterialTheme.sizes.contentIcon).background(colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onAccent)
    }
}

/**
 * The whole seconds played of media [duration] seconds long at [progress] (0–1); null before playing started (at 0).
 * [progress] may read snapshot state, e.g. a player's position: the caller recomposes only when the second changes,
 * while a waveform reads [progress] itself in the draw phase.
 */
@Composable
internal fun playedSeconds(
    progress: () -> Float,
    duration: Int,
): Int? {
    val currentProgress by rememberUpdatedState(progress)
    val played by remember(duration) {
        derivedStateOf {
            val fraction = currentProgress().coerceIn(0f, 1f)
            if (fraction > 0f) (fraction * duration).toInt() else null
        }
    }
    return played
}

/**
 * The time to show next to playable media: "0:12 / 0:42" once playing started ([played] is not null, or
 * [isPlaying]), else the [duration] alone.
 */
@Composable
internal fun playbackTimeText(
    played: Int?,
    isPlaying: Boolean,
    duration: Int,
): String =
    if (played != null || isPlaying) {
        stringResource(R.string.content_progress_of, mediaDurationText(played ?: 0), mediaDurationText(duration))
    } else {
        mediaDurationText(duration)
    }
