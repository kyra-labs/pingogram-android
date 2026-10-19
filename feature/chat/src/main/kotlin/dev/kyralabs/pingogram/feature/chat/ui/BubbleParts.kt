package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

// The blocks inside a bubble besides its text: the reply quote, the link preview and the reactions
// (docs/DESIGN_SYSTEM.md §2.2: tonal blocks of the bubble's side, chosen reactions in secondaryContainer).

/**
 * The replied message: a tonal block with a bar in the sender's color, the name over one line of its text. With
 * [onOpen] it is a button that shows the replied message, with its state layer clipped to the block. Inside a bubble,
 * screen readers reach it as the bubble's custom action instead ([replyAction]).
 */
@Composable
internal fun ReplyQuoteBlock(
    reply: ReplyQuote,
    colors: BubbleColors,
    onOpen: (() -> Unit)? = null,
) {
    val accent =
        reply.senderId?.let { MaterialTheme.colorScheme.senderNameColor(it) } ?: MaterialTheme.colorScheme.primary
    val openLabel = stringResource(R.string.chat_reply_open)
    val clickable =
        onOpen?.let { Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = it) } ?: Modifier
    QuoteBlock(colors.block, accent, clickable) {
        reply.sender?.let { sender ->
            Text(
                text = sender.resolve(),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = reply.text.resolve(),
            color = colors.blockContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The link's site name in `primary`, its title, and a few lines of its description, on a tonal block. */
@Composable
internal fun LinkPreviewBlock(
    preview: LinkPreviewCard,
    colors: BubbleColors,
) {
    val primary = MaterialTheme.colorScheme.primary
    QuoteBlock(colors.block, primary) {
        if (preview.siteName.isNotBlank()) {
            Text(
                text = preview.siteName,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (preview.title.isNotBlank()) {
            Text(
                text = preview.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = LINK_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (preview.description.isNotBlank()) {
            Text(
                text = preview.description,
                color = colors.blockContent,
                maxLines = LINK_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The reactions as small tonal chips, wrapping onto more lines when they do not fit. With [onToggle], tapping a chip
 * adds or takes back the user's reaction (not the paid star reaction, which is changed elsewhere); [toggles] crossfades
 * it meanwhile. Inside a bubble, screen readers reach them as the bubble's custom actions instead.
 */
@Composable
internal fun ReactionsRow(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    toggles: ReactionToggleState,
    onToggle: (ReactionChip) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val messageId = bubble.reactionsMessageId
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        bubble.reactions.forEach { chip ->
            ReactionChipView(
                reaction = chip,
                colors = colors,
                chosenFraction = { toggles.chosenFraction(messageId, chip) },
                onClick = { onToggle(chip) }.takeIf { chip.label != ReactionLabel.Paid },
            )
        }
    }
}

/**
 * A reaction's emoji and count, in the bubble's block colors, or in `secondaryContainer` when the user chose it too:
 * [chosenFraction] blends them, read in the draw phase. A button if [onClick] is not null, its state layer clipped to
 * the chip.
 */
@Composable
private fun ReactionChipView(
    reaction: ReactionChip,
    colors: BubbleColors,
    chosenFraction: () -> Float,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val container = { lerp(colors.block, scheme.secondaryContainer, chosenFraction()) }
    val content = { lerp(colors.blockContent, scheme.onSecondaryContainer, chosenFraction()) }
    val clickable = onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .then(clickable)
                .drawBehind { drawRect(container()) }
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReactionGlyph(reaction.label, MaterialTheme.typography.labelLarge, MaterialTheme.sizes.inlineIconSmall, content)
        BasicText(
            text = reaction.count.toString(),
            style = LocalTextStyle.current.merge(MaterialTheme.typography.labelMedium),
            color = content,
        )
    }
}

/**
 * A reaction's emoji in [style]; the star of the paid reaction; for a custom emoji, until custom emoji are drawn, a
 * dot of [customEmojiSize] in [placeholderColor].
 */
@Composable
internal fun ReactionGlyph(
    label: ReactionLabel,
    style: TextStyle,
    customEmojiSize: Dp,
    placeholderColor: ColorProducer,
) {
    when (label) {
        is ReactionLabel.Emoji -> Text(label.emoji, style = style)
        ReactionLabel.Paid -> Text(PAID_REACTION_EMOJI, style = style)
        is ReactionLabel.CustomEmoji -> CustomEmojiPlaceholder(customEmojiSize, placeholderColor)
    }
}

/** Where a custom emoji goes until custom emoji are drawn: a dot of the emoji's size, its color read when drawn. */
@Composable
private fun CustomEmojiPlaceholder(
    size: Dp,
    color: ColorProducer,
) {
    Spacer(
        Modifier
            .size(size)
            .drawBehind { drawCircle(color()) },
    )
}

/**
 * A block inside a bubble: [container] in the small shape, with a bar of [accent] along its start edge; [modifier]
 * goes inside the clip, so a click's state layer keeps the shape.
 */
@Composable
private fun QuoteBlock(
    container: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val barWidth = MaterialTheme.sizes.quoteBarWidth
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .then(modifier)
                .background(container)
                .leadingBar(accent, barWidth)
                .padding(start = barWidth + spacing.small, end = spacing.small)
                .padding(vertical = spacing.extraSmall),
        content = content,
    )
}

/** A bar of [color], [width] wide, along the start edge; drawn, not laid out. */
private fun Modifier.leadingBar(
    color: Color,
    width: Dp,
): Modifier =
    drawBehind {
        val barWidth = width.toPx()
        val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - barWidth
        drawRect(color, topLeft = Offset(x, 0f), size = Size(barWidth, size.height))
    }

private const val LINK_TITLE_MAX_LINES = 2
private const val LINK_DESCRIPTION_MAX_LINES = 3

/** The paid star reaction of channels. */
private const val PAID_REACTION_EMOJI = "⭐"
