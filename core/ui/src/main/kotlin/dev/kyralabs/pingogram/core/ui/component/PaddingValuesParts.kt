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

/**
 * The sides of this padding alone, read when asked, e.g. to keep something above a body's content clear of the system
 * bars and cutouts at the sides, as the content is.
 */
fun PaddingValues.sidesOnly(): PaddingValues = BelowTopOf(this, top = 0.dp, bottom = false)

/**
 * This padding with [extra] more at the bottom, read when asked, e.g. so the end of a list rests clear of a floating
 * action button above the bottom it already keeps clear of.
 */
fun PaddingValues.plusBottom(extra: Dp): PaddingValues = PlusBottom(this, extra)

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
    private val bottom: Boolean = true,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = top

    override fun calculateBottomPadding(): Dp = if (bottom) padding.calculateBottomPadding() else 0.dp

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateRightPadding(layoutDirection)
}

private class PlusBottom(
    private val padding: PaddingValues,
    private val extra: Dp,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = padding.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = padding.calculateBottomPadding() + extra

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateRightPadding(layoutDirection)
}
