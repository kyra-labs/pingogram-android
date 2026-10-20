package dev.kyralabs.pingogram.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.searchButtonTransform
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The rail's primary action: global search, as the floating bar's detached button is in compact windows. The collapsed
 * rail shows a FAB, the expanded one an extended FAB with its label (`docs/DESIGN_SYSTEM.md` §6), both in the FAB's
 * `large` corners. The rail's header places it at its start edge, so it brings its own start padding, as in Material's
 * rail samples. It grows into search and back ([SearchButtonSlot]), and is away while search is [searchOpen].
 */
@Composable
internal fun RailSearchButton(
    navigationSuiteType: NavigationSuiteType,
    searchOpen: Boolean,
    search: GlobalSearchState,
    onClick: () -> Unit,
) {
    val expanded = navigationSuiteType == NavigationSuiteType.WideNavigationRailExpanded
    val icon = painterResource(UiR.drawable.ic_search)
    val label = stringResource(R.string.nav_search)
    val shape = MaterialTheme.shapes.large
    SearchButtonSlot(
        shown = !searchOpen,
        search = search,
        shape = shape,
        // The extended and the collapsed button differ in size: each keeps its own place.
        sizeKey = expanded,
        modifier = Modifier.padding(start = RAIL_HEADER_START_PADDING),
    ) { transform ->
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                icon = { Icon(painter = icon, contentDescription = null) },
                text = { Text(label) },
                modifier = transform,
                shape = shape,
            )
        } else {
            FloatingActionButton(onClick = onClick, modifier = transform, shape = shape) {
                Icon(painter = icon, contentDescription = label)
            }
        }
    }
}

/** Centers a 56 dp FAB over the items of the 96 dp wide collapsed rail; Material has no token for it. */
private val RAIL_HEADER_START_PADDING = 20.dp

/**
 * Where the search button sits, the floating navigation bar's or the rail's (`docs/DESIGN_SYSTEM.md` §5, §6): the
 * [button] while [shown], given the modifier of its container transform into search ([searchButtonTransform], in
 * [shape]; none without [search]). While search shows the app hides the button, and the button and search share their
 * bounds as one comes and the other goes: the button grows into search and search shrinks back into it, on the motion
 * scheme's springs, each fading on the effects springs. Without the transform (reduced motion) it fades in and out.
 *
 * Hidden, the slot keeps the size the button had (per [sizeKey]), so the toolbar or the rail around it never moves.
 *
 * Only a slot that was placed takes part in the transform and in the semantics tree: `NavigationSuiteScaffold`
 * composes its primary action a second time for navigation bars and never places that copy beside a rail, and a
 * second, unplaced side of the same transform would have no bounds to grow from.
 */
@Composable
internal fun SearchButtonSlot(
    shown: Boolean,
    search: GlobalSearchState?,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    sizeKey: Any? = null,
    button: @Composable (transform: Modifier) -> Unit,
) {
    val keptSize = remember(sizeKey) { KeptSize() }
    var placed by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    Box(
        modifier
            .keepingSize(keptSize)
            .onPlaced { placed = true }
            .then(if (placed) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            ProvideNavAnimatedVisibilityScope(this) {
                button(if (search != null && placed) Modifier.searchButtonTransform(search, shape) else Modifier)
            }
        }
    }
}

/** The largest size content laid out with [keepingSize] had; a plain holder, written and read in the layout phase. */
private class KeptSize {
    var size = IntSize.Zero
}

/** Lays this content out at least as large as it ever was ([kept]), so it keeps its place while it is gone. */
private fun Modifier.keepingSize(kept: KeptSize): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        kept.size = IntSize(maxOf(kept.size.width, placeable.width), maxOf(kept.size.height, placeable.height))
        val width = kept.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = kept.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) { placeable.place(0, 0) }
    }
