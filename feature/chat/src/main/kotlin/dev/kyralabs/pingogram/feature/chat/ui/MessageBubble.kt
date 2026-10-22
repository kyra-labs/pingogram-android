package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.hasTextBlocks
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.textBlocks
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.messageBubble
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.bots.botButtonActions
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.edgeToEdgeWidth
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.max

/**
 * A message in the conversation (`REQUIREMENTS.md` FR-C2, FR-C4, `docs/DESIGN_SYSTEM.md` §2.2, §4, §9): its bubble at
 * the end for outgoing messages, at the start for incoming ones, beside the sender's avatar in group chats.
 *
 * - **Bubble**: the [BubbleColors] of its side, a shape from the shape scale by its place in the group
 *   ([messageBubble], built once for the list: [BubbleResources.shapes]), at most [MaterialTheme.sizes]' share of the
 *   width. From top to bottom: the sender's name (first of a group in group chats), "Forwarded from …", the reply
 *   quote ([ReplyQuoteBlock], a tonal card), the content ([MessageContentBody]), the text with its formatting and the
 *   link preview (a tonal card too), with the time and state of the message at the bottom end, after the last line
 *   when it fits, inside the padding (`spacing.medium` at the sides, `spacing.small` at the top and bottom) that keeps
 *   it clear of the round corners. A text bubble and a media bubble are one custom layout each
 *   ([rememberTextBubbleMeasurePolicy], [rememberMediaBubbleMeasurePolicy]), measured in one pass.
 * - **Time**: the time and the state show on the last bubble of a group and on a message alone
 *   ([ChatItem.Bubble.showsTime], decided by the ViewModel), as Google Messages shows them; tapping a bubble of the
 *   group where nothing inside takes the tap shows its own, and tapping again hides it ([ChatEvent.ToggleTime]).
 * - **Reactions**: round badges on the bubble's bottom corner on the far side ([ReactionBadges]), overlapping it by
 *   its bottom padding, so they never cover the time; below the content for frames without a bubble. Each has a 48 dp
 *   touch target that does not move the layout ([touchTarget]).
 * - **Not sent**: a message that failed to send has a "Not sent · Tap to retry" label under its bubble
 *   ([NotSentLabel], [WithFooter]), which sends it again ([ChatEvent.Resend]).
 * - **Frames**, as the content asks:
 *   - *Emoji only* ([ChatItem.Bubble.isEmojiOnly]): 1 to 3 emoji in `displaySmall`, without a bubble, the time and
 *     state (where they show) below them.
 *   - *Bare* content ([isBare]: stickers, animated emoji, dice, video messages) has no bubble; the time and state sit
 *     on a `mediaOverlay` pill at its bottom end.
 *   - *Edge-to-edge* media ([isEdgeToEdge]: photos, videos, GIFs, albums) fills a bubble as wide as itself, clipped to
 *     the bubble's shape. A caption follows below with the usual padding and meta; without one, the meta sits on a
 *     `mediaOverlay` pill over the media's bottom end.
 *   - *Padded* content (everything else) sits in the bubble's padding like text.
 * - **Screen readers** hear the bubble as one, with its time whether it shows or not: "{sender}, {time}: {content},
 *   {status}" ([bubbleDescription]), with the reply quote, the content's buttons, replying, copying and sending again
 *   ([messageShortcuts]) and the reaction badges as custom actions, and the actions sheet as its long click and a
 *   custom action. All of it is built in the `semantics` block, only when something reads the semantics tree, not in
 *   every bubble's composition.
 * - **Long press** anywhere on the row, also on media and links, opens the message's actions
 *   ([ChatEvent.ShowMessageActions]) with a long-press haptic, lifting the message for its floating menu
 *   ([MessageLiftState]) where that suits; whatever was pressed inside does not take it as a tap. Screen readers' long
 *   click opens the actions sheet.
 * - **Swipe to reply** ([swipeToReply]): where the user can reply ([canBeRepliedTo]) and outside selection mode, the
 *   bubble follows a horizontal swipe toward the end in the layout phase, revealing a reply icon drawn behind it;
 *   released past `sizes.swipeReplyDistance`, it replies ([ChatEvent.Reply]) with a `TextHandleMove` haptic at the
 *   threshold, then springs back.
 * - **Selection mode** ([selection], `docs/DESIGN_SYSTEM.md` §2.2, §6): a check at the row's start, filled in
 *   `primary` once [RowSelection.isSelected], when the bubble takes `secondaryContainer`; a tap or a long press
 *   anywhere on the row selects or deselects it ([SelectionEvent.Toggle]) with a toggle haptic, and nothing inside
 *   takes the tap. Screen readers hear whether it is selected, and its click and long click toggle it; outside
 *   selection mode a custom action starts it with the message ([SelectionEvent.Select]).
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
    selection: RowSelection? = null,
) {
    val strings = LocalResources.current
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val highlightShape = MaterialTheme.shapes.medium
    val showActions = rememberActionsOpener(bubble.id, onEvent, resources.lift)
    val isSelected = selection?.isSelected == true
    val toggle = rememberSelectionToggle(bubble.id, isSelected, onEvent)
    val swipe =
        if (selection == null && bubble.canBeRepliedTo(resources.shortcuts)) {
            rememberSwipeToReply(bubble.id, onEvent, resources.swipe)
        } else {
            null
        }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .jumpHighlight(resources.jump, bubble, highlightColor, highlightShape)
                .bubbleGestures(selecting = selection != null, toggle, showActions, swipe, isRtl)
                .clearAndSetSemantics {
                    contentDescription = bubbleDescription(strings, bubble)
                    if (selection != null) {
                        selectionSemantics(strings, isSelected, toggle)
                    } else {
                        onLongClick(label = strings.getString(R.string.message_actions)) {
                            showActions(null)
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
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection != null) SelectionCheck(isSelected)
        BubbleRowContent(
            bubble = bubble,
            fileState = fileState,
            playback = playback,
            resources = resources,
            onEvent = onEvent,
            uploads = uploads,
            isSelected = isSelected,
            modifier = Modifier.weight(1f).then(if (swipe != null) Modifier.swipeOffset(swipe) else Modifier),
        )
    }
}

/**
 * The bubble of a row, at the end for outgoing messages and at the start for incoming ones, beside the sender's avatar
 * in group chats; the list's rows and the message lifted for its floating menu ([MessageActionsMenu]) both show it.
 */
