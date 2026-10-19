package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import kotlin.math.max

// The frames of a message without a bubble (emoji only, bare content), and the reaction badges on a bubble's corner,
// for MessageBubble.

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
 * Emoji only: the 1 to 3 emoji in `displaySmall`, without a bubble, the sender's name above them in group chats, the
 * time and state below them.
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
        SurfaceMeta(bubble)
    }
}

/**
 * Bare content: no bubble, the name, forward and reply above it, the meta on a pill over its bottom end.
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
            OverlayMeta(bubble, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * The bottom of a message's bubble above its reaction badges ([WithReactionBadges]), which the sender's avatar lines
 * up with.
 */
private val BubbleBottom = HorizontalAlignmentLine(merger = ::minOf)

/** Where the bubble of this measured row content ends: [BubbleBottom] if it has badges below, else its bottom. */
internal fun Measured.bubbleBottom(): Int =
    this[BubbleBottom].takeIf { it != AlignmentLine.Unspecified } ?: measuredHeight
