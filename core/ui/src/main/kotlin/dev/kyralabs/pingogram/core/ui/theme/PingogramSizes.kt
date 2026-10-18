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
 * `dp` literals (and of fractions of the window), through [MaterialTheme.sizes].
 *
 * @property contentMaxWidth the widest a single column of content gets (forms, onboarding, a list on its own),
 *   centered on medium and wider windows so lines and fields stay comfortable; the width of an M3 dialog.
 * @property illustrationMaxSize the largest side of a hero illustration or a QR code frame.
 * @property avatarSmall an `Avatar` in a one- or two-line list item: contacts, members, search results (the M3 list
 *   item's leading avatar).
 * @property avatarMedium an `Avatar` in a chat list row, next to a title and a two-line preview.
 * @property avatarExtraSmall an `Avatar` beside the last of a group of messages in a group chat, next to its bubble.
 * @property inlineIcon an icon within a line of title text, e.g. the badges and mute icon after a chat's name.
 * @property inlineIconSmall an icon next to label text, e.g. read receipts beside a time, or a chat's pin.
 * @property badgeIcon an icon inside a `Badge`, e.g. the `@` of an unread mention; the size of a badge's digits.
 * @property inlineProgressIndicator an indeterminate progress indicator within a line of `labelLarge` text, e.g. in
 *   the chat list's connection pill.
 * @property bubbleMaxWidthFraction the widest a message bubble gets, as a fraction of the conversation's width, so
 *   incoming and outgoing bubbles stay apart.
 * @property bubbleMaxWidth the widest a message bubble gets on wide windows, where [bubbleMaxWidthFraction] would make
 *   lines too long to read comfortably; the width of [contentMaxWidth].
 * @property quoteBarWidth the bar at the start of a reply quote or link preview inside a bubble.
 */
@Immutable
data class PingogramSizes(
    val contentMaxWidth: Dp = 560.dp,
    val illustrationMaxSize: Dp = 280.dp,
    val avatarSmall: Dp = 40.dp,
    val avatarMedium: Dp = 56.dp,
    val avatarExtraSmall: Dp = 32.dp,
    val inlineIcon: Dp = 18.dp,
    val inlineIconSmall: Dp = 16.dp,
    val badgeIcon: Dp = 12.dp,
    val inlineProgressIndicator: Dp = 20.dp,
    val bubbleMaxWidthFraction: Float = 0.8f,
    val bubbleMaxWidth: Dp = 560.dp,
    val quoteBarWidth: Dp = 3.dp,
)

val LocalPingogramSizes = staticCompositionLocalOf { PingogramSizes() }

/** The [PingogramSizes] provided by `PingogramTheme`. */
val MaterialTheme.sizes: PingogramSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramSizes.current
