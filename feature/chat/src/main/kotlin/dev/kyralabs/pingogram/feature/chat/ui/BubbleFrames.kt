package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import kotlin.math.max

// The frames of a message without a bubble (emoji only, bare content), and the reaction badges on a bubble's corner and
// the label under it, for MessageBubble.

/**
 * [bubble] with its reaction [badges] on its bottom corner on the far side (the end for incoming messages, the start
 * for outgoing ones), overlapping it by [overlap] and hanging below it. As wide as the wider of the two: badges wider
 * than the bubble reach past its other side. One layout (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun WithReactionBadges(
    isOutgoing: Boolean,
    overlap: Dp,
    modifier: Modifier,
    bubble: @Composable () -> Unit,
    badges: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(bubble, badges),
        modifier = modifier,
    ) { (bubbleMeasurables, badgeMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bubblePlaceable = bubbleMeasurables.first().measure(loose)
        val badgesPlaceable = badgeMeasurables.first().measure(loose)
        val overlapPx = minOf(overlap.roundToPx(), badgesPlaceable.height, bubblePlaceable.height)
        val width = max(bubblePlaceable.width, badgesPlaceable.width)
        val height = bubblePlaceable.height + badgesPlaceable.height - overlapPx
        layout(width, height, mapOf(BubbleBottom to bubblePlaceable.height)) {
            // From the start; placeRelative mirrors both in right-to-left layouts.
            val bubbleX = if (isOutgoing) width - bubblePlaceable.width else 0
            val badgesX =
                if (isOutgoing) {
                    bubbleX.coerceAtMost(width - badgesPlaceable.width)
                } else {
                    (bubbleX + bubblePlaceable.width - badgesPlaceable.width).coerceAtLeast(0)
                }
            bubblePlaceable.placeRelative(bubbleX, 0)
            badgesPlaceable.placeRelative(badgesX, bubblePlaceable.height - overlapPx)
        }
    }
}

/**
 * [frame] of [bubble] with what goes around it: its reaction badges on its corner ([WithReactionBadges], overlapping it
 * by [overlap]) and, for a message that failed to send, its "Not sent" label below ([WithFooter]). Where the group
 * shows its time on its last bubble only, a tap that nothing inside takes (links, media, quotes and badges keep
 * theirs) shows or hides this one's ([ChatEvent.ToggleTime]).
 */
@Suppress("LongParameterList") // The bubble, its frame and the badges' state, and where they send events.
@Composable
internal fun BubbleStack(
    bubble: ChatItem.Bubble,
    overlap: Dp,
    reactions: ReactionToggleState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
    frame: @Composable (Modifier) -> Unit,
) {
    val withBadges: @Composable (Modifier) -> Unit = { modifier ->
        if (bubble.reactions.isEmpty()) {
            frame(modifier)
        } else {
            WithReactionBadges(
                isOutgoing = bubble.isOutgoing,
                overlap = overlap,
                modifier = modifier,
                bubble = { frame(Modifier) },
                badges = {
                    ReactionBadges(
                        bubble = bubble,
                        toggles = reactions,
                        onToggle = rememberReactionToggle(bubble, reactions, onEvent),
                        onMore = { onEvent(ChatEvent.ShowAllReactions(bubble.id)) },
                    )
                },
            )
        }
    }
    val stackModifier = if (bubble.position.joinsNext) modifier.onTap(bubble.id, onEvent) else modifier
    if (bubble.status == OutgoingStatus.Failed) {
        WithFooter(
            isOutgoing = bubble.isOutgoing,
            modifier = stackModifier,
            bubble = { withBadges(Modifier) },
            footer = { NotSentLabel(bubble.canResend, onRetry = { onEvent(ChatEvent.Resend(bubble.id)) }) },
        )
    } else {
        withBadges(stackModifier)
    }
}

/** Shows or hides the time of the bubble of message [messageId] when tapped where nothing inside takes the tap. */
private fun Modifier.onTap(
    messageId: Long,
    onEvent: (ChatEvent) -> Unit,
): Modifier = pointerInput(messageId, onEvent) { detectTapGestures { onEvent(ChatEvent.ToggleTime(messageId)) } }

/**
 * [bubble] (with its reaction badges) and [footer] below it, a gap of `spacing.extraSmall` apart, on the bubble's side
 * (the end for outgoing messages, the start for incoming ones). It keeps the bubble's bottom ([bubbleBottom]), which
 * the sender's avatar lines up with. One layout (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun WithFooter(
    isOutgoing: Boolean,
    modifier: Modifier,
    bubble: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val gap = MaterialTheme.spacing.extraSmall
    Layout(
        contents = listOf(bubble, footer),
        modifier = modifier,
    ) { (bubbleMeasurables, footerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bubblePlaceable = bubbleMeasurables.first().measure(loose)
        val footerPlaceable = footerMeasurables.first().measure(loose)
        val width = max(bubblePlaceable.width, footerPlaceable.width)
        val footerY = bubblePlaceable.height + gap.roundToPx()
        layout(width, footerY + footerPlaceable.height, mapOf(BubbleBottom to bubblePlaceable.bubbleBottom())) {
            // From the start; placeRelative mirrors both in right-to-left layouts.
            bubblePlaceable.placeRelative(if (isOutgoing) width - bubblePlaceable.width else 0, 0)
            footerPlaceable.placeRelative(if (isOutgoing) width - footerPlaceable.width else 0, footerY)
        }
    }
}

/**
 * Emoji only: the 1 to 3 emoji in `displaySmall`, without a bubble, the sender's name above them in group chats, the
 * time and state below them where [ChatItem.Bubble.showsTime] says.
 */
@Composable
internal fun EmojiBubble(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = if (bubble.isOutgoing) Alignment.End else Alignment.Start,
    ) {
        BubbleHeader(bubble, colors, onEvent)
        Text(
            text = bubble.text.text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall,
        )
        if (bubble.showsTime) SurfaceMeta(bubble)
    }
}

/**
 * Bare content: no bubble, the name, forward and reply above it, the meta on a pill over its bottom end where
 * [ChatItem.Bubble.showsTime] says.
 */
@Composable
internal fun BareBubble(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = if (bubble.isOutgoing) Alignment.End else Alignment.Start,
    ) {
        BubbleHeader(bubble, colors, onEvent)
        Box {
            MessageContentBody(bubble, fileState = null, Playback.Stopped, colors, onEvent)
            if (bubble.showsTime) OverlayMeta(bubble, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * The column of senders' avatars in a group chat: [avatar] beside the last bubble of a group (its bottom, not its
 * reaction badges'), else its space, and the gap to the bubble.
 */
@Composable
internal fun RowScope.SenderAvatarColumn(avatar: SenderAvatar?) {
    val size = MaterialTheme.sizes.avatarExtraSmall
    if (avatar != null) {
        Avatar(
            photo = avatar.photo,
            name = avatar.name.resolve(),
            id = avatar.id,
            size = size,
            modifier = Modifier.alignBy { it.measuredHeight },
            placeholderIcon = avatar.placeholderIcon,
        )
    } else {
        Spacer(Modifier.width(size))
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
}

/**
 * The bottom of a message's bubble above its reaction badges ([WithReactionBadges]) and its label ([WithFooter]), which
 * the sender's avatar lines up with.
 */
private val BubbleBottom = HorizontalAlignmentLine(merger = ::minOf)

/**
 * Where the bubble of this measured row content ends: [BubbleBottom] if it has badges or a label below, else its
 * bottom.
 */
internal fun Measured.bubbleBottom(): Int =
    this[BubbleBottom].takeIf { it != AlignmentLine.Unspecified } ?: measuredHeight
