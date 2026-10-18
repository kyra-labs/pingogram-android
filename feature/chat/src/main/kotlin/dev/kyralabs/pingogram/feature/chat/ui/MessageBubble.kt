package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.messageBubble
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.edgeToEdgeWidth
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import kotlin.math.ceil
import kotlin.math.max
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A message in the conversation (`REQUIREMENTS.md` FR-C2, FR-C4, `docs/DESIGN_SYSTEM.md` §2.2, §4, §9): its bubble at
 * the end for outgoing messages, at the start for incoming ones, beside the sender's avatar in group chats.
 *
 * - **Bubble**: the [BubbleColors] of its side, a shape from the shape scale by its place in the group
 *   ([messageBubble]), at most [MaterialTheme.sizes]' share of the width. From top to bottom: the sender's name
 *   (first of a group in group chats), "Forwarded from …", the reply quote, the content ([MessageContentBody]), the
 *   text with its formatting, the link preview and the reactions, with the time and state of the message at the bottom
 *   end, after the last line when it fits ([TrailingMeta]).
 * - **Frames**, as the content asks:
 *   - *Bare* content ([isBare]: stickers, animated emoji, dice, video messages) has no bubble; the time and state sit
 *     on a `mediaOverlay` pill at its bottom end.
 *   - *Edge-to-edge* media ([isEdgeToEdge]: photos, videos, GIFs, albums) fills a bubble as wide as itself, clipped to
 *     the bubble's shape. A caption follows below with the usual padding and meta; without one, the meta sits on a
 *     `mediaOverlay` pill over the media's bottom end.
 *   - *Padded* content (everything else) sits in the bubble's padding like text.
 * - **Screen readers** hear the bubble as one: "{sender}, {time}: {content}, {status}" ([bubbleDescription]), with
 *   the reply quote and the content's buttons as custom actions ([replyAction], [contentActions]).
 * - **Reply quote**: tapping it shows the replied message ([ChatEvent.OpenReply]).
 * - **Highlight**: after a jump to the bubble, a `secondaryContainer` band behind the row fades in and out
 *   ([jumpHighlight]), read in the draw phase.
 * - Everything shown is in [bubble], mapped by the ViewModel, in [fileState], the file of a voice message, song or
 *   document, and in [playback], how a voice message or song plays; the text is built once per message
 *   ([AnnotatedTextCache]). No effects, no fixed heights, so it grows with the font scale (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun MessageBubbleRow(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = bubbleDescription(bubble)
    val actions = rememberBubbleActions(bubble, fileState, playback.isPlaying, onEvent)
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val highlightShape = MaterialTheme.shapes.medium
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .jumpHighlight(resources.jump, bubble, highlightColor, highlightShape)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (actions.isNotEmpty()) customActions = actions
                },
        horizontalArrangement = if (bubble.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (bubble.hasAvatarColumn) {
            val size = MaterialTheme.sizes.avatarExtraSmall
            val avatar = bubble.avatar
            if (avatar != null) {
                Avatar(
                    photo = avatar.photo,
                    name = avatar.name.resolve(),
                    id = avatar.id,
                    size = size,
                    placeholderIcon = avatar.placeholderIcon,
                )
            } else {
                Spacer(Modifier.width(size))
            }
            Spacer(Modifier.width(MaterialTheme.spacing.small))
        }
        MessageBubble(bubble, fileState, playback, resources, onEvent)
    }
}

/** [replyAction] and [contentActions] as the screen reader's custom actions, sending their events. */
@Composable
private fun rememberBubbleActions(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    isPlaying: Boolean,
    onEvent: (ChatEvent) -> Unit,
): List<CustomAccessibilityAction> {
    val resources = LocalResources.current
    return remember(bubble, fileState, isPlaying, onEvent, resources) {
        (listOfNotNull(replyAction(bubble)) + contentActions(bubble, fileState, isPlaying)).map { action ->
            CustomAccessibilityAction(resources.getString(action.label)) {
                onEvent(action.event)
                true
            }
        }
    }
}

@Composable
private fun MessageBubble(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
) {
    val style = resources.theme.styleOf(bubble.isOutgoing)
    val shapes = MaterialTheme.shapes
    val position = bubble.position
    val shape =
        remember(shapes, bubble.isOutgoing, position) {
            shapes.messageBubble(bubble.isOutgoing, position.joinsPrevious, position.joinsNext)
        }
    val sizes = MaterialTheme.sizes
    val bubbleModifier = Modifier.maxWidthFraction(sizes.bubbleMaxWidthFraction, sizes.bubbleMaxWidth)
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        when {
            bubble.album == null && bubble.content.isBare -> {
                BareBubble(bubble, style.colors, onEvent, bubbleModifier)
            }

            bubble.album != null || bubble.content.isEdgeToEdge -> {
                MediaBubble(bubble, style, shape, resources, onEvent, bubbleModifier)
            }

            else -> {
                Column(
                    modifier =
                        bubbleModifier
                            .background(style.colors.container, shape)
                            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                    BubbleHeader(bubble, style.colors, onEvent)
                    MessageContentBody(bubble, fileState, playback, style.colors, onEvent)
                    BubbleBody(bubble, style, resources, onEvent)
                }
            }
        }
    }
}

/**
 * Bare content: no bubble, the name, forward and reply above it, the meta on a pill over its bottom end, the
 * reactions below.
 */
@Composable
private fun BareBubble(
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
        if (bubble.reactions.isNotEmpty()) ReactionsRow(bubble, colors)
    }
}

