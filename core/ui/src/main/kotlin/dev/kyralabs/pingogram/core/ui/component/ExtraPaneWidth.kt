package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.kyralabs.pingogram.core.ui.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// The Chats tab's third pane on extra-large windows (docs/DESIGN_SYSTEM.md §7): its width, its drag handle, and the
// window its content lays itself out for.

/**
 * The width of the extra pane, the third pane beside the detail pane on extra-large windows (`docs/DESIGN_SYSTEM.md`
 * §7), which its [ExtraPaneDragHandle] changes between Material's fixed widths (360 and 412 dp) and a third of the
 * scaffold. Material's own drag handle resizes only two panes, the list and the detail, so the extra pane has its own.
 * Remembered with [rememberExtraPaneWidth], which saves it, so it survives rotation and process death.
 */
@Stable
class ExtraPaneWidth internal constructor(
    initial: Dp,
) {
    /** The extra pane's preferred width, for the scaffold's `Modifier.preferredWidth` on the pane. */
    var width: Dp by mutableStateOf(initial)
        internal set

    /** The extra pane's edge that faces the detail pane, in the root's pixels; NaN until it is placed. */
    internal var paneEdge by mutableFloatStateOf(Float.NaN)

    companion object {
        /** Where the pane rests: Material's preferred pane width on extra-large windows. */
        internal val Default = 412.dp

        internal val Saver: Saver<ExtraPaneWidth, Float> = Saver({ it.width.value }, { ExtraPaneWidth(it.dp) })
    }
}

/** An [ExtraPaneWidth] at its default width, saved with the composition. */
@Composable
fun rememberExtraPaneWidth(): ExtraPaneWidth =
    rememberSaveable(saver = ExtraPaneWidth.Saver) { ExtraPaneWidth(ExtraPaneWidth.Default) }

/**
 * Tells [state] where this pane, the extra pane, is placed, so that its drag handle sits in the space between it and
 * the detail pane. On the pane's own modifier, before any padding.
 */
@Composable
fun Modifier.extraPaneEdge(state: ExtraPaneWidth): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onPlaced { coordinates ->
        val start = coordinates.positionInRoot().x
        state.paneEdge = if (rtl) start + coordinates.size.width else start
    }
}

/**
 * The drag handle of the extra pane ([ExtraPaneWidth]), drawn above the scaffold it fills, in the middle of the space
 * ([spacerSize], the directive's) between the detail pane and the extra pane: Material's [VerticalDragHandle] on a
 * touch target of the minimum interactive size. A drag resizes the extra pane between 360 dp and a third of the
 * scaffold, mirrored right to left, and settles on the nearest of [ExtraPaneWidth]'s widths on the motion scheme's
 * default spatial spring. Accessibility services hear its width and move it to the next width with a click.
 */
@Composable
fun ExtraPaneDragHandle(
    state: ExtraPaneWidth,
    spacerSize: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val interactionSource = remember { MutableInteractionSource() }
    val touchTarget = LocalMinimumInteractiveComponentSize.current
    // The overlay's start in the root's pixels, and its width, which the widest anchor depends on.
    var overlayStart by remember { mutableFloatStateOf(Float.NaN) }
    var overlayWidth by remember { mutableStateOf(0.dp) }
    val anchors = extraPaneAnchors(overlayWidth)
    val settle = { target: Dp ->
        scope.launch {
            animate(state.width.value, target.value, animationSpec = spec) { value, _ -> state.width = value.dp }
        }
    }
    val widthName = extraPaneWidthName(anchors, state.width)
    Box(
        modifier
            .fillMaxSize()
            .onPlaced { coordinates ->
                overlayStart = coordinates.positionInRoot().x
                overlayWidth = with(density) { coordinates.size.width.toDp() }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // Centered in the space before the pane's edge (after it, right to left).
                    val half = spacerSize.toPx() / 2
                    val center = state.paneEdge - overlayStart + if (rtl) half else -half
                    val x = if (center.isNaN()) 0 else (center - touchTarget.toPx() / 2).roundToInt()
                    IntOffset(x, 0)
                }.size(touchTarget)
                .systemGestureExclusion()
                .draggable(
                    state =
                        rememberDraggableState { delta ->
                            val change = with(density) { delta.toDp() }
                            state.width =
                                (state.width + if (rtl) change else -change).coerceIn(anchors.first(), anchors.last())
                        },
                    orientation = Orientation.Horizontal,
                    interactionSource = interactionSource,
                    onDragStopped = { settle(anchors.minBy { (it - state.width).value.absoluteValue }) },
                ).handleSemantics(widthName) {
                    settle(anchors.firstOrNull { it > state.width + AnchorTolerance } ?: anchors.first())
                },
            contentAlignment = Alignment.Center,
        ) {
            VerticalDragHandle(interactionSource = interactionSource)
        }
    }
}

/** The handle's semantics: what it is, the pane's width ([widthName]), and a click that moves it to [next] width. */
@Composable
private fun Modifier.handleSemantics(
    @StringRes widthName: Int,
    next: () -> Unit,
): Modifier {
    val description = stringResource(R.string.pane_extra_drag_handle)
    val widthDescription = stringResource(widthName)
    val nextLabel = stringResource(R.string.pane_extra_width_next)
    return semantics {
        contentDescription = description
        stateDescription = widthDescription
        onClick(label = nextLabel) {
            next()
            true
        }
    }
}

/** Material's fixed widths, and a third of a scaffold [width] wide where that is wider still. */
private fun extraPaneAnchors(width: Dp): List<Dp> {
    val third = width * WIDE_FRACTION
    return if (third > ExtraPaneWidth.Default + AnchorTolerance) {
        listOf(360.dp, ExtraPaneWidth.Default, third)
    } else {
        listOf(360.dp, ExtraPaneWidth.Default)
    }
}

/** The name of the extra pane's [width] among [anchors]: narrow, medium or wide. */
private fun extraPaneWidthName(
    anchors: List<Dp>,
    width: Dp,
): Int =
    when (anchors.indexOf(anchors.minBy { (it - width).value.absoluteValue })) {
        0 -> R.string.pane_extra_width_narrow
        1 -> R.string.pane_extra_width_medium
        else -> R.string.pane_extra_width_wide
    }

/** The widest anchor of the extra pane, as a fraction of the scaffold's width. */
private const val WIDE_FRACTION = 1f / 3

/** How close two widths count as the same. */
private val AnchorTolerance = 1.dp

/**
 * [content] in a pane that it takes for the window: content that adapts to the window (its size class, through
 * `currentWindowAdaptiveInfoV2`, and [windowMargin]) lays itself out for the pane's width, e.g. a profile in the Chats
 * tab's extra pane shows its one-pane layout instead of its own supporting pane. The height, focus and keyboard
 * modifiers stay the window's.
 */
@Composable
fun PaneAsWindow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else window.containerSize.width
        val pane = remember(window, density, width) { PaneWindowInfo(window, density, width) }
        CompositionLocalProvider(LocalWindowInfo provides pane, content = content)
    }
}

/** [window], [width] pixels wide. */
private class PaneWindowInfo(
    private val window: WindowInfo,
    private val density: Density,
    private val width: Int,
) : WindowInfo {
    override val isWindowFocused: Boolean get() = window.isWindowFocused

    override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers

    override val containerSize: IntSize get() = IntSize(width, window.containerSize.height)

    override val containerDpSize: DpSize get() = with(density) { containerSize.toSize().toDpSize() }
}
