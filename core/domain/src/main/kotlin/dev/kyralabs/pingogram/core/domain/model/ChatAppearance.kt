package dev.kyralabs.pingogram.core.domain.model

import kotlin.math.roundToInt

/**
 * How conversations look (`REQUIREMENTS.md` FR-S1, Chat settings; `docs/DESIGN_SYSTEM.md` §3.2, §4): the size of
 * message text, the corners of bubbles and the wallpaper behind them. Part of [AppearanceSettings]; the defaults are
 * the conversation as it looks before the user changes anything.
 *
 * @property textScale what message text's size (`bodyLarge`) is multiplied by, [MIN_TEXT_SCALE]–[MAX_TEXT_SCALE] in
 *   steps of [TEXT_SCALE_STEP]; on top of the system's font scale.
 * @property bubbleCorners the shape scale's step for a bubble's round corners.
 * @property wallpaper what the conversation's messages sit on.
 */
data class ChatAppearance(
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val bubbleCorners: BubbleCorners = BubbleCorners.ExtraLarge,
    val wallpaper: ChatWallpaper = ChatWallpaper.None,
) {
    companion object {
        const val DEFAULT_TEXT_SCALE = 1f
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.3f
        const val TEXT_SCALE_STEP = 0.05f

        /** [scale] on the nearest step within the range: what a stored or dragged value becomes. */
        fun textScaleOf(scale: Float): Float {
            if (scale.isNaN()) return DEFAULT_TEXT_SCALE
            val percent = (scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE) * PERCENT).roundToInt()
            // In whole percents, so every step is the float nearest its value (0.85 + 9 × 0.05 is not 1.3).
            val step = (percent - MIN_PERCENT + STEP_PERCENT / 2) / STEP_PERCENT
            return (MIN_PERCENT + step * STEP_PERCENT) / PERCENT
        }

        private const val PERCENT = 100f
        private const val MIN_PERCENT = 85
        private const val STEP_PERCENT = 5
    }
}

/**
 * A bubble's round corners, as steps of the Material 3 Expressive shape scale (12, 16, 20, 28 and 32 dp). Where a
 * bubble meets another message of its group the corners stay `extraSmall` whatever this is. [ExtraLarge], the
 * default, makes a one-line bubble a full pill.
 */
enum class BubbleCorners {
    Medium,
    Large,
    LargeIncreased,
    ExtraLarge,
    ExtraLargeIncreased,
}

/**
 * The conversation's background: none (the conversation's `surface`), one of the theme's roles as a light tint, or a
 * gradient between two of them; never an image (image wallpapers are in the backlog). Drawn from the current theme,
 * so they follow the wallpaper's colors, the color theme, dark theme and contrast.
 */
enum class ChatWallpaper {
    None,
    Neutral,
    Primary,
    Secondary,
    Tertiary,
    PrimaryToTertiary,
    SecondaryToPrimary,
    TertiaryToSecondary,
    NeutralToPrimary,
}
