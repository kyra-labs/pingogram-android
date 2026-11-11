package dev.kyralabs.pingogram.core.domain.model

/**
 * A name color Telegram offers (td_api.tl `accentColor`): its [id], and [builtInId], the one of the seven built-in
 * colors (0–6: red, orange, violet, green, cyan, blue, pink) it is drawn like where Pingogram has no colors of its own
 * for it. The built-in ones are [isBuiltIn]; the others come with Telegram Premium.
 */
data class AccentColorOption(
    val id: Int,
    val builtInId: Int,
) {
    val isBuiltIn: Boolean get() = id in 0 until BUILT_IN_ACCENT_COLORS

    companion object {
        /** How many built-in name colors Telegram has. */
        const val BUILT_IN_ACCENT_COLORS = 7
    }
}
