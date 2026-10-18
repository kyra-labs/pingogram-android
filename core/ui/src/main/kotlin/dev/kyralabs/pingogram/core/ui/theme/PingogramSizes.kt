package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout dimensions beyond the spacing scale (`docs/DESIGN_SYSTEM.md` §7). Feature modules use these instead of
 * `dp` literals, through [MaterialTheme.sizes].
 *
 * @property contentMaxWidth the widest a single column of content gets (forms, onboarding), centered on medium and
 *   wider windows so lines and fields stay comfortable; the width of an M3 dialog.
 * @property illustrationMaxSize the largest side of a hero illustration or a QR code frame.
 * @property avatarSmall an `Avatar` in a one- or two-line list item: contacts, members, search results (the M3 list
 *   item's leading avatar).
 * @property avatarMedium an `Avatar` in a chat list row, next to a title and a two-line preview.
 */
@Immutable
data class PingogramSizes(
    val contentMaxWidth: Dp = 560.dp,
    val illustrationMaxSize: Dp = 280.dp,
    val avatarSmall: Dp = 40.dp,
    val avatarMedium: Dp = 56.dp,
)

val LocalPingogramSizes = staticCompositionLocalOf { PingogramSizes() }

/** The [PingogramSizes] provided by `PingogramTheme`. */
val MaterialTheme.sizes: PingogramSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramSizes.current
