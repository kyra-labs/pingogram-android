package dev.kyralabs.pingogram.core.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.navigation.NavBackStackEntry

/**
 * Says the overlay destination this is composed in hides the screen below completely while [opaque] (read in the
 * layout phase, so it can follow a drag): once the overlay is shown in full, the screen below is not placed then, and
 * draws nothing ([OverlayHost], `docs/PERFORMANCE.md`, "Overdraw"). Nothing outside an overlay destination.
 */
@Composable
fun CoverBelow(opaque: () -> Boolean) {
    val cover = LocalOverlayCover.current ?: return
    DisposableEffect(cover, opaque) {
        cover.opaque = opaque
        onDispose { cover.opaque = NotOpaque }
    }
}

/** What an overlay says of the screen below it ([CoverBelow]), and whether it is shown in full; main thread only. */
internal class OverlayCover {
    var opaque: () -> Boolean by mutableStateOf(NotOpaque)
    var shownInFull by mutableStateOf(false)

    /** Whether the screen below shows nowhere: the overlay is shown in full and opaque. */
    val hidesBelow: Boolean get() = shownInFull && opaque()
}

/**
 * Keeps the [OverlayCover] composed here told whether its overlay is shown in full: [visible], its fade in ([state])
 * over, not going out.
 */
@Composable
internal fun ShownInFullEffect(
    visible: Boolean,
    state: MutableTransitionState<EnterExitState>,
) {
    val cover = LocalOverlayCover.current ?: return
    val shown = visible && state.isIdle && state.currentState == EnterExitState.Visible
    SideEffect { cover.shownInFull = shown }
}

/** Each overlay's [OverlayCover], by back stack entry, while it is composed; main thread only. */
internal class OverlayCovers {
    private val covers = HashMap<String, OverlayCover>()

    fun of(entry: NavBackStackEntry): OverlayCover = covers.getOrPut(entry.id) { OverlayCover() }

    /** Forgets the covers of overlays no longer [shown]. */
    fun keepOnly(shown: List<NavBackStackEntry>) {
        if (covers.size > shown.size) covers.keys.retainAll(shown.mapTo(HashSet()) { it.id })
    }
}

private val NotOpaque: () -> Boolean = { false }

/** The [OverlayCover] of the overlay destination composed here ([OverlayHost]). */
internal val LocalOverlayCover = staticCompositionLocalOf<OverlayCover?> { null }

/** Measured as ever, but placed, and so drawn, only while [hidden] is false; read in the layout phase. */
internal fun Modifier.placedUnless(hidden: () -> Boolean): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (!hidden()) placeable.place(0, 0) }
    }
