package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.lerp
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A voice message without its caption (`REQUIREMENTS.md` FR-C4), padded in its bubble: a [PlayPauseButton], the
 * [Waveform] and the time.
 *
 * - **Waveform**: decoded once per message, `waveformMinWidth` wide for a short message and up to `waveformMaxWidth`
 *   for a minute and more. The part played ([Playback.progress]) is in the bubble's accent, read in the draw phase, so
 *   playing never recomposes per frame; tapping or dragging it seeks ([onSeek]).
 * - **Time**: the length, or "0:12 / 0:42" once playing started; it recomposes once a second at most
 *   ([playedSeconds]). A dot in the accent follows it while nobody has listened to the message yet.
 * - **Download**: while [fileState] is [FileState.Downloading], a determinate wavy ring runs around the button.
 *
 * Screen readers hear the button ("Play"/"Pause") and then the message ("Voice message, 0:42, not played yet") as a
 * progress bar they can set to seek.
 *
 * @param fileState the voice file's state, from `FileRepository`; null while unknown.
 * @param playback whether this message plays and how far, from the ViewModel's player.
 * @param onSeek called with the fraction (0–1) to play from.
 * @param colors the bubble's colors, `MaterialTheme.pingogramColors.incomingBubble` or `outgoingBubble`.
 */
@Composable
internal fun MessageVoiceNote(
    content: MessageContent.VoiceNote,
    fileState: FileState?,
    playback: Playback,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val voiceNote = content.voiceNote
    val sizes = MaterialTheme.sizes
    val played = playedSeconds(playback.progress, voiceNote.duration)
    val time = playbackTimeText(played, playback.isPlaying, voiceNote.duration)
    val kind = stringResource(UiR.string.preview_voice_message)
    val notPlayed = stringResource(R.string.content_not_played)
    val description =
        stringResource(R.string.content_kind_and_details, kind, time).let {
            if (content.isListened) it else stringResource(R.string.content_kind_and_details, it, notPlayed)
        }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayPauseButton(isPlaying = playback.isPlaying, fileState = fileState, colors = colors, onClick = onPlayPause)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            Waveform(
                waveform = voiceNote.waveform,
                progress = playback.progress,
                colors = colors,
                onSeek = onSeek,
                width =
                    lerp(
                        sizes.waveformMinWidth,
                        sizes.waveformMaxWidth,
                        (voiceNote.duration.toFloat() / LONGEST_WAVEFORM_SECONDS).coerceIn(0f, 1f),
                    ),
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = description
                        val fraction = if (voiceNote.duration > 0) (played ?: 0).toFloat() / voiceNote.duration else 0f
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                        setProgress { target ->
                            onSeek(target.coerceIn(0f, 1f))
                            true
                        }
                    },
            )
            Row(
                modifier = Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = time, color = colors.content, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                if (!content.isListened) Badge(containerColor = colors.accent)
            }
        }
    }
}

// A voice message this long or longer gets the widest waveform.
private const val LONGEST_WAVEFORM_SECONDS = 60f
