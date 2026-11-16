package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.AnimatedPaneScope
import androidx.compose.material3.adaptive.layout.ExtendedPaneScaffoldPaneScope
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneScaffoldValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// What the app's list-detail layouts share (docs/DESIGN_SYSTEM.md §2.2, §7): the Chats tab (chat list | conversation,
// and on extra-large windows a profile or a group's info beside it) and Settings (Settings home | a page) on expanded
// and wider windows.

/**
 * Each pane's own rounded container beside the other (`docs/DESIGN_SYSTEM.md` §2.2, §7): `extraLarge` corners on the
 * window's `surfaceContainer`, above the system navigation bar and the window margin, and the window margin from the
 * window's end after the [last] pane. With one pane ([twoPanes] false) the screen fills the window, as when pushed.
 */
@Composable
fun Modifier.paneContainer(
    twoPanes: Boolean,
    last: Boolean,
): Modifier {
    if (!twoPanes) return this
    val margin = MaterialTheme.spacing.windowMarginMedium
    val shape = MaterialTheme.shapes.extraLarge
    return this
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .padding(bottom = margin, end = if (last) margin else 0.dp)
        .clip(shape)
}

/**
 * Resizes the list pane between [ListPaneExpansionAnchors] (`docs/DESIGN_SYSTEM.md` §7): Material's drag handle in the
 * space between the panes, with the draggable's default semantics, so accessibility services can move it between the
 * anchors too.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldScope.PaneDragHandle(state: PaneExpansionState) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier =
            Modifier.paneExpansionDraggable(
                state = state,
                minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                interactionSource = interactionSource,
            ),
        interactionSource = interactionSource,
    )
}

/**
 * Material's fixed list widths (360 and 412 dp) and an even split, where a drag of the [PaneDragHandle] settles; the
 * list pane rests at the first.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ListPaneExpansionAnchors: List<PaneExpansionAnchor> =
    listOf(
        PaneExpansionAnchor.Offset.fromStart(360.dp),
        PaneExpansionAnchor.Offset.fromStart(412.dp),
        PaneExpansionAnchor.Proportion(0.5f),
    )

/**
 * Moves [navigator]'s scaffold to the panes of a new directive. The navigator moves it only when it navigates, so a
 * window resized without recreating the activity (one that crosses no size threshold of the app's resources) would
 * keep the old panes; a recreated activity starts with the right ones anyway.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FollowScaffoldDirective(navigator: ThreePaneScaffoldNavigator<*>) {
    LaunchedEffect(navigator, navigator.scaffoldDirective) {
        (navigator.scaffoldState as? MutableThreePaneScaffoldState)?.animateTo(navigator.scaffoldValue)
    }
}

/**
 * Shows [contentKey] in the detail pane, in place of what it shows (the history never holds more than the list and one
 * detail); nothing if it is shown. Both steps change the history before they suspend for their animations, so no frame
 * shows the pane empty in between: the second animation takes over from the first.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun <T> ThreePaneScaffoldNavigator<T>.showInDetailPane(
    contentKey: T,
    scope: CoroutineScope,
) {
    val current = currentDestination
    if (current?.pane == ListDetailPaneScaffoldRole.Detail && current.contentKey == contentKey) return
    if (current?.pane == ListDetailPaneScaffoldRole.Detail) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { navigateBack(BackNavigationBehavior.PopLatest) }
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) { navigateTo(ListDetailPaneScaffoldRole.Detail, contentKey) }
}

// The panes of the list-detail and supporting-pane scaffolds move on the motion scheme (docs/DESIGN_SYSTEM.md §5.1:
// panes move on defaultSpatialSpec, read from the scheme: their bounds, their enter and exit). material3-adaptive's
// own pane motion is a spring of the same values, hard-coded rather than read from the theme.

/**
 * A pane's motion from [motionScheme]: its [bounds] as it resizes or moves, on the default spatial spring. Made by
 * [paneMotion]. Its slides and expansions as it comes and goes stay material3-adaptive's, whose springs have the
 * expressive scheme's default spatial values already (`PaneMotionDefaults`, checked by `PaneMotionTest`).
 */
@Immutable
class PaneMotionSpecs internal constructor(
    motionScheme: MotionScheme,
) {
    val bounds: FiniteAnimationSpec<IntRect> = motionScheme.defaultSpatialSpec()
}

/** The [PaneMotionSpecs] of [motionScheme], the theme's by default, made once per scheme. */
@Composable
fun paneMotion(motionScheme: MotionScheme = MaterialTheme.motionScheme): PaneMotionSpecs =
    remember(motionScheme) { PaneMotionSpecs(motionScheme) }

/**
 * material3-adaptive's [AnimatedPane] with its bounds on [motion] (by default the theme's [paneMotion]), so every pane
 * of the app moves as one on the motion scheme; it comes and goes on the library's slides (see [PaneMotionSpecs]).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <R : PaneScaffoldRole, V : PaneScaffoldValue<R>> ExtendedPaneScaffoldPaneScope<R, V>.SchemeAnimatedPane(
    modifier: Modifier = Modifier,
    motion: PaneMotionSpecs = paneMotion(),
    content: @Composable AnimatedPaneScope.() -> Unit,
) {
    AnimatedPane(
        modifier = modifier,
        boundsAnimationSpec = motion.bounds,
        content = content,
    )
}
