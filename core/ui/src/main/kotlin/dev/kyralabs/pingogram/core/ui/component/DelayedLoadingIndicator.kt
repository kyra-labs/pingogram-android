package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Material 3 Expressive's indeterminate [LoadingIndicator], shown only once loading has taken [delay], fading in on
 * the motion scheme's default effects spring (read in the draw phase). Content that loads sooner, such as a chat's
 * messages from TDLib's local database, never flashes it, and nothing composes or animates it meanwhile: its morphing
 * polygons are costly to set up in the frame that opens a screen (`docs/PERFORMANCE.md` §4).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
    delay: Duration = LoadingIndicatorDelay,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay)
        shown = true
    }
    if (shown) {
        val alpha = remember { Animatable(0f) }
        val fadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        LaunchedEffect(alpha) { alpha.animateTo(1f, fadeIn) }
        LoadingIndicator(modifier.graphicsLayer { this.alpha = alpha.value })
    }
}

/** How long loading may take before [DelayedLoadingIndicator] shows: shorter waits do not read as waiting. */
val LoadingIndicatorDelay: Duration = 150.milliseconds
