package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlin.math.max
import dev.kyralabs.pingogram.core.ui.R as UiR

// The share button beside channel posts (docs/audit/feature-gaps-octogram.md P2, docs/DESIGN_SYSTEM.md §6).

/**
 * Whether [ChatItem.Bubble.hasShareButton] shows its button: where the chat lets its messages be forwarded
 * ([MessageShortcuts.canForward]) and the message was sent.
 */
internal fun ChatItem.Bubble.showsShareButton(shortcuts: MessageShortcuts): Boolean =
    hasShareButton && shortcuts.canForward && status != OutgoingStatus.Pending && status != OutgoingStatus.Failed

/** The screen reader's action that forwards [bubble], where its share button shows ([showsShareButton]). */
internal fun shareAction(
    bubble: ChatItem.Bubble,
    shortcuts: MessageShortcuts,
): ContentAction? =
    ContentAction(R.string.message_action_forward, ChatEvent.Forward(bubble.id)).takeIf {
        bubble.showsShareButton(shortcuts)
    }

/**
 * [bubble] with the share button on its far side (the end for incoming messages, the start for outgoing ones),
 * `spacing.small` from it, the button's circle level with the bubble's bottom ([bubbleBottom], above its reaction
 * badges): a small `FilledIconButton` in `surfaceContainerHighest` / `onSurfaceVariant`, as the swipe-to-reply circle,
 * with the forward icon and its press morph, which opens the forward sheet through [onShare]. The bubble is measured
 * first, as without the button, which takes the room it leaves (a bubble is at most 80% of the row): so the bubble
 * stays where it would be, and the lifted copy of the message ([LiftedBubble]), which has no button, lines up with it.
 * The button's 48 dp touch target reaches past the row where the bubble is shorter. One layout
 * (`docs/PERFORMANCE.md` §4); the row keeps the bubble's bottom, which the sender's avatar lines up with.
 */
@Composable
internal fun WithShareButton(
    isOutgoing: Boolean,
    style: ShareButtonStyle,
    onShare: () -> Unit,
    modifier: Modifier,
    bubble: @Composable () -> Unit,
) {
    val gap = MaterialTheme.spacing.small
    val circle = IconButtonDefaults.smallContainerSize().width
    val button: @Composable () -> Unit = {
        FilledIconButton(onClick = onShare, shapes = IconButtonDefaults.shapes(), colors = style.colors) {
            Icon(painter = style.icon, contentDescription = style.label)
        }
    }
    Layout(
        contents = listOf(bubble, button),
        modifier = modifier,
    ) { (bubbleMeasurables, buttonMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bubblePlaceable = bubbleMeasurables.first().measure(loose)
        val buttonPlaceable = buttonMeasurables.first().measure(loose)
        // The touch target's margin around the circle, on each side.
        val inset = max(0, (buttonPlaceable.width - circle.roundToPx()) / 2)
        // The gap and the circle beside the bubble.
        val side = gap.roundToPx() + buttonPlaceable.width - 2 * inset
        val width = (bubblePlaceable.width + side).coerceAtMost(constraints.maxWidth)
        val bottom = bubblePlaceable.bubbleBottom()
        layout(width, bubblePlaceable.height, mapOf(BubbleBottom to bottom)) {
            // From the start; placeRelative mirrors both in right-to-left layouts.
            val bubbleX = if (isOutgoing) width - bubblePlaceable.width else 0
            val circleX = if (isOutgoing) bubbleX - side else bubbleX + bubblePlaceable.width + gap.roundToPx()
            val buttonX = circleX - inset
            bubblePlaceable.placeRelative(bubbleX, 0)
            buttonPlaceable.placeRelative(buttonX, bottom - buttonPlaceable.height + inset)
        }
    }
}

/**
 * What every share button of the list shares: its colors, its icon and its label. One painter for all of them, as the
 * swipe-to-reply circle has ([SwipeReplyStyle]): a painter per button cost every channel post's composition its own.
 */
@Immutable
internal class ShareButtonStyle(
    val colors: IconButtonColors,
    val icon: Painter,
    val label: String,
)

/** The [ShareButtonStyle] of the conversation's bubbles. */
@Composable
internal fun rememberShareButtonStyle(): ShareButtonStyle {
    val scheme = MaterialTheme.colorScheme
    val colors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurfaceVariant,
        )
    val icon = painterResource(UiR.drawable.ic_forward)
    val label = stringResource(R.string.message_action_forward)
    // Not keyed on the icon: painterResource hands out a new painter of the same vector as the screen recomposes,
    // which would recompose every bubble; the first one draws the same.
    return remember(colors, label) { ShareButtonStyle(colors, icon, label) }
}