@Suppress("LongParameterList") // The bubble, its states and look, and where its content sends events.
@Composable
internal fun BubbleRowContent(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    uploads: ImmutableMap<Long, MessageUpload>,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (bubble.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (bubble.hasAvatarColumn) SenderAvatarColumn(bubble.avatar, bubble.senderUserId, onEvent)
        MessageBubble(
            bubble = bubble,
            fileState = fileState,
            playback = playback,
            resources = resources,
            onEvent = onEvent,
            uploads = uploads,
            isSelected = isSelected,
            modifier = Modifier.alignBy { it.bubbleBottom() },
        )
    }
}

/**
 * The screen reader's custom actions of [bubble], sending their events: [replyAction], [threadAction],
 * [contentActions], [messageShortcuts], the buttons a bot attached ([botButtonActions]), a toggle per reaction but the
 * paid one, all reactions where the bubble shows "+N", and the selecting it, then the actions sheet, last. Cancelling
 * the [uploads] comes first, while there are some.
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
        listOfNotNull(uploadAction(uploads), replyAction(bubble), profileAction(bubble), threadAction(bubble)) +
            contentActions(bubble, fileState, isPlaying) + messageShortcuts(bubble, shortcuts)
    val buttons =
        bubble.keyboard?.let { botButtonActions(resources, it) }.orEmpty().map { (label, event) ->
            action(label, event)
        }
    val select = action(resources.getString(R.string.selection_start), SelectionEvent.Select(bubble.id))
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
    return fixed.map { action(resources.getString(it.label), it.event) } + buttons + reactions + allReactions + select +
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
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = resources.theme.styleOf(bubble.isOutgoing, isSelected)
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
        BubbleStack(
            bubble = bubble,
            // Over the bubble's bottom padding, clear of the time; below content without a bubble.
            overlap = if (bare) 0.dp else MaterialTheme.spacing.small,
            reactions = resources.reactions,
            onEvent = onEvent,
            modifier = bubbleModifier,
            frame = frame,
        )
    }
}

/**
 * Padded content and text in a bubble of [shape], padded so the text and the time keep clear of the round corners: one
 * layout ([rememberTextBubbleMeasurePolicy]) for the header, the content, the text, the link preview and the meta.
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
    val textLayout = remember { TextLayoutHolder() }
    Layout(
        contents =
            listOf(
                {
                    BubbleHeader(bubble, style.colors, onEvent)
                    MessageContentBody(bubble, fileState, playback, style.colors, onEvent, uploads = uploads)
                },
                { if (bubble.text.text.isNotEmpty()) BubbleText(bubble, style, resources, onEvent, textLayout) },
                { bubble.linkPreview?.let { LinkPreviewBlock(it, style.colors) } },
                { if (bubble.showsTime) BubbleMeta(bubble, style.colors) },
            ),
        modifier = modifier.background(style.colors.container, shape),
        measurePolicy = rememberTextBubbleMeasurePolicy(textLayout),
    )
}

/**
 * Edge-to-edge media in a bubble as wide as the media and clipped to the bubble's [shape], one layout
 * ([rememberMediaBubbleMeasurePolicy]): the name, forward and reply above it with the usual padding; below it the
 * caption and link preview with the meta; without either, the meta (where it shows) on a pill over the media's bottom
 * end. The media's own corners ([mediaCorners]) are where the media viewer's container transform starts and ends.
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
    val hasHeader = bubble.showsSenderName || bubble.forwardedFrom != null || bubble.reply != null
    val captionless = bubble.text.text.isEmpty() && bubble.linkPreview == null
    val mediaShape = remember(shape, hasHeader, captionless) { shape.mediaCorners(hasHeader, hasFooter = !captionless) }
    val textLayout = remember { TextLayoutHolder() }
    Layout(
        contents =
            listOf(
                { BubbleHeader(bubble, colors, onEvent) },
                {
                    MessageContentBody(
                        bubble = bubble,
                        fileState = null,
                        playback = Playback.Stopped,
                        colors = colors,
                        onEvent = onEvent,
                        mediaShape = mediaShape,
                        uploads = uploads,
                        mediaDownloads = resources.mediaDownloads,
                    )
                },
                { if (captionless && bubble.showsTime) OverlayMeta(bubble) },
                { if (bubble.text.text.isNotEmpty()) BubbleText(bubble, style, resources, onEvent, textLayout) },
                { bubble.linkPreview?.let { LinkPreviewBlock(it, colors) } },
                { if (!captionless && bubble.showsTime) BubbleMeta(bubble, colors) },
            ),
        modifier =
            modifier
                .width(edgeToEdgeWidth(bubble.content, isAlbum = bubble.album != null))
                .clip(shape)
                .background(colors.container),
        measurePolicy = rememberMediaBubbleMeasurePolicy(textLayout),
    )
}

/**
 * The sender's name (first of a group in group chats), "Forwarded from …" and the reply quote, whichever there are;
 * the name opens the sender's profile and the quote shows its message when tapped, if it opens one.
 */
