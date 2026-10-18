package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.messageBubble
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlin.math.ceil
import kotlin.math.max
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A message in the conversation (`REQUIREMENTS.md` FR-C2, `docs/DESIGN_SYSTEM.md` §2.2, §4, §9): its bubble at the
 * end for outgoing messages, at the start for incoming ones, beside the sender's avatar in group chats.
 *
 * - **Bubble**: [BubbleColors] of its side, a shape from the shape scale by its place in the group
 *   ([messageBubble]), at most [MaterialTheme.sizes]' share of the width. From top to bottom: the sender's name
 *   (first of a group in group chats), "Forwarded from …", the reply quote, the content ([MessageContentBody]), the
 *   text with its formatting, the link preview and the reactions, with the time and state of the message at the bottom
 *   end, after the last line when it fits ([TrailingMeta]).
 * - **Screen readers** hear the bubble as one: "{sender}, {time}: {content}, {status}" ([bubbleDescription]).
 * - Everything shown is in [bubble], mapped by the ViewModel; the text is built once per message ([texts]). No
 *   effects, no fixed heights, so it grows with the font scale (`docs/PERFORMANCE.md` §4).
 *
 * @param onLinkClick opens a link of the text; the same for every bubble.
 */
@Composable
internal fun MessageBubbleRow(
    bubble: ChatItem.Bubble,
    theme: BubbleTheme,
    texts: AnnotatedTextCache,
    onLinkClick: LinkInteractionListener,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = bubbleDescription(bubble)
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
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
        MessageBubble(bubble, theme.colorsOf(bubble.isOutgoing), texts, onLinkClick, onEvent)
    }
}

@Composable
private fun MessageBubble(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    texts: AnnotatedTextCache,
    onLinkClick: LinkInteractionListener,
    onEvent: (ChatEvent) -> Unit,
) {
    val shapes = MaterialTheme.shapes
    val position = bubble.position
    val shape =
        remember(shapes, bubble.isOutgoing, position) {
            shapes.messageBubble(bubble.isOutgoing, position.joinsPrevious, position.joinsNext)
        }
    val sizes = MaterialTheme.sizes
    val spacing = MaterialTheme.spacing
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier =
                Modifier
                    .maxWidthFraction(sizes.bubbleMaxWidthFraction, sizes.bubbleMaxWidth)
                    .background(colors.container, shape)
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
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
            bubble.reply?.let { ReplyQuoteBlock(it, colors) }
            MessageContentBody(content = bubble.content, label = bubble.contentLabel, colors = colors)
            BubbleBody(bubble, colors, texts, onLinkClick, onEvent)
        }
    }
}

/**
 * The text, the link preview and the reactions, whichever there are, with the meta after the text or the reactions;
 * otherwise the meta alone, at the bubble's end.
 */
@Composable
private fun ColumnScope.BubbleBody(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    texts: AnnotatedTextCache,
    onLinkClick: LinkInteractionListener,
    onEvent: (ChatEvent) -> Unit,
) {
    val hasText = bubble.text.text.isNotEmpty()
    val linkPreview = bubble.linkPreview
    val hasReactions = bubble.reactions.isNotEmpty()
    val meta = @Composable { BubbleMeta(bubble, colors) }
    when {
        hasText && linkPreview == null && !hasReactions -> {
            val layout = remember { TextLayoutHolder() }
            TrailingMeta(meta = meta, lastLineWidth = layout::lastLineWidth) {
                BubbleText(bubble, colors, texts, onLinkClick, onEvent) { layout.result = it }
            }
        }

        hasReactions -> {
            if (hasText) BubbleText(bubble, colors, texts, onLinkClick, onEvent)
            linkPreview?.let { LinkPreviewBlock(it, colors) }
            TrailingMeta(meta = meta, lastLineWidth = { null }) { ReactionsRow(bubble, colors) }
        }

        else -> {
            if (hasText) BubbleText(bubble, colors, texts, onLinkClick, onEvent)
            linkPreview?.let { LinkPreviewBlock(it, colors) }
            BubbleMeta(bubble, colors, Modifier.align(Alignment.End))
        }
    }
}

/** The text or caption in `bodyLarge` with its entities ([AnnotatedTextCache]); tapping a spoiler reveals it. */
@Composable
private fun BubbleText(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    texts: AnnotatedTextCache,
    onLinkClick: LinkInteractionListener,
    onEvent: (ChatEvent) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val onSpoilerClick =
        remember(bubble.id, onEvent) { LinkInteractionListener { onEvent(ChatEvent.RevealSpoilers(bubble.id)) } }
    Text(
        text = texts.get(bubble, colors.text, onSpoilerClick, onLinkClick),
        color = colors.content,
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

/**
 * [content] with [meta] at its bottom end: after the last line of [content] when there is room for it there
 * ([lastLineWidth] of the content, or its whole width when that returns null), otherwise on a line of its own. One
 * layout rather than nested rows (`docs/PERFORMANCE.md` §4).
 */
@Composable
private fun TrailingMeta(
    meta: @Composable () -> Unit,
    lastLineWidth: (LayoutDirection) -> Int?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gap = MaterialTheme.spacing.small
    Layout(
        contents = listOf(content, meta),
        modifier = modifier,
    ) { (contentMeasurables, metaMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val contentPlaceable = contentMeasurables.firstOrNull()?.measure(loose)
        val metaPlaceable = metaMeasurables.first().measure(loose)
        val contentWidth = contentPlaceable?.width ?: 0
        val contentHeight = contentPlaceable?.height ?: 0
        val lineWidth = if (contentPlaceable == null) 0 else lastLineWidth(layoutDirection) ?: contentWidth
        val inlineWidth = if (lineWidth > 0) lineWidth + gap.roundToPx() + metaPlaceable.width else metaPlaceable.width
        val inline = inlineWidth <= constraints.maxWidth
        val width = max(contentWidth, if (inline) inlineWidth else metaPlaceable.width)
        val height = if (inline) max(contentHeight, metaPlaceable.height) else contentHeight + metaPlaceable.height
        val size = constraints.constrain(IntSize(width, height))
        layout(size.width, size.height) {
            contentPlaceable?.placeRelative(0, 0)
            metaPlaceable.placeRelative(size.width - metaPlaceable.width, size.height - metaPlaceable.height)
        }
    }
}

/** "edited", the time, and the state of an outgoing message, in `labelSmall` with tabular figures. */
@Composable
private fun BubbleMeta(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelSmall
        if (bubble.isEdited) Text(stringResource(R.string.chat_edited), color = colors.secondaryContent, style = style)
        Text(bubble.time, color = colors.secondaryContent, maxLines = 1, style = style)
        bubble.status?.let { StatusIcon(it, colors) }
    }
}

/** A clock while sending, an error when it failed, one check once sent, two in `primary` once read. */
@Composable
private fun StatusIcon(
    status: OutgoingStatus,
    colors: BubbleColors,
) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) =
        when (status) {
            OutgoingStatus.Pending -> UiR.drawable.ic_schedule to colors.secondaryContent
            OutgoingStatus.Failed -> UiR.drawable.ic_error to scheme.error
            OutgoingStatus.Sent -> UiR.drawable.ic_done to colors.secondaryContent
            OutgoingStatus.Read -> UiR.drawable.ic_done_all to scheme.primary
        }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        tint = tint,
    )
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
