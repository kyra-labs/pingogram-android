package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Leaves selection mode with back while [enabled] (`docs/ARCHITECTURE.md`, Navigation: `PredictiveBackHandler` for
 * selection mode), in the chat list and in a conversation: the gesture's progress, for the selection toolbar to follow
 * (`Modifier.predictiveBackScale`, `docs/DESIGN_SYSTEM.md` §5), and [onBack] once it completes. A cancelled gesture
 * springs back on the motion scheme's fast spatial spring.
 */
@Composable
fun rememberSelectionBack(
    enabled: Boolean,
    onBack: () -> Unit,
): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            currentOnBack()
        } finally {
            // Also after a cancelled gesture, when this coroutine can no longer animate.
            scope.launch { progress.animateTo(0f, settle) }
        }
    }
    return progress
}
