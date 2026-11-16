package dev.kyralabs.pingogram.feature.media.ui

/** The most of the page's width, at each side, that turns the page: the rest shows or hides the chrome. */
internal const val SIDE_TAP_MAX_FRACTION = 1f / 3

/**
 * Whether a tap at [x] of a page [width] wide is within [edge] pixels of its end side (true), its start side (false),
 * or neither (null); the edge is at most [SIDE_TAP_MAX_FRACTION] of the width. The end side is the right in
 * left-to-right layouts and the left in right-to-left ones.
 */
internal fun sideOf(
    x: Float,
    width: Int,
    edge: Float,
    rtl: Boolean,
): Boolean? {
    val zone = edge.coerceAtMost(width * SIDE_TAP_MAX_FRACTION)
    val left =
        when {
            x < zone -> true
            x > width - zone -> false
            else -> return null
        }
    return left == rtl
}

/** What a tap near a side of [page] does: turns to the page beside it, of [count], through [turnTo]. */
internal fun sideTurn(
    page: Int,
    count: Int,
    turnTo: (page: Int) -> Unit,
): (end: Boolean) -> Unit =
    { end ->
        val target = page + if (end) 1 else -1
        if (target in 0 until count) turnTo(target)
    }
