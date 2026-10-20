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
 * @property headlineBadge the side of a `MaterialShapes` badge with an icon above a headline, e.g. on every login step
 *   (`docs/DESIGN_SYSTEM.md` §3.3); its icon takes half of it.
 * @property avatarSmall an `Avatar` in a one- or two-line list item: contacts, members, search results (the M3 list
 *   item's leading avatar).
 * @property avatarRow an `Avatar` in a chat list row, next to a title and a one-line preview (`docs/DESIGN_SYSTEM.md`
 *   §7: between Material's 40 dp list avatar and [avatarMedium], so photos stay recognizable in a dense list).
 * @property avatarMedium an `Avatar` on the account's card at the top of Settings.
 * @property avatarLarge an `Avatar` heading a screen on its own: the profile photo in Edit profile.
 * @property avatarExtraSmall an `Avatar` beside the last of a group of messages in a group chat, next to its bubble.
 * @property avatarRing the width of a ring around an `Avatar`, and of the gap that sets it off from the avatar, e.g.
 *   the ring in `primary` around the account's avatar on its card in Settings.
 * @property avatarBadge the diameter of a small round badge over an `Avatar`'s bottom end, e.g. the edit badge on the
 *   account's avatar in Settings, holding an icon of [badgeIcon].
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
 * @property reactionBadge the smallest a reaction's round badge on a bubble's corner gets: its diameter with one emoji.
 * @property reactionBadgeRing the ring in the conversation's background color around a reaction badge, which sets it
 *   off from the bubble it overlaps.
 * @property reactionTile the width of a reaction with its count in the reactions picker's carousel of a message's
 *   reactions, at 100% font scale.
 * @property mediaMaxWidth the widest a photo, a video or an album gets in a message, and the width of a poll.
 * @property mediaMaxHeight the tallest a photo or a video gets in a message.
 * @property mediaMinSize the shortest side of a photo or a video in a message: panoramas and tall screenshots are
 *   cropped to it rather than shown as slivers.
 * @property mediaAlbumGap the gap between the photos and videos of an album.
 * @property playBadge the round badge on a video's or a GIF's thumbnail that says it plays.
 * @property sticker the square a sticker fits in, keeping its aspect ratio.
 * @property largeEmoji the square an emoji shown on its own fits in: an animated emoji, a die.
 * @property videoNote the diameter of a round video message.
 * @property contentIcon the circle behind the icon of a document, a location, a game or a story in a message.
 * @property waveformMinWidth the width of the waveform of a short voice message; longer ones get wider, up to
 *   [waveformMaxWidth] for a minute and more.
 * @property waveformMaxWidth the width of the waveform of a voice message of a minute or more.
 * @property waveformHeight the height of a voice message's waveform: its loudest bars.
 * @property waveformBarWidth the width of one bar of a voice message's waveform.
 * @property waveformBarGap the gap between two bars of a voice message's waveform.
 * @property attachmentPreview the square of a picked photo or video in the preview before it is sent.
 * @property fabMedium the height of a medium floating action button, extended or not (Material 3 Expressive's medium
 *   FAB, which material3 has no public token for): the room a list leaves at its end, so its last row can scroll clear
 *   of one.
 * @property pinPadMaxWidth the widest the passcode lock's keypad gets, so its keys stay within a thumb's reach.
 * @property passcodeDot the diameter of a dot that stands for an entered digit of a passcode.
 */
@Immutable
data class PingogramSizes(
    val contentMaxWidth: Dp = 560.dp,
    val illustrationMaxSize: Dp = 280.dp,
    val headlineBadge: Dp = 64.dp,
    val avatarSmall: Dp = 40.dp,
    val avatarRow: Dp = 48.dp,
    val avatarMedium: Dp = 56.dp,
    val avatarLarge: Dp = 96.dp,
    val avatarExtraSmall: Dp = 32.dp,
    val avatarRing: Dp = 2.dp,
    val avatarBadge: Dp = 24.dp,
    val inlineIcon: Dp = 18.dp,
    val inlineIconSmall: Dp = 16.dp,
    val badgeIcon: Dp = 12.dp,
    val inlineProgressIndicator: Dp = 20.dp,
    val bubbleMaxWidthFraction: Float = 0.8f,
    val bubbleMaxWidth: Dp = 560.dp,
    val quoteBarWidth: Dp = 3.dp,
    val reactionBadge: Dp = 28.dp,
    val reactionBadgeRing: Dp = 2.dp,
    val reactionTile: Dp = 64.dp,
    val mediaMaxWidth: Dp = 280.dp,
    val mediaMaxHeight: Dp = 360.dp,
    val mediaMinSize: Dp = 120.dp,
    val mediaAlbumGap: Dp = 2.dp,
    val playBadge: Dp = 48.dp,
    val sticker: Dp = 160.dp,
    val largeEmoji: Dp = 112.dp,
    val videoNote: Dp = 200.dp,
    val contentIcon: Dp = 40.dp,
    val waveformMinWidth: Dp = 96.dp,
    val waveformMaxWidth: Dp = 192.dp,
    val waveformHeight: Dp = 24.dp,
    val waveformBarWidth: Dp = 3.dp,
    val waveformBarGap: Dp = 2.dp,
    val attachmentPreview: Dp = 120.dp,
    val fabMedium: Dp = 80.dp,
    val pinPadMaxWidth: Dp = 360.dp,
    val passcodeDot: Dp = 14.dp,
)

val LocalPingogramSizes = staticCompositionLocalOf { PingogramSizes() }

/** The [PingogramSizes] provided by `PingogramTheme`. */
val MaterialTheme.sizes: PingogramSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramSizes.current
