package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.messageBubble
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.edgeToEdgeWidth
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.ceil
import kotlin.math.max

/**
 * A message in the conversation (`REQUIREMENTS.md` FR-C2, FR-C4, `docs/DESIGN_SYSTEM.md` §2.2, §4, §9): its bubble at
 * the end for outgoing messages, at the start for incoming ones, beside the sender's avatar in group chats.
 *
 * - **Bubble**: the [BubbleColors] of its side, a shape from the shape scale by its place in the group
 *   ([messageBubble], built once for the list: [BubbleResources.shapes]), at most [MaterialTheme.sizes]' share of the
 *   width. From top to bottom: the sender's name (first of a group in group chats), "Forwarded from …", the reply
 *   quote, the content ([MessageContentBody]), the text with its formatting and the link preview, with the time and
 *   state of the message at the bottom end, after the last line when it fits ([TrailingMeta]), inside the padding
 *   that keeps it clear of the round corners.
 * - **Reactions**: round badges on the bubble's bottom corner on the far side ([ReactionBadges]), overlapping it by
 *   its bottom padding, so they never cover the time; below the content for frames without a bubble.
 * - **Frames**, as the content asks:
 *   - *Emoji only* ([ChatItem.Bubble.isEmojiOnly]): 1 to 3 emoji in `displaySmall`, without a bubble, the time and
 *     state below them.
 *   - *Bare* content ([isBare]: stickers, animated emoji, dice, video messages) has no bubble; the time and state sit
 *     on a `mediaOverlay` pill at its bottom end.
 *   - *Edge-to-edge* media ([isEdgeToEdge]: photos, videos, GIFs, albums) fills a bubble as wide as itself, clipped to
 *     the bubble's shape. A caption follows below with the usual padding and meta; without one, the meta sits on a
 *     `mediaOverlay` pill over the media's bottom end.
 *   - *Padded* content (everything else) sits in the bubble's padding like text.
 * - **Screen readers** hear the bubble as one: "{sender}, {time}: {content}, {status}" ([bubbleDescription]), with
 *   the reply quote, the content's buttons, replying, copying and sending again ([messageShortcuts]) and the reaction
 *   badges as custom actions, and the actions sheet as its long click and a custom action. All of it is built in the
 *   `semantics` block, only when something reads the semantics tree, not in every bubble's composition.
 * - **Long press** anywhere on the row, also on media and links, opens the message's actions sheet
 *   ([ChatEvent.ShowMessageActions]) with a long-press haptic; whatever was pressed inside does not take it as a tap.
 * - **Reactions**: tapping a badge adds or takes back the user's reaction ([ChatEvent.ToggleReaction]) with a toggle
 *   haptic, the badge crossfading on the motion scheme's fast effects spring ([ReactionToggleState]); "+N" opens the
 *   actions sheet at its reactions picker ([ChatEvent.ShowAllReactions]), where all of them show.
 * - **Reply quote**: tapping it shows the replied message ([ChatEvent.OpenReply]).
 * - **Highlight**: after a jump to the bubble, a `secondaryContainer` band behind the row fades in and out
 *   ([jumpHighlight]), read in the draw phase.
 * - Everything shown is in [bubble], mapped by the ViewModel, in [fileState], the file of a voice message, song or
 *   document, in [playback], how a voice message or song plays, and in [uploads], the uploads of the bubble's messages
 *   being sent (cancelled by screen readers through a custom action too); the text is built once per message
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
    uploads: ImmutableMap<Long, MessageUpload> = persistentMapOf(),
) {
    val strings = LocalResources.current
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val highlightShape = MaterialTheme.shapes.medium
    val haptics = LocalHapticFeedback.current
    val showActions =
        remember(bubble.id, onEvent, haptics) {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onEvent(ChatEvent.ShowMessageActions(bubble.id))
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .jumpHighlight(resources.jump, bubble, highlightColor, highlightShape)
                .onLongPress(showActions)
                .clearAndSetSemantics {
                    contentDescription = bubbleDescription(strings, bubble)
                    onLongClick(label = strings.getString(R.string.message_actions)) {
                        showActions()
                        true
                    }
                    customActions =
                        bubbleActions(
                            resources = strings,
                            bubble = bubble,
                            fileState = fileState,
                            isPlaying = playback.isPlaying,
                            shortcuts = resources.shortcuts,
                            uploads = uploads,
                            onEvent = onEvent,
                        )
                },
        horizontalArrangement = if (bubble.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (bubble.hasAvatarColumn) SenderAvatarColumn(bubble.avatar)
        MessageBubble(
            bubble = bubble,
            fileState = fileState,
            playback = playback,
            resources = resources,
            onEvent = onEvent,
            uploads = uploads,
            modifier = Modifier.alignBy { it.bubbleBottom() },
        )
    }
}

/**
 * The screen reader's custom actions of [bubble], sending their events: [replyAction], [contentActions],
 * [messageShortcuts], a toggle per reaction but the paid one, all reactions where the bubble shows "+N", and the
 * actions sheet, last. Cancelling the [uploads] comes first, while there are some.
 */