/**
 * Edge-to-edge media in a bubble as wide as the media and clipped to the bubble's [shape]: the name, forward and reply
 * above it with the usual padding; below it the caption, link preview and reactions with the meta; without any of
 * them, the meta on a pill over the media's bottom end.
 */
@Composable
private fun MediaBubble(
    bubble: ChatItem.Bubble,
    style: BubbleStyle,
    shape: Shape,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = style.colors
    val spacing = MaterialTheme.spacing
    val hasHeader = bubble.showsSenderName || bubble.forwardedFrom != null || bubble.reply != null
    val metaOnMedia = bubble.text.text.isEmpty() && bubble.linkPreview == null && bubble.reactions.isEmpty()
    Column(
        modifier
            .width(edgeToEdgeWidth(bubble.content, isAlbum = bubble.album != null))
            .clip(shape)
            .background(colors.container),
    ) {
        if (hasHeader) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) { BubbleHeader(bubble, colors, onEvent) }
        }
        Box {
            MessageContentBody(bubble, fileState = null, Playback.Stopped, colors, onEvent)
            if (metaOnMedia) OverlayMeta(bubble, Modifier.align(Alignment.BottomEnd).padding(spacing.small))
        }
        if (!metaOnMedia) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) { BubbleBody(bubble, style, resources, onEvent) }
        }
    }
}

/**
 * The sender's name (first of a group in group chats), "Forwarded from …" and the reply quote, whichever there are;
 * the quote shows its message when tapped, if it opens one.
 */
@Composable
private fun BubbleHeader(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    if (bubble.showsSenderName) {
        Text(
            text = bubble.sender.resolve(),
            color = MaterialTheme.colorScheme.senderNameColor(bubble.senderId),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    bubble.forwardedFrom?.let { forwarded ->
        Text(
            text = forwarded.resolve(),
            color = colors.secondaryContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    bubble.reply?.let { reply ->
        val messageId = reply.messageId
        val onOpen = remember(messageId, onEvent) { messageId?.let { { onEvent(ChatEvent.OpenReply(it)) } } }
        ReplyQuoteBlock(reply, colors, onOpen)
    }
}

/**
 * The text, the link preview and the reactions, whichever there are, with the meta after the text or the reactions;
 * otherwise the meta alone, at the bubble's end.
 */
@Composable
private fun ColumnScope.BubbleBody(
    bubble: ChatItem.Bubble,
    style: BubbleStyle,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
) {
    val colors = style.colors
    val hasText = bubble.text.text.isNotEmpty()
    val linkPreview = bubble.linkPreview
    val hasReactions = bubble.reactions.isNotEmpty()
    val meta = @Composable { BubbleMeta(bubble, colors) }
    when {
        hasText && linkPreview == null && !hasReactions -> {
            val layout = remember { TextLayoutHolder() }
            TrailingMeta(meta = meta, lastLineWidth = layout::lastLineWidth) {
                BubbleText(bubble, style, resources, onEvent) { layout.result = it }
            }
        }

        hasReactions -> {
            if (hasText) BubbleText(bubble, style, resources, onEvent)
            linkPreview?.let { LinkPreviewBlock(it, colors) }
            TrailingMeta(meta = meta, lastLineWidth = { null }) { ReactionsRow(bubble, colors) }
        }

        else -> {
            if (hasText) BubbleText(bubble, style, resources, onEvent)
            linkPreview?.let { LinkPreviewBlock(it, colors) }
            BubbleMeta(bubble, colors, Modifier.align(Alignment.End))
        }
    }
}

/** The text or caption in `bodyLarge` with its entities ([AnnotatedTextCache]); tapping a spoiler reveals it. */
@Composable
private fun BubbleText(
    bubble: ChatItem.Bubble,
    style: BubbleStyle,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val onSpoilerClick =
        remember(bubble.id, onEvent) { LinkInteractionListener { onEvent(ChatEvent.RevealSpoilers(bubble.id)) } }
    Text(
        text = resources.texts.get(bubble, style.text, onSpoilerClick, resources.onLinkClick),
        color = style.colors.content,
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = onTextLayout,
    )
}

/** The text's latest layout, read by [TrailingMeta] right after the text was measured. Not composition state. */
private class TextLayoutHolder {
    var result: TextLayoutResult? = null

    /**
     * How wide the last line is, so the meta can follow it; the text's full width when the line runs against the
     * layout's direction (right-to-left text in a left-to-right layout), as the meta would cover its end then.
     */
    fun lastLineWidth(layoutDirection: LayoutDirection): Int? {
        val layout = result?.takeIf { it.lineCount > 0 } ?: return null
        val last = layout.lineCount - 1
        val lineDirection = layout.getParagraphDirection(layout.getLineStart(last))
        val againstLayout = (lineDirection == ResolvedTextDirection.Rtl) != (layoutDirection == LayoutDirection.Rtl)
        return if (againstLayout) {
            layout.size.width
        } else {
            ceil(
                layout.getLineRight(last) - layout.getLineLeft(last),
            ).toInt()
        }
    }
}

/** At most [fraction] of the available width, and at most [max]. */
private fun Modifier.maxWidthFraction(
    fraction: Float,
    max: Dp,
): Modifier =
    layout { measurable, constraints ->
        val maxWidth =
            minOf(
                (constraints.maxWidth * fraction).toInt(),
                max.roundToPx(),
            ).coerceAtLeast(constraints.minWidth)
        val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
