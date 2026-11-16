package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer

/**
 * A color that animates to [targetValue] on [animationSpec] (the theme's default effects spring) without recomposing
 * whatever shows it (`docs/PERFORMANCE.md` §4): pass it to material3's `Text(text, color = …)` or
 * `Icon(painter, tint = …)`, which read it in the draw phase, or read it in a `drawBehind`.
 */
@Composable
fun animateColorProducer(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = MaterialTheme.motionScheme.defaultEffectsSpec(),
): ColorProducer {
    val color = animateColorAsState(targetValue, animationSpec, label = "animateColorProducer")
    return remember(color) { ColorProducer { color.value } }
}
