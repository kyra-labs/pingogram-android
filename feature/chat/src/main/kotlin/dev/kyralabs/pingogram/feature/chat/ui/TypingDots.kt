package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.util.lerp
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * Three dots that pulse in turn while someone is doing something in the chat (`docs/DESIGN_SYSTEM.md` §5): each
 * brightens from a resting opacity to [color] and fades back on the motion scheme's fast effects spring, the next one
 * starting as it fades. The dots' opacities are read in the draw phase, so the pulse composes nothing again. With
 * reduced motion they stand still at full opacity. Decorative: the text beside them says what is happening, so nothing
 * is conveyed by the motion alone.
 *
 * They are as tall and as far apart as a fraction of [style]'s font size, so they grow with the font scale like the
 * text they precede.
 */
@Composable
internal fun TypingDots(
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    state: TypingDotsState = remember { TypingDotsState() },
) {
    val reducedMotion = isReducedMotion()
    if (!reducedMotion) {
        val spec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        LaunchedEffect(state, spec) { state.pulseForever(spec) }
    }
    val (dot, width) =
        with(LocalDensity.current) {
            (style.fontSize * DOT_EM).toDp() to
                (style.fontSize * (DOT_COUNT * DOT_EM + (DOT_COUNT - 1) * GAP_EM)).toDp()
        }
    Canvas(modifier.size(width = width, height = dot)) {
        val radius = size.height / 2
        val step = (size.width - size.height) / (DOT_COUNT - 1)
        for (index in 0 until DOT_COUNT) {
            val alpha = if (reducedMotion) 1f else lerp(REST_ALPHA, 1f, state.level(index))
            drawCircle(color, radius, Offset(radius + index * step, radius), alpha)
        }
    }
}

/** How far each of the [TypingDots] has brightened, from 0 at rest to 1, as snapshot state the dots draw from. */
@Stable
internal class TypingDotsState {
    private val dots = List(DOT_COUNT) { Animatable(0f) }

    /** How far dot [index] has brightened; read it in the draw phase. */
    fun level(index: Int): Float = dots[index].value

    /**
     * Pulses the dots in turn on [spec] until cancelled. Under an [InfiniteAnimationPolicy] (Compose UI tests install
     * one, which cancels such loops) it runs as the policy allows.
     */
    suspend fun pulseForever(spec: AnimationSpec<Float>) {
        val policy = currentCoroutineContext()[InfiniteAnimationPolicy]
        if (policy == null) pulse(spec) else policy.onInfiniteOperation { pulse(spec) }
    }

    /** Pulses the dots in turn on [spec], wave after wave, until cancelled. */
    suspend fun pulse(spec: AnimationSpec<Float>): Nothing {
        while (true) {
            coroutineScope {
                for (dot in dots) {
                    dot.animateTo(1f, spec)
                    launch { dot.animateTo(0f, spec) }
                }
            }
        }
    }
}

private const val DOT_COUNT = 3

// Each dot a third of the font size wide, a quarter of it apart: about as wide as an ellipsis of the text.
private const val DOT_EM = 0.34f
private const val GAP_EM = 0.25f

// Resting dots at Material's disabled-content opacity, so the brightening one stands out.
private const val REST_ALPHA = 0.38f
