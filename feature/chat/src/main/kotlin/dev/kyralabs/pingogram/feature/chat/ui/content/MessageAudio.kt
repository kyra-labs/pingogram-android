package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A music message without its caption (`REQUIREMENTS.md` FR-C4), padded in its bubble and `mediaMaxWidth` wide: a
 * [PlayPauseButton], the title (or the file name, or "Audio") and the performer, and the time.
 *
 * Once playing started, a determinate [LinearWavyProgressIndicator] shows the progress (read in its draw phase), waving
 * while it plays and flat while paused; tapping or dragging it seeks ([onSeek]), and screen readers can set it. Before
 * that, the length and file size show instead. While [fileState] is [FileState.Downloading], a wavy ring runs around
 * the button.
 *
 * The parameters are those of [MessageVoiceNote].
 */
@Composable
internal fun MessageAudio(
    content: MessageContent.Audio,
    fileState: FileState?,
    playback: Playback,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val audio = content.audio
    val played = playedSeconds(playback.progress, audio.duration)
    val started = played != null || playback.isPlaying
    val title = audio.title.ifBlank { audio.fileName }.ifBlank { stringResource(UiR.string.preview_audio) }
    Row(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayPauseButton(isPlaying = playback.isPlaying, fileState = fileState, colors = colors, onClick = onPlayPause)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            Text(
                text = title,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (audio.performer.isNotBlank()) {
                Text(
                    text = audio.performer,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (started) {
                PlaybackProgress(playback, colors, onSeek)
            }
            Text(
                text =
                    if (started) {
                        playbackTimeText(played, playback.isPlaying, audio.duration)
                    } else {
                        stringResource(
                            R.string.content_duration_and_size,
                            mediaDurationText(audio.duration),
                            fileSizeText(audio.file.size),
                        )
                    },
                color = colors.content,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    playback: Playback,
    colors: BubbleColors,
    onSeek: (Float) -> Unit,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val direction = LocalLayoutDirection.current
    LinearWavyProgressIndicator(
        progress = playback.progress,
        modifier =
            Modifier
                .fillMaxWidth()
                .seekGestures(direction) { currentOnSeek }
                .semantics {
                    setProgress { target ->
                        currentOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                },
        color = colors.accent,
        trackColor = colors.track,
        amplitude = if (playback.isPlaying) WavyProgressIndicatorDefaults.indicatorAmplitude else NoAmplitude,
    )
}

private val NoAmplitude: (Float) -> Float = { 0f }
