package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Parts of a Scaffold's inner padding, for a body placed below its top bar that pads its own content with the rest.
// The parts read the padding when asked: Modifier.padding asks in the layout phase, so a collapsing top app bar,
// which changes the Scaffold's inner padding on every frame, moves what they pad without recomposing
// (docs/PERFORMANCE.md §4).

/** The top of this padding alone, read when asked, e.g. to place a body below a `Scaffold`'s top bar. */
fun PaddingValues.topOnly(): PaddingValues = TopOf(this)

/**
 * This padding with [top] in place of its own top, read when asked, e.g. to pad the content of a body placed below a
 * `Scaffold`'s top bar ([topOnly]) with the rest: the sides and the bottom (system bars, the floating navigation bar).
 */
fun PaddingValues.withTop(top: Dp): PaddingValues = BelowTopOf(this, top)

private class TopOf(
    private val padding: PaddingValues,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = padding.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = 0.dp

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

private class BelowTopOf(
    private val padding: PaddingValues,
    private val top: Dp,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = top

    override fun calculateBottomPadding(): Dp = padding.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateRightPadding(layoutDirection)
}
