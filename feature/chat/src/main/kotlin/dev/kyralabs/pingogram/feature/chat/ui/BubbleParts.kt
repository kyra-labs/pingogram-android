package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The blocks inside a bubble besides its text: the reply quote, the link preview and the reactions
// (docs/DESIGN_SYSTEM.md §2.2: tonal blocks of the bubble's side, chosen reactions in secondaryContainer).

/** The replied message: a tonal block with a bar in the sender's color, the name over one line of its text. */
@Composable
internal fun ReplyQuoteBlock(
    reply: ReplyQuote,
    colors: BubbleColors,
) {
    val accent =
        reply.senderId?.let { MaterialTheme.colorScheme.senderNameColor(it) } ?: MaterialTheme.colorScheme.primary
    QuoteBlock(colors.block, accent) {
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

/** The reactions as small tonal chips, wrapping onto more lines when they do not fit. */
@Composable
internal fun ReactionsRow(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
) {
    val spacing = MaterialTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        bubble.reactions.forEach { ReactionChipView(it, colors) }
    }
}

/** A reaction's emoji and count; in `secondaryContainer` when the user chose it too. */
@Composable
private fun ReactionChipView(
    reaction: ReactionChip,
    colors: BubbleColors,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (reaction.isChosen) scheme.secondaryContainer else colors.block
    val content = if (reaction.isChosen) scheme.onSecondaryContainer else colors.blockContent
    Row(
        modifier =
            Modifier
                .background(container, MaterialTheme.shapes.small)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val label = reaction.label) {
            is ReactionLabel.Emoji -> Text(label.emoji, style = MaterialTheme.typography.labelLarge)
            ReactionLabel.Paid -> Text(PAID_REACTION_EMOJI, style = MaterialTheme.typography.labelLarge)
            ReactionLabel.CustomEmoji -> CustomEmojiPlaceholder(content)
        }
        Text(reaction.count.toString(), color = content, style = MaterialTheme.typography.labelMedium)
    }
}

/** Where a custom emoji goes until custom emoji are drawn: a dot of the emoji's size. */
@Composable
private fun CustomEmojiPlaceholder(color: Color) {
    Spacer(
        Modifier
            .size(MaterialTheme.sizes.inlineIconSmall)
            .background(color, CircleShape),
    )
}

/** A block inside a bubble: [container] in the small shape, with a bar of [accent] along its start edge. */
@Composable
private fun QuoteBlock(
    container: Color,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val barWidth = MaterialTheme.sizes.quoteBarWidth
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
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