@Suppress("LongParameterList") // The bubble and the states its actions depend on, and where they send events.
private fun bubbleActions(
    resources: Resources,
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    isPlaying: Boolean,
    shortcuts: MessageShortcuts,
    uploads: ImmutableMap<Long, MessageUpload>,
    onEvent: (ChatEvent) -> Unit,
): List<CustomAccessibilityAction> {
    fun action(
        label: String,
        event: ChatEvent,
    ) = CustomAccessibilityAction(label) {
        onEvent(event)
        true
    }
    val fixed =
        listOfNotNull(uploadAction(uploads), replyAction(bubble)) + contentActions(bubble, fileState, isPlaying) +
            messageShortcuts(bubble, shortcuts)
    val reactions =
        bubble.reactions.filter { it.label != ReactionLabel.Paid }.map { chip ->
            val label = reactionToggleLabel(resources, chip.label, chip.isChosen)
            action(label, ChatEvent.ToggleReaction(bubble.reactionsMessageId, chip.label))
        }
    // What "+N" opens: every reaction, where the badges have no room for all.
    val allReactions =
        if (bubble.reactions.size > MAX_REACTION_BADGES) {
            listOf(action(resources.getString(R.string.message_reactions_all), ChatEvent.ShowAllReactions(bubble.id)))
        } else {
            emptyList()
        }
    return fixed.map { action(resources.getString(it.label), it.event) } + reactions + allReactions +
        action(resources.getString(R.string.message_actions), ChatEvent.ShowMessageActions(bubble.id))
}

