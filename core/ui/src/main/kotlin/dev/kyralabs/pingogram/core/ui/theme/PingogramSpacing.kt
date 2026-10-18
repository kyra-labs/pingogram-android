package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale and window margins (`docs/DESIGN_SYSTEM.md` §7). Feature modules use these instead
 * of `dp` literals, through [MaterialTheme.spacing].
 *
 * @property windowMarginCompact horizontal screen margin for compact windows.
 * @property windowMarginMedium horizontal screen margin for medium and wider windows.
 */
@Immutable
data class PingogramSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val extraExtraLarge: Dp = 32.dp,
    val windowMarginCompact: Dp = 16.dp,
    val windowMarginMedium: Dp = 24.dp,
)

val LocalPingogramSpacing = staticCompositionLocalOf { PingogramSpacing() }

/** The [PingogramSpacing] provided by `PingogramTheme`. */
val MaterialTheme.spacing: PingogramSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramSpacing.current
