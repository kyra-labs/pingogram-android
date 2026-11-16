package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.kyralabs.pingogram.core.ui.component.revealHorizontally
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Selection mode as the list's rows show it: the messages selected ([selectedIds], null outside selection mode), those
 * whose checks show ([checkedIds]: the same, and the last selection while the checks leave with it), and how much of
 * the checks shows ([reveal]).
 */
@Stable
internal data class RowChecks(
    val selectedIds: ImmutableSet<Long>?,
    val checkedIds: ImmutableSet<Long>?,
    val reveal: SelectionReveal,
)

/** The list's [RowChecks] for [selectedIds] (null outside selection mode), kept while the checks leave with it. */
@Composable
internal fun rememberRowChecks(selectedIds: ImmutableSet<Long>?): RowChecks {
    // The checks leave with selection mode as they came, showing the selection they had meanwhile.
    val reveal = rememberSelectionReveal(selecting = selectedIds != null)
    val lastSelectedIds = remember { arrayOfNulls<ImmutableSet<Long>>(1) }
    if (selectedIds != null) lastSelectedIds[0] = selectedIds
    val checkedIds = selectedIds ?: lastSelectedIds[0]?.takeIf { reveal.showsChecks }
    return RowChecks(selectedIds, checkedIds, reveal)
}

/**
 * The rows' checks coming and going with selection mode (`docs/DESIGN_SYSTEM.md` §6 "Selection mode"): their width
 * on the motion scheme's fast spatial spring ([width], 0 to 1) and their opacity on its effects springs ([alpha]), one
 * animation for the whole list, read by every row in its layout and draw phases ([selectionReveal]), so entering or
 * leaving selection mode moves the bubbles beside the checks without recomposing a row in any frame.
 */
@Stable
internal class SelectionReveal(
    selecting: Boolean,
) {
    val width = Animatable(if (selecting) 1f else 0f)
    val alpha = Animatable(if (selecting) 1f else 0f)

    /** Whether the rows show their checks: in selection mode, or while they leave with it. */
    val showsChecks: Boolean by derivedStateOf { width.targetValue > 0f || width.isRunning }
}

/** The list's [SelectionReveal], following whether it is [selecting]. */
@Composable
internal fun rememberSelectionReveal(selecting: Boolean): SelectionReveal {
    val reveal = remember { SelectionReveal(selecting) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(selecting) {
        val target = if (selecting) 1f else 0f
        val fade = if (selecting) motionScheme.defaultEffectsSpec<Float>() else motionScheme.fastEffectsSpec()
        coroutineScope {
            launch { reveal.alpha.animateTo(target, fade) }
            reveal.width.animateTo(target, motionScheme.fastSpatialSpec())
        }
    }
    return reveal
}

/** Shows this check as much as [reveal] says: its width in the layout phase, its opacity in the draw phase. */
internal fun Modifier.selectionReveal(reveal: SelectionReveal): Modifier =
    revealHorizontally { reveal.width.value }.graphicsLayer { alpha = reveal.alpha.value }
