package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex

/**
 * The composer's send button ([send]) and microphone ([record]) in one slot, the one shown while [showSend] or not:
 * they overlap, so the slot keeps the width of the one shown and the pill beside it never changes width as they
 * morph. The morph is one spring (the motion scheme's fast spatial spec) read in each button's graphics layer, the
 * leaving one shrinking and fading as the coming one grows in, so no frame of it recomposes; a button is composed
 * only while it shows or leaves. The one leaving lies under the other and is hidden from screen readers.
 */
@Composable
internal fun SendOrRecord(
    showSend: Boolean,
    send: @Composable () -> Unit,
    record: @Composable () -> Unit,
) {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val progress = remember { Animatable(if (showSend) 1f else 0f) }
    LaunchedEffect(showSend) { progress.animateTo(if (showSend) 1f else 0f, spec) }
    // Changes only as the morph starts and ends (the spring overshoots, so its value would cross 1 again): the button
    // leaving stays composed while it runs, not a frame more.
    val morphing = progress.isRunning
    Box(contentAlignment = Alignment.Center) {
        if (morphing || !showSend) {
            MorphLayer(shown = !showSend, fraction = { 1f - progress.value }, content = record)
        }
        if (morphing || showSend) {
            MorphLayer(shown = showSend, fraction = { progress.value }, content = send)
        }
    }
}

/**
 * [content] at [fraction] of the way in (0 gone, 1 shown): scaled from [HIDDEN_SCALE] and faded, in its graphics layer.
 * Above the other while [shown]; otherwise below it and hidden from screen readers.
 */
@Composable
private fun MorphLayer(
    shown: Boolean,
    fraction: () -> Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .zIndex(if (shown) 1f else 0f)
                .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
                .graphicsLayer {
                    val value = fraction()
                    val scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * value
                    scaleX = scale
                    scaleY = scale
                    alpha = value.coerceIn(0f, 1f)
                },
    ) { content() }
}

/** How small a button is when it has left: it grows from here, so the morph reads as one button changing. */
private const val HIDDEN_SCALE = 0.6f
