package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.time.countdownFraction

/**
 * A countdown's progress, determinate and wavy (`docs/DESIGN_SYSTEM.md` §5): a [CircularWavyProgressIndicator] whose
 * arc shrinks as [secondsLeft] runs down from [totalSeconds], gliding from one whole second to the next on the effects
 * spring of `MaterialTheme.motionScheme`. The progress is read while drawing, so the glide never recomposes. Hidden
 * from screen readers: the countdown's text says the same, at most every 15 seconds. [content] sits in its middle.
 */
@Composable
fun CountdownIndicator(
    secondsLeft: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    content: @Composable () -> Unit = {},
) {
    val fraction by animateFloatAsState(
        targetValue = countdownFraction(secondsLeft, totalSeconds),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "Countdown",
    )
    Box(
        modifier = modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.matchParentSize(),
            color = color,
            trackColor = trackColor,
        )
        content()
    }
}