@Suppress("LongParameterList") // The bubble, its states and look, and where its content sends events.
@Composable
private fun MessageBubble(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    uploads: ImmutableMap<Long, MessageUpload>,
    modifier: Modifier = Modifier,
) {
    val style = resources.theme.styleOf(bubble.isOutgoing)
    val position = bubble.position
    val shape = resources.theme.shapes.of(bubble.isOutgoing, position.joinsPrevious, position.joinsNext)
    val sizes = MaterialTheme.sizes
    val bubbleModifier = modifier.maxWidthFraction(sizes.bubbleMaxWidthFraction, sizes.bubbleMaxWidth)
    val bare = bubble.isEmojiOnly || (bubble.album == null && bubble.content.isBare)
    val frame: @Composable (Modifier) -> Unit = { modifier ->
        when {
            bubble.isEmojiOnly -> {
                EmojiBubble(bubble, style.colors, onEvent, modifier)
            }

            bare -> {
                BareBubble(bubble, style.colors, onEvent, modifier)
            }

            bubble.album != null || bubble.content.isEdgeToEdge -> {
                MediaBubble(
                    bubble,
                    style,
                    shape,
                    resources,
                    onEvent,
                    uploads,
                    modifier,
                )
            }

            else -> {
                PaddedBubble(bubble, fileState, playback, style, shape, resources, onEvent, uploads, modifier)
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        if (bubble.reactions.isEmpty()) {
            frame(bubbleModifier)
        } else {
            WithReactionBadges(
                isOutgoing = bubble.isOutgoing,
                // Over the bubble's bottom padding, clear of the time; below content without a bubble.
                overlap = if (bare) 0.dp else MaterialTheme.spacing.small,
                modifier = bubbleModifier,
                bubble = { frame(Modifier) },
                badges = {
                    ReactionBadges(
                        bubble = bubble,
                        toggles = resources.reactions,
                        onToggle = rememberReactionToggle(bubble, resources.reactions, onEvent),
                        onMore = { onEvent(ChatEvent.ShowAllReactions(bubble.id)) },
                    )
                },
            )
        }
    }
}

/**
 * Padded content and text in a bubble of [shape], padded so the text and the time keep clear of the round corners.
 */
@Suppress("LongParameterList") // The bubble, its states and look, and where its content sends events.
@Composable
private fun PaddedBubble(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    style: BubbleStyle,
    shape: CornerBasedShape,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    uploads: ImmutableMap<Long, MessageUpload>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(style.colors.container, shape)
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        BubbleHeader(bubble, style.colors, onEvent)
        MessageContentBody(bubble, fileState, playback, style.colors, onEvent, uploads = uploads)
        BubbleBody(bubble, style, resources, onEvent)
    }
}

/**
 * Edge-to-edge media in a bubble as wide as the media and clipped to the bubble's [shape]: the name, forward and reply
 * above it with the usual padding; below it the caption and link preview with the meta; without either, the meta on a
 * pill over the media's bottom end. The media's own corners ([mediaCorners]) are where the media viewer's container
 * transform starts and ends.
 */
@Suppress("LongParameterList") // The bubble, its look, and what it passes on to its content.
@Composable
private fun MediaBubble(
    bubble: ChatItem.Bubble,
    style: BubbleStyle,
    shape: CornerBasedShape,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    uploads: ImmutableMap<Long, MessageUpload>,
    modifier: Modifier = Modifier,
) {
    val colors = style.colors
    val spacing = MaterialTheme.spacing
    val hasHeader = bubble.showsSenderName || bubble.forwardedFrom != null || bubble.reply != null
    val metaOnMedia = bubble.text.text.isEmpty() && bubble.linkPreview == null
    val mediaShape = remember(shape, hasHeader, metaOnMedia) { shape.mediaCorners(hasHeader, hasFooter = !metaOnMedia) }
    Column(
        modifier
            .width(edgeToEdgeWidth(bubble.content, isAlbum = bubble.album != null))
            .clip(shape)
            .background(colors.container),
    ) {
        if (hasHeader) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) { BubbleHeader(bubble, colors, onEvent) }
        }
        Box {
            MessageContentBody(
                bubble = bubble,
                fileState = null,
                playback = Playback.Stopped,
                colors = colors,
                onEvent = onEvent,
                mediaShape = mediaShape,
                uploads = uploads,
            )
            if (metaOnMedia) OverlayMeta(bubble, Modifier.align(Alignment.BottomEnd).padding(spacing.small))
        }
        if (!metaOnMedia) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) { BubbleBody(bubble, style, resources, onEvent) }
        }
    }
}

/**
 * The corners media shows in a bubble of this shape: the bubble's own, except square at the top below a header
 * ([hasHeader]) and at the bottom above a caption, link preview or reactions ([hasFooter]).
 */
private fun CornerBasedShape.mediaCorners(
    hasHeader: Boolean,
    hasFooter: Boolean,
): CornerBasedShape =
    copy(
        topStart = if (hasHeader) ZeroCornerSize else topStart,
        topEnd = if (hasHeader) ZeroCornerSize else topEnd,
        bottomEnd = if (hasFooter) ZeroCornerSize else bottomEnd,
        bottomStart = if (hasFooter) ZeroCornerSize else bottomStart,
    )

/**
 * The sender's name (first of a group in group chats), "Forwarded from …" and the reply quote, whichever there are;
 * the quote shows its message when tapped, if it opens one.
 */
@Composable
internal fun BubbleHeader(
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
 * The text and the link preview, whichever there are, with the meta after the text's last line when there is room;
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
    if (hasText && linkPreview == null) {
        val layout = remember { TextLayoutHolder() }
        TrailingMeta(meta = { BubbleMeta(bubble, colors) }, lastLineWidth = layout::lastLineWidth) {
            BubbleText(bubble, style, resources, onEvent) { layout.result = it }
        }
    } else {
        if (hasText) BubbleText(bubble, style, resources, onEvent)
        linkPreview?.let { LinkPreviewBlock(it, colors) }
        BubbleMeta(bubble, colors, Modifier.align(Alignment.End))
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
