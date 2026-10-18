package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that have no Material 3 role, derived from the active [ColorScheme] instead of
 * hard-coded (`docs/DESIGN_SYSTEM.md` §2.2).
 *
 * @property online presence dot for online users: Material green harmonized toward `primary`.
 */
@Immutable
data class PingogramColors(
    val online: Color,
)

internal fun pingogramColors(
    colorScheme: ColorScheme,
    dark: Boolean,
): PingogramColors = PingogramColors(online = harmonize(if (dark) OnlineDark else OnlineLight, colorScheme.primary))

val LocalPingogramColors = staticCompositionLocalOf<PingogramColors> { error("Wrap the content in PingogramTheme") }

/** The [PingogramColors] provided by `PingogramTheme`. */
val MaterialTheme.pingogramColors: PingogramColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramColors.current

/** Container and content colors of an avatar placeholder. */
@Immutable
data class AvatarColors(
    val container: Color,
    val content: Color,
)

/**
 * One of the seven avatar placeholder role pairs from §2.2, chosen by [id] (a TDLib user or chat id,
 * which can be negative) so the same chat always gets the same colors.
 */
fun ColorScheme.avatarPlaceholderColors(id: Long): AvatarColors =
    when (id.mod(AVATAR_COLOR_COUNT)) {
        0 -> AvatarColors(primaryContainer, onPrimaryContainer)
        1 -> AvatarColors(secondaryContainer, onSecondaryContainer)
        2 -> AvatarColors(tertiaryContainer, onTertiaryContainer)
        3 -> AvatarColors(primaryFixed, onPrimaryFixed)
        4 -> AvatarColors(secondaryFixed, onSecondaryFixed)
        5 -> AvatarColors(tertiaryFixed, onTertiaryFixed)
        else -> AvatarColors(surfaceVariant, onSurfaceVariant)
    }

private const val AVATAR_COLOR_COUNT = 7

/**
 * The color of a sender's name in a group conversation and in reply quotes, chosen by the sender's [id] (a TDLib user
 * or chat id, which can be negative) so a sender keeps one color: `primary`, `secondary` or `tertiary`. Roles of one
 * scheme, so they are harmonized with each other and readable on every bubble container (§2.2), unlike Telegram's
 * fixed palette of name colors.
 */
fun ColorScheme.senderNameColor(id: Long): Color =
    when (id.mod(SENDER_COLOR_COUNT)) {
        0 -> primary
        1 -> secondary
        else -> tertiary
    }

private const val SENDER_COLOR_COUNT = 3