@Composable
internal fun BubbleHeader(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    if (bubble.showsSenderName) {
        val userId = bubble.senderUserId
        Text(
            text = bubble.sender.resolve(),
            // Its touch target reaches past the line, without moving what is around it; the bubble's own semantics
            // offer the profile to screen readers.
            modifier =
                if (userId != null) {
                    Modifier.touchTarget(
                        Modifier.clickable(interactionSource = null, indication = null) {
                            onEvent(ChatEvent.OpenProfile(userId))
                        },
                    )
                } else {
                    Modifier
                },
            color = MaterialTheme.colorScheme.senderNameColor(bubble.senderId),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.userContent,
        )
    }
    bubble.forwardedFrom?.let { forwarded ->
        Text(
            text = forwarded.resolve(),
            color = colors.secondaryContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // "Forwarded from" and an isolated name: the language's direction, not the layout's.
            style = MaterialTheme.typography.labelMedium.userContent,
        )
    }
    bubble.reply?.let { reply ->
        val messageId = reply.messageId
        val onOpen = remember(messageId, onEvent) { messageId?.let { { onEvent(ChatEvent.OpenReply(it)) } } }
        ReplyQuoteBlock(reply, colors, onOpen)
    }
}

/**
 * The text or caption in `bodyLarge` with its entities ([AnnotatedTextCache]); tapping a spoiler reveals it. Its code,
 * code blocks and hidden spoilers are rounded `small` blocks drawn behind it from its layout ([textBlocks]), in the
 * draw phase. Its layout is kept in [textLayout], for the bubble's layout to put the meta after its last line.
 */
@Composable
private fun BubbleText(
    bubble: ChatItem.Bubble,
    style: BubbleStyle,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    textLayout: TextLayoutHolder,
) {
    val onSpoilerClick =
        remember(bubble.id, onEvent) { LinkInteractionListener { onEvent(ChatEvent.RevealSpoilers(bubble.id)) } }
    val text = resources.texts.get(bubble, style.text, onSpoilerClick, resources.onLinkClick, resources.onMentionClick)
    if (text.hasTextBlocks()) {
        val blocks = remember { TextBlocksLayout() }
        val onLayout =
            remember(blocks, textLayout) {
                { result: TextLayoutResult ->
                    blocks.onTextLayout(result)
                    textLayout.result = result
                }
            }
        Text(
            text = text,
            modifier =
                Modifier.textBlocks(
                    layout = blocks,
                    codeColor = style.text.codeBackground,
                    spoilerColor = style.text.spoilerColor,
                    shape = MaterialTheme.shapes.small,
                    horizontalPadding = MaterialTheme.sizes.textBlockPadding,
                ),
            color = style.colors.content,
            style = MaterialTheme.typography.bodyLarge.userContent,
            onTextLayout = onLayout,
        )
    } else {
        Text(
            text = text,
            color = style.colors.content,
            style = MaterialTheme.typography.bodyLarge.userContent,
            onTextLayout = { textLayout.result = it },
        )
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
