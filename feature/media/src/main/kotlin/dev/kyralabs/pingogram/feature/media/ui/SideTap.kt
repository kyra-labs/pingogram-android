package dev.kyralabs.pingogram.feature.media.ui

/** The share of the page's width, at each side, where a tap turns the page instead of showing the chrome. */
internal const val SIDE_TAP_FRACTION = 0.15f

/**
 * Whether a tap at [x] of a page [width] wide is near its end side (true), its start side (false), or neither
 * (null). The end side is the right in left-to-right layouts and the left in right-to-left ones.
 */
internal fun sideOf(
    x: Float,
    width: Int,
    rtl: Boolean,
): Boolean? {
    val edge = width * SIDE_TAP_FRACTION
    val left =
        when {
            x < edge -> true
            x > width - edge -> false
            else -> return null
        }
    return left == rtl
}
