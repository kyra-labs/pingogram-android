package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.kyralabs.pingogram.core.ui.transition.LocalNavAnimatedVisibilityScope
import kotlinx.coroutines.flow.first

/**
 * Whether the viewer's way in has ended: its container transform settled. The bottom bar (the caption, a video's
 * controls, the toolbar with a tooltip per button and its menu) composes only then, not in the frame the viewer first
 * composes its pages, which also starts the transform (`docs/audit/performance-review-2026-11.md`, M2); the bars
 * fade in anyway, and the photo is what the transform shows. Once true it stays true, also on the way out. True at
 * once without a destination's transition (previews, tests of the screen alone) and for a viewer restored on screen.
 */
@Composable
internal fun rememberEnterSettled(): Boolean {
    val transition = LocalNavAnimatedVisibilityScope.current?.transition
    var settled by remember(transition) { mutableStateOf(transition == null) }
    if (transition != null && !settled) {
        LaunchedEffect(transition) {
            snapshotFlow { transition.currentState == EnterExitState.Visible }.first { it }
            settled = true
        }
    }
    return settled
}
