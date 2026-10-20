package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The composer's text field while a voice message records (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6
 * Composer): a tonal pill (`surfaceContainerHighest`, `extraLarge`) as tall as the field it replaces, with a dot in
 * `error` that swells with the voice and the length (`bodyLarge`, tabular figures).
 *
 * - **Held**: "Slide to cancel" toward the start, fading as the finger slides there ([drag]), and a lock that grows as
 *   it slides up.
 * - **Locked**: Delete (in `error`) at the start, the live waveform of the last seconds in `primary`, its newest bar
 *   at the end, and Pause or Resume at the end; paused, the dot and the waveform turn `onSurfaceVariant` and stand
 *   still.
 *
 * Both crossfade on the motion scheme's effects springs. The level and the waveform are read in the draw phase, the
 * length once a second.
 */
@Composable
internal fun RecordingBar(
    recording: VoiceRecordingUiState,
    drag: RecordDrag,
    onEvent: (VoiceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = OutlinedTextFieldDefaults.MinHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = recording.isLocked,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
            },
            contentAlignment = Alignment.CenterStart,
            label = "recordingBar",
        ) { locked ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = OutlinedTextFieldDefaults.MinHeight)
                        .padding(
                            horizontal = if (locked) MaterialTheme.spacing.extraSmall else MaterialTheme.spacing.large,
                        ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (locked) {
                    LockedContent(recording, onEvent)
                } else {
                    HeldContent(recording, drag)
                }
            }
        }
    }
}

/** While held: the dot, the length, "Slide to cancel" and the lock. */
@Composable
private fun RowScope.HeldContent(
    recording: VoiceRecordingUiState,
    drag: RecordDrag,
) {
    val sizes = MaterialTheme.sizes
    val layoutDirection = LocalLayoutDirection.current
    RecordingDot(recording)
    RecordingLength(recording.meter)
    Row(
        modifier =
            Modifier
                .weight(1f)
                .graphicsLayer {
                    val progress = (drag.towardStart / sizes.recordingCancelDistance.toPx()).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    val shift = drag.towardStart / 2
                    translationX = if (layoutDirection == LayoutDirection.Ltr) -shift else shift
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_chevron_left),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.voice_slide_to_cancel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Icon(
        painter = painterResource(UiR.drawable.ic_lock_open),
        contentDescription = stringResource(R.string.voice_slide_to_lock),
        modifier =
            Modifier.graphicsLayer {
                val progress = (drag.up / sizes.recordingLockDistance.toPx()).coerceIn(0f, 1f)
                val scale = 1f + progress * LOCK_GROWTH
                scaleX = scale
                scaleY = scale
            },
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** While locked: Delete, the dot, the length, the live waveform, and Pause or Resume. */
@Composable
private fun RowScope.LockedContent(
    recording: VoiceRecordingUiState,
    onEvent: (VoiceEvent) -> Unit,
) {
    IconButton(onClick = { onEvent(VoiceEvent.Cancel) }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_delete),
            contentDescription = stringResource(R.string.voice_delete),
            tint = MaterialTheme.colorScheme.error,
        )
    }
    RecordingDot(recording)
    RecordingLength(recording.meter)
    LiveWaveform(recording, Modifier.weight(1f))
    IconButton(
        onClick = { onEvent(if (recording.isPaused) VoiceEvent.Resume else VoiceEvent.Pause) },
        shapes = IconButtonDefaults.shapes(),
    ) {
        val paused = recording.isPaused
        Icon(
            painter = painterResource(if (paused) UiR.drawable.ic_mic_filled else UiR.drawable.ic_pause_filled),
            contentDescription = stringResource(if (paused) R.string.voice_resume else R.string.voice_pause),
        )
    }
}

/** The dot that says it records: `error`, swelling with the voice; `onSurfaceVariant` and still while paused. */
@Composable
private fun RecordingDot(recording: VoiceRecordingUiState) {
    val meter = recording.meter
    val paused = recording.isPaused
    Box(
        Modifier
            .size(MaterialTheme.sizes.recordingDot)
            .graphicsLayer {
                val scale = if (paused) 1f else DOT_REST + meter.level * DOT_GROWTH
                scaleX = scale
                scaleY = scale
            }.background(
                color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                shape = CircleShape,
            ),
    )
}

/** How long it has recorded, "0:07" or "1:02:03", recomposed once a second. */
@Composable
private fun RecordingLength(meter: RecordingMeter) {
    val seconds by remember(meter) { derivedStateOf { meter.elapsedMillis / MILLIS_PER_SECOND } }
    Text(
        text = recordingLength(seconds),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
    )
}

/**
 * The levels of the last ticks as bars `waveformBarWidth` wide and `waveformBarGap` apart, as many as fit, the newest
 * at the end, centred and at least as tall as wide, so silence shows as dots: drawn from the meter in the draw phase.
 */
@Composable
private fun LiveWaveform(
    recording: VoiceRecordingUiState,
    modifier: Modifier = Modifier,
) {
    val sizes = MaterialTheme.sizes
    val meter = recording.meter
    val colors = MaterialTheme.colorScheme
    val color = if (recording.isPaused) colors.onSurfaceVariant else colors.primary
    Spacer(
        modifier
            .height(sizes.waveformHeight)
            .drawBehind {
                val barWidth = sizes.waveformBarWidth.toPx()
                val step = barWidth + sizes.waveformBarGap.toPx()
                val levels = meter.recentLevels(((size.width + sizes.waveformBarGap.toPx()) / step).toInt())
                val rtl = layoutDirection == LayoutDirection.Rtl
                levels.forEachIndexed { index, level ->
                    // The newest bar sits at the end.
                    val fromEnd = (levels.size - 1 - index) * step
                    val left = if (rtl) fromEnd else size.width - fromEnd - barWidth
                    val height = barWidth + (size.height - barWidth) * level
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, (size.height - height) / 2),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            },
    )
}

/** [seconds] as "m:ss", or "h:mm:ss" from an hour on. */
internal fun recordingLength(seconds: Long): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val TABULAR_FIGURES = "tnum"

/** The dot's scale in silence, and how much the loudest voice adds. */
private const val DOT_REST = 0.7f
private const val DOT_GROWTH = 0.6f

/** How much the lock grows as the finger reaches the lock distance. */
private const val LOCK_GROWTH = 0.4f
