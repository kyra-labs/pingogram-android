package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.Waveform
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * The levels in a voice message's waveform [bytes] (`Waveform`), each from 0 to 31. Telegram packs them as 5-bit
 * values one after the other, least significant bit first: level `i` is bits `5i` to `5i + 4` of the bytes read as
 * one little-endian number. Bits left over at the end, too few for a level, are padding. Empty for no bytes.
 */
internal fun decodeWaveform(bytes: ByteArray): IntArray =
    IntArray(bytes.size * Byte.SIZE_BITS / BITS_PER_LEVEL) { index ->
        val bit = index * BITS_PER_LEVEL
        val byte = bit / Byte.SIZE_BITS
        val low = bytes[byte].toInt() and BYTE_MASK
        val high = if (byte + 1 < bytes.size) bytes[byte + 1].toInt() and BYTE_MASK else 0
        (low or (high shl Byte.SIZE_BITS)) shr (bit % Byte.SIZE_BITS) and MAX_LEVEL
    }

/** [levels] (each clamped to 0–31) packed as [decodeWaveform] reads them, for previews and tests. */
internal fun encodeWaveform(levels: IntArray): ByteArray {
    val bytes = ByteArray((levels.size * BITS_PER_LEVEL + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS)
    levels.forEachIndexed { index, level ->
        val bit = index * BITS_PER_LEVEL
        val value = level.coerceIn(0, MAX_LEVEL) shl (bit % Byte.SIZE_BITS)
        val byte = bit / Byte.SIZE_BITS
        bytes[byte] = (bytes[byte].toInt() or (value and BYTE_MASK)).toByte()
        if (byte + 1 < bytes.size) {
            bytes[byte + 1] = (bytes[byte + 1].toInt() or (value shr Byte.SIZE_BITS)).toByte()
        }
    }
    return bytes
}

/**
 * [levels] (0–31) as [barCount] bars from 0 (silent) to 1 (loudest level): each bar is the loudest level of the stretch
 * of [levels] it covers, so short peaks stay visible; with more bars than levels, levels repeat. All 0 without levels.
 */
internal fun waveformBars(
    levels: IntArray,
    barCount: Int,
): FloatArray =
    when {
        barCount <= 0 -> {
            FloatArray(0)
        }

        levels.isEmpty() -> {
            FloatArray(barCount)
        }

        else -> {
            FloatArray(barCount) { bar ->
                val start = (bar.toLong() * levels.size / barCount).toInt()
                val end = maxOf(start + 1, ((bar + 1).toLong() * levels.size / barCount).toInt())
                var loudest = 0
                for (index in start until end) loudest = maxOf(loudest, levels[index])
                loudest.toFloat() / MAX_LEVEL
            }
        }
    }

/**
 * A voice message's [waveform] as bars `waveformBarWidth` wide and `waveformBarGap` apart, centred and at least as
 * tall as they are wide, so silence shows as dots. The bars up to [progress] (0–1, read in the draw phase) are in the
 * bubble's accent, the rest in its `waveformUnplayed`; the bars are built once per size ([drawWithCache]). It runs
 * from the start side, so right to left in RTL layouts.
 *
 * Tapping or dragging along it calls [onSeek] with the fraction under the finger. Screen readers get this through the
 * voice message's semantics instead.
 */
@Composable
internal fun Waveform(
    waveform: Waveform,
    progress: () -> Float,
    colors: BubbleColors,
    onSeek: (Float) -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val sizes = MaterialTheme.sizes
    val levels = remember(waveform) { decodeWaveform(waveform.bytes) }
    val barWidth = sizes.waveformBarWidth
    val barGap = sizes.waveformBarGap
    val playedColor = colors.accent
    val unplayedColor = colors.waveformUnplayed
    val currentOnSeek by rememberUpdatedState(onSeek)
    val direction = LocalLayoutDirection.current
    Spacer(
        modifier
            .width(width)
            .height(sizes.waveformHeight)
            .seekGestures(direction) { currentOnSeek }
            .drawWithCache {
                val barWidthPx = barWidth.toPx()
                val stepPx = barWidthPx + barGap.toPx()
                val bars = waveformBars(levels, ((size.width + barGap.toPx()) / stepPx).toInt())
                val rtl = layoutDirection == LayoutDirection.Rtl
                val path = Path()
                bars.forEachIndexed { index, level ->
                    val height = barWidthPx + (size.height - barWidthPx) * level
                    val start = index * stepPx
                    val left = if (rtl) size.width - start - barWidthPx else start
                    val top = (size.height - height) / 2
                    path.addRoundRect(
                        RoundRect(left, top, left + barWidthPx, top + height, CornerRadius(barWidthPx / 2)),
                    )
                }
                onDrawBehind {
                    drawPath(path, unplayedColor)
                    val played = size.width * progress().coerceIn(0f, 1f)
                    if (played > 0f) {
                        val left = if (rtl) size.width - played else 0f
                        clipRect(left = left, right = left + played) { drawPath(path, playedColor) }
                    }
                }
            },
    )
}

/**
 * Seeking by tapping or dragging along this seek bar: calls the callback [onSeek] returns (read on every event, so it
 * may change between recompositions) with the fraction under the finger ([seekFraction]).
 */
internal fun Modifier.seekGestures(
    direction: LayoutDirection,
    onSeek: () -> (Float) -> Unit,
): Modifier =
    pointerInput(direction) {
        detectTapGestures { offset -> onSeek()(seekFraction(offset.x, size.width, direction)) }
    }.pointerInput(direction) {
        detectHorizontalDragGestures { change, _ -> onSeek()(seekFraction(change.position.x, size.width, direction)) }
    }

/** The fraction (0–1) of a seek bar [width] pixels wide at [x], from its start side. */
internal fun seekFraction(
    x: Float,
    width: Int,
    layoutDirection: LayoutDirection,
): Float {
    if (width <= 0) return 0f
    val fraction = (x / width).coerceIn(0f, 1f)
    return if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
}

private const val BITS_PER_LEVEL = 5
private const val MAX_LEVEL = 0x1F
private const val BYTE_MASK = 0xFF
