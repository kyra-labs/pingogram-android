package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper

/**
 * How conversations look with the user's Chat appearance settings (`REQUIREMENTS.md` FR-S1, [ChatAppearance]), from
 * [PingogramTheme], read as [MaterialTheme.chatStyle]:
 *
 * @property messageText `bodyLarge` at the chosen size, its optical size following it (`docs/DESIGN_SYSTEM.md`
 *   §3.2): the text of messages.
 * @property bubbleShapes the bubbles' shapes with the chosen round corners in place of `extraLarge`
 *   (`docs/DESIGN_SYSTEM.md` §4); the joined corners stay `extraSmall`.
 * @property wallpaper the conversation's background, or null for none (the sheet's `surface`).
 */
@Immutable
class ChatStyle(
    val messageText: TextStyle,
    val bubbleShapes: MessageBubbleShapes,
    val wallpaper: Brush?,
) {
    override fun equals(other: Any?): Boolean =
        other is ChatStyle &&
            other.messageText == messageText &&
            other.bubbleShapes == bubbleShapes &&
            other.wallpaper == wallpaper

    override fun hashCode(): Int = (messageText.hashCode() * 31 + bubbleShapes.hashCode()) * 31 + wallpaper.hashCode()
}

/** The chat appearance of the enclosing [PingogramTheme] (`docs/DESIGN_SYSTEM.md` §3.2, §4). */
val MaterialTheme.chatStyle: ChatStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalChatStyle.current

internal val LocalChatStyle =
    staticCompositionLocalOf {
        ChatStyle(pingogramTypography().bodyLarge, MessageBubbleShapes(PingogramShapes), wallpaper = null)
    }

/** The [ChatStyle] of [appearance] with this theme's [shapes] and [colorScheme]. */
internal fun chatStyle(
    appearance: ChatAppearance,
    messageText: TextStyle,
    shapes: Shapes,
    colorScheme: ColorScheme,
): ChatStyle =
    ChatStyle(
        messageText = messageText,
        bubbleShapes = MessageBubbleShapes(shapes.copy(extraLarge = appearance.bubbleCorners.shape(shapes))),
        wallpaper = appearance.wallpaper.brush(colorScheme),
    )

/** The shape scale's step these corners are. */
fun BubbleCorners.shape(shapes: Shapes) =
    when (this) {
        BubbleCorners.Medium -> shapes.medium
        BubbleCorners.Large -> shapes.large
        BubbleCorners.LargeIncreased -> shapes.largeIncreased
        BubbleCorners.ExtraLarge -> shapes.extraLarge
        BubbleCorners.ExtraLargeIncreased -> shapes.extraLargeIncreased
    }

/**
 * This wallpaper drawn from [colorScheme]'s roles: null for none; `surfaceContainerLow` for neutral; a role as a light
 * tint over `surface` ([WALLPAPER_TINT], as Material's tonal state layers), or a top-to-bottom gradient between two
 * tints. Light enough that both bubble colors (`primaryContainer`, `surfaceContainerHigh`) stand out on it in every
 * theme and contrast level.
 */
fun ChatWallpaper.brush(colorScheme: ColorScheme): Brush? {
    fun tint(role: Color) = role.copy(alpha = WALLPAPER_TINT).compositeOver(colorScheme.surface)
    val primary = tint(colorScheme.primary)
    val secondary = tint(colorScheme.secondary)
    val tertiary = tint(colorScheme.tertiary)
    return when (this) {
        ChatWallpaper.None -> null
        ChatWallpaper.Neutral -> SolidColor(colorScheme.surfaceContainerLow)
        ChatWallpaper.Primary -> SolidColor(primary)
        ChatWallpaper.Secondary -> SolidColor(secondary)
        ChatWallpaper.Tertiary -> SolidColor(tertiary)
        ChatWallpaper.PrimaryToTertiary -> Brush.verticalGradient(listOf(primary, tertiary))
        ChatWallpaper.SecondaryToPrimary -> Brush.verticalGradient(listOf(secondary, primary))
        ChatWallpaper.TertiaryToSecondary -> Brush.verticalGradient(listOf(tertiary, secondary))
        ChatWallpaper.NeutralToPrimary -> Brush.verticalGradient(listOf(colorScheme.surfaceContainerLow, primary))
    }
}

/** How much of a role tints `surface` in a wallpaper: Material's dragged state layer, a clear but quiet tint. */
private const val WALLPAPER_TINT = 0.16f
