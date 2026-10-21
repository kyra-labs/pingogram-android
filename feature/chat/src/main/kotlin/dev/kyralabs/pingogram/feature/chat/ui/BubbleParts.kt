package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

// The blocks inside a bubble besides its text, the reply quote and the link preview (docs/DESIGN_SYSTEM.md §2.2, §4:
// tonal cards in the block tone of the bubble's side), and the reaction badges on its corner (chosen reactions in
// secondaryContainer).

/**
 * The replied message: a tonal card, the name in the sender's color over one line of its text. With [onOpen] it is a
 * button that shows the replied message, with its state layer clipped to the card. Inside a bubble, screen readers
 * reach it as the bubble's custom action instead ([replyAction]).
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
    BlockCard(colors.block, clickable) {
        reply.sender?.let { sender ->
            Text(
                text = sender.resolve(),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
        Text(
            text = reply.text.resolve(),
            color = colors.blockContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
    }
}

/** The link's site name in `primary`, its title, and a few lines of its description, on a tonal card. */
@Composable
internal fun LinkPreviewBlock(
    preview: LinkPreviewCard,
    colors: BubbleColors,
) {
    val primary = MaterialTheme.colorScheme.primary
    BlockCard(colors.block) {
        if (preview.siteName.isNotBlank()) {
            Text(
                text = preview.siteName,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
        if (preview.title.isNotBlank()) {
            Text(
                text = preview.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = LINK_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.userContent,
            )
        }
        if (preview.description.isNotBlank()) {
            Text(
                text = preview.description,
                color = colors.blockContent,
                maxLines = LINK_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.userContent,
            )
        }
    }
}

/**
 * The reactions of [bubble] as small round badges side by side, as Google's Material 3 Expressive messaging shows them
 * on a bubble's corner: up to [MAX_REACTION_BADGES], then "+N" for the rest, which opens the message's reactions picker
 * ([onMore]), where all of them show. With [onToggle], tapping a badge adds or takes back the user's reaction (not the
 * paid star reaction, which is changed elsewhere); [toggles] crossfades it meanwhile. Inside a bubble's row, screen
 * readers reach them as the bubble's custom actions instead.
 */
@Composable
internal fun ReactionBadges(
    bubble: ChatItem.Bubble,
    toggles: ReactionToggleState,
    onToggle: (ReactionChip) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reactions = bubble.reactions
    val messageId = bubble.reactionsMessageId
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = if (reactions.size > MAX_REACTION_BADGES) MAX_REACTION_BADGES else reactions.size
        for (index in 0 until shown) {
            val chip = reactions[index]
            ReactionBadge(
                reaction = chip,
                chosenFraction = { toggles.chosenFraction(messageId, chip) },
                onClick = { onToggle(chip) }.takeIf { chip.label != ReactionLabel.Paid },
            )
        }
        if (reactions.size > shown) MoreReactionsBadge(reactions.size - shown, onMore)
    }
}

/**
 * A reaction's emoji, with its count when more than one person added it, on a round `surfaceContainerHighest` badge,
 * or `secondaryContainer` when the user chose it too: [chosenFraction] blends them, read in the draw phase. A button
 * if [onClick] is not null. A custom emoji without its picture shows a tonal dot: the content color as a tint of the
 * badge ([CUSTOM_EMOJI_TINT]), a placeholder rather than a dark mark.
 */
@Composable
private fun ReactionBadge(
    reaction: ReactionChip,
    chosenFraction: () -> Float,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val container = { lerp(scheme.surfaceContainerHighest, scheme.secondaryContainer, chosenFraction()) }
    val content = { lerp(scheme.onSurfaceVariant, scheme.onSecondaryContainer, chosenFraction()) }
    BadgeFrame(container, onClick) {
        ReactionGlyph(
            reaction.label,
            MaterialTheme.typography.titleMedium,
            MaterialTheme.sizes.inlineIconSmall,
            { lerp(container(), content(), CUSTOM_EMOJI_TINT) },
        )
        if (reaction.count > 1) {
            BasicText(
                text = reaction.count.toString(),
                style = LocalTextStyle.current.merge(MaterialTheme.typography.labelMedium),
                color = content,
            )
        }
    }
}

/** "+N": the reactions beyond the badges shown, which opens the message's reactions picker ([onClick]). */
@Composable
private fun MoreReactionsBadge(
    count: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BadgeFrame({ scheme.surfaceContainerHighest }, onClick) {
        Text(
            text = stringResource(R.string.chat_reactions_more, count),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * A reaction badge's frame: a circle at least [reactionBadge][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes]
 * wide, a pill when its content is wider, in [container], with a ring in the conversation's `surface` that sets it off
 * from the bubble it overlaps. A button if [onClick] is not null: its touch target is 48 dp tall without the badge
 * taking more room ([touchTarget]), its state layer clipped to the circle.
 */
@Composable
private fun BadgeFrame(
    container: ColorProducer,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val sizes = MaterialTheme.sizes
    val interactions = remember { MutableInteractionSource() }
    val target =
        onClick?.let {
            Modifier.touchTarget(Modifier.clickable(interactions, indication = null, role = Role.Button, onClick = it))
        } ?: Modifier
    val stateLayer = if (onClick != null) Modifier.indication(interactions, ripple()) else Modifier
    Row(
        modifier =
            target
                .defaultMinSize(minWidth = sizes.reactionBadge, minHeight = sizes.reactionBadge)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(sizes.reactionBadgeRing)
                .clip(CircleShape)
                .then(stateLayer)
                .drawBehind { drawRect(container()) }
                .padding(horizontal = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A card inside a bubble, as Google's Material 3 Expressive messaging shows quotes and previews: [container] in the
 * medium shape, without a bar; [modifier] goes inside the clip, so a click's state layer keeps the shape.
 */
@Composable
private fun BlockCard(
    container: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .then(modifier)
                .background(container)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        content = content,
    )
}

/** How strongly a custom emoji's placeholder tints its badge with the content color: Material's disabled opacity. */
private const val CUSTOM_EMOJI_TINT = 0.38f

/** How many reaction badges a bubble shows before "+N". */
internal const val MAX_REACTION_BADGES = 3

private const val LINK_TITLE_MAX_LINES = 2
private const val LINK_DESCRIPTION_MAX_LINES = 3
