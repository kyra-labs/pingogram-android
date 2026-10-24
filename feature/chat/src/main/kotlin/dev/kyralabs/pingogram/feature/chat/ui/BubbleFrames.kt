package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.sticker.LocalCustomEmoji
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.customEmoji
import dev.kyralabs.pingogram.core.ui.text.customEmojiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButtons
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
 * by [overlap]) and, below it ([WithFooter]), the "Not sent" label of a message that failed to send, or the buttons a
 * bot attached to it ([BotKeyboardButtons]) and the link to its thread ([ThreadButton]: "12 comments", "3 replies").
 * Where the group
 * shows its time on its last bubble only, a tap that nothing inside takes (links, media, quotes and badges keep
 * theirs) shows or hides this one's ([ChatEvent.ToggleTime]); a double tap adds the quick reaction where the settings
 * allow it ([bubbleTaps], [rememberDoubleTapReaction]).
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
    val onDoubleTap = rememberDoubleTapReaction(bubble, reactions, onEvent)
    val stackModifier = modifier.bubbleTaps(bubble.id, bubble.position.joinsNext, onDoubleTap, onEvent)
    val thread = bubble.thread
    val keyboard = bubble.keyboard
    when {
        bubble.status == OutgoingStatus.Failed -> {
            WithFooter(
                isOutgoing = bubble.isOutgoing,
                modifier = stackModifier,
                bubble = { withBadges(Modifier) },
                footer = { NotSentLabel(bubble.canResend, onRetry = { onEvent(ChatEvent.Resend(bubble.id)) }) },
            )
        }

        thread != null || keyboard != null -> {
            WithFooter(
                isOutgoing = bubble.isOutgoing,
                modifier = stackModifier,
                bubble = { withBadges(Modifier) },
                footer = { KeyboardAndThread(bubble.isOutgoing, keyboard, thread, onEvent) },
            )
        }

        else -> {
            withBadges(stackModifier)
        }
    }
}

/**
 * Below a bubble: the bot's [keyboard], as wide as a bubble can be, then the link to the [thread], on the bubble's
 * side, `spacing.small` apart, so their touch targets do not overlap; either may be missing.
 */
@Composable
private fun KeyboardAndThread(
    isOutgoing: Boolean,
    keyboard: BotKeyboard?,
    thread: ThreadLink?,
    onEvent: (ChatEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        if (keyboard != null) {
            BotKeyboardButtons(keyboard, onPress = { onEvent(BotButtonEvent.Press(keyboard.messageId, it)) })
        }
        if (thread != null) ThreadButton(thread, onOpen = { onEvent(ChatEvent.OpenThread(thread.messageId)) })
    }
}

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
 * time and state below them where [ChatItem.Bubble.showsTime] says. Custom emoji show their pictures that large over
 * their fallback emoji ([customEmoji]), repainted in `onSurface` where they take the text's color.
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
        val renderer = LocalCustomEmoji.current?.takeIf { bubble.text.entities.isNotEmpty() }
        if (renderer == null) {
            Text(
                text = bubble.text.text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displaySmall,
            )
        } else {
            val text = remember(bubble.text) { bubble.text.customEmojiText() }
            val layout = remember { TextBlocksLayout() }
            val color = MaterialTheme.colorScheme.onSurface
            val tint = remember(color) { ColorProducer { color } }
            Text(
                text = text,
                modifier = Modifier.customEmoji(renderer, layout, tint),
                color = color,
                style = MaterialTheme.typography.displaySmall,
                onTextLayout = layout.onTextLayout,
            )
        }
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
 * reaction badges'), else its space, and the gap to the bubble. Tapping the avatar of user [userId] opens their
 * profile ([ChatEvent.OpenProfile], [openProfileTarget]), on a 48 dp touch target that moves nothing, with its state
 * layer in the avatar's circle; screen readers get it as the bubble's custom action.
 */
@Composable
internal fun RowScope.SenderAvatarColumn(
    avatar: SenderAvatar?,
    userId: Long? = null,
    onEvent: (ChatEvent) -> Unit = {},
) {
    val size = MaterialTheme.sizes.avatarExtraSmall
    if (avatar != null) {
        Avatar(
            photo = avatar.photo,
            name = avatar.name.resolve(),
            id = avatar.id,
            size = size,
            modifier =
                Modifier
                    .alignBy { it.measuredHeight }
                    .openProfileTarget(userId, CircleShape, onEvent),
            placeholderIcon = avatar.placeholderIcon,
        )
    } else {
        Spacer(Modifier.width(size))
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
}

/**
 * Opens the profile of user [userId] ([ChatEvent.OpenProfile]) when this sender's name or avatar is clicked, where
 * there is a user (`docs/audit/accessibility.md` A4): a button ("Open profile") on a 48 dp touch target that moves
 * nothing ([touchTarget]), focusable by the keyboard, with Material's pressed, hover and focus states clipped to
 * [shape]. The label is read from the resources in the semantics block, only when something reads it.
 */
@Composable
internal fun Modifier.openProfileTarget(
    userId: Long?,
    shape: Shape,
    onEvent: (ChatEvent) -> Unit,
): Modifier {
    if (userId == null) return this
    val interactions = remember { MutableInteractionSource() }
    val resources = LocalResources.current
    val open = remember(userId, onEvent) { { onEvent(ChatEvent.OpenProfile(userId)) } }
    return touchTarget(
        Modifier
            .clickable(interactions, indication = null, onClick = open)
            .semantics {
                role = Role.Button
                onClick(label = resources.getString(R.string.chat_open_profile)) {
                    open()
                    true
                }
            },
    ).clip(shape)
        .indication(interactions, ripple(focusRingShape = shape))
}

/**
 * The bottom of a message's bubble above its reaction badges ([WithReactionBadges]) and its label ([WithFooter]), which
 * the sender's avatar and the share button ([WithShareButton]) line up with.
 */
internal val BubbleBottom = HorizontalAlignmentLine(merger = ::minOf)

/**
 * Where the bubble of this measured row content ends: [BubbleBottom] if it has badges or a label below, else its
 * bottom.
 */
internal fun Measured.bubbleBottom(): Int =
    this[BubbleBottom].takeIf { it != AlignmentLine.Unspecified } ?: measuredHeight
