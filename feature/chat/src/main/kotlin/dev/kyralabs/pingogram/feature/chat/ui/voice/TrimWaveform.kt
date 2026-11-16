package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import kotlin.math.abs

/**
 * A paused voice recording's whole waveform with its trim (`docs/DESIGN_SYSTEM.md` §6 Composer, "Trimming"): bars as
 * the live waveform's, the part that will be sent in `primary` and the parts cut away in `onSurfaceVariant` at 38%,
 * between two handles in `onSurface`, twice a bar's width and as tall as the waveform. A horizontal drag moves the
 * handle nearer the finger (mirrored right to left), keeping at least a second between them, with a `TextHandleMove`
 * haptic as it starts; the trim goes to the recorder ([VoiceEvent.Trim]) as the finger lifts. Drawn in the draw phase
 * from the drag's own state. Screen readers hear what is kept ("Trim: keeps 0:02 to 0:09 of 0:15") and move each end
 * a second at a time through custom actions.
 */
@Composable
internal fun TrimWaveform(
    trim: VoiceTrim,
    onEvent: (VoiceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val draft = remember(trim) { TrimDraft(trim.startMillis, trim.endMillis) }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val description =
        stringResource(
            R.string.voice_trim_description,
            recordingLength(draft.start / MILLIS_PER_SECOND),
            recordingLength(draft.end / MILLIS_PER_SECOND),
            recordingLength(trim.durationMillis / MILLIS_PER_SECOND),
        )
    val actions = trimActions(trim, draft.start, draft.end, onEvent)
    Spacer(
        modifier
            .height(sizes.waveformHeight)
            .semantics {
                contentDescription = description
                customActions = actions
            }.pointerInput(trim) {
                detectTrimDrag(
                    durationMillis = trim.durationMillis,
                    draft = draft,
                    rtl = rtl,
                    onStart = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    onEnd = { currentOnEvent(VoiceEvent.Trim(draft.start, draft.end)) },
                )
            }.drawBehind {
                val barWidth = sizes.waveformBarWidth.toPx()
                drawBars(
                    trim,
                    draft,
                    barWidth,
                    sizes.waveformBarGap.toPx(),
                    rtl,
                    colors.primary,
                    colors.onSurfaceVariant,
                )
                drawHandles(trim, draft, barWidth * HANDLE_WIDTH, rtl, colors.onSurface)
            },
    )
}

/** Where the handles are while the finger moves them, read in the draw phase. */
@Stable
private class TrimDraft(
    start: Long,
    end: Long,
) {
    var start by mutableLongStateOf(start)
    var end by mutableLongStateOf(end)
}

/** Moves the handle nearer the finger, at least a second from the other; [onEnd] as the finger lifts. */
private suspend fun PointerInputScope.detectTrimDrag(
    durationMillis: Long,
    draft: TrimDraft,
    rtl: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    var movingStart = false
    val min = ChatRecorder.MIN_DURATION_MILLIS
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            val time = timeAt(offset.x, size.width.toFloat(), durationMillis, rtl)
            movingStart = abs(time - draft.start) < abs(time - draft.end)
            onStart()
        },
        onDragEnd = onEnd,
        onDragCancel = onEnd,
    ) { change, _ ->
        val time = timeAt(change.position.x, size.width.toFloat(), durationMillis, rtl)
        if (movingStart) {
            draft.start = time.coerceIn(0L, draft.end - min)
        } else {
            draft.end = time.coerceIn(draft.start + min, durationMillis)
        }
    }
}

/** The bars, as many as fit, the part kept in [kept] and the rest in [cut] at 38%. */
@Suppress("LongParameterList") // What it draws, its sizes, its direction and its two colors.
private fun DrawScope.drawBars(
    trim: VoiceTrim,
    draft: TrimDraft,
    barWidth: Float,
    gap: Float,
    rtl: Boolean,
    kept: Color,
    cut: Color,
) {
    val step = barWidth + gap
    val bars = ((size.width + gap) / step).toInt().coerceAtLeast(1)
    val levels = trim.levels
    val duration = trim.durationMillis.coerceAtLeast(1)
    for (bar in 0 until bars) {
        val level = if (levels.isEmpty()) 0f else levels[bar * levels.size / bars]
        val isKept = bar * duration / bars in draft.start..draft.end
        val x = bar * step
        val height = barWidth + (size.height - barWidth) * level
        drawRoundRect(
            color = if (isKept) kept else cut.copy(alpha = CUT_ALPHA),
            topLeft = Offset(if (rtl) size.width - x - barWidth else x, (size.height - height) / 2),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2),
        )
    }
}

/** The two handles, [width] wide and as tall as the waveform, in [color]. */
private fun DrawScope.drawHandles(
    trim: VoiceTrim,
    draft: TrimDraft,
    width: Float,
    rtl: Boolean,
    color: Color,
) {
    val duration = trim.durationMillis.coerceAtLeast(1)
    listOf(draft.start, draft.end).forEach { handle ->
        val x = size.width * handle / duration - width / 2
        val left = (if (rtl) size.width - x - width else x).coerceIn(0f, size.width - width)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(width / 2),
        )
    }
}

/** Screen readers' way to move each end a second at a time. */
@Composable
private fun trimActions(
    trim: VoiceTrim,
    start: Long,
    end: Long,
    onEvent: (VoiceEvent) -> Unit,
): List<CustomAccessibilityAction> {
    val startLater = stringResource(R.string.voice_trim_start_later)
    val startEarlier = stringResource(R.string.voice_trim_start_earlier)
    val endEarlier = stringResource(R.string.voice_trim_end_earlier)
    val endLater = stringResource(R.string.voice_trim_end_later)
    val min = ChatRecorder.MIN_DURATION_MILLIS
    val step = MILLIS_PER_SECOND

    fun action(
        label: String,
        newStart: Long,
        newEnd: Long,
    ) = CustomAccessibilityAction(label) {
        onEvent(VoiceEvent.Trim(newStart, newEnd))
        true
    }
    return buildList {
        if (start + step + min <= end) add(action(startLater, start + step, end))
        if (start > 0) add(action(startEarlier, (start - step).coerceAtLeast(0), end))
        if (end - step - min >= start) add(action(endEarlier, start, end - step))
        if (end < trim.durationMillis) add(action(endLater, start, (end + step).coerceAtMost(trim.durationMillis)))
    }
}

/** The time under [x] of a waveform [width] wide showing [durationMillis], mirrored right to left. */
private fun timeAt(
    x: Float,
    width: Float,
    durationMillis: Long,
    rtl: Boolean,
): Long {
    if (width <= 0f) return 0L
    val fraction = ((if (rtl) width - x else x) / width).coerceIn(0f, 1f)
    return (fraction * durationMillis).toLong()
}

private const val MILLIS_PER_SECOND = 1_000L

/** The handles are as wide as two bars, so they stand apart from them. */
private const val HANDLE_WIDTH = 2f

/** The cut parts' bars: Material's disabled content opacity. */
private const val CUT_ALPHA = 0.38f
