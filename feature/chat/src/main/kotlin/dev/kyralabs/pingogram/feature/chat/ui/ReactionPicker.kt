package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The reactions picker (`REQUIREMENTS.md` FR-C7): a header with a back button to the actions, then, in one lazy grid
 * that fills the sheet as far as it needs, the message's own reactions ([MessageReactionsCarousel]) and every reaction
 * the user can add by section, as [ReactionToggleButton]s in cells at least as wide as they are tall, wider with the
 * font scale. Only what TDLib offers the user shows: reactions that need Telegram Premium are not offered to users
 * without it.
 */
@Composable
internal fun ReactionPickerContent(
    sheet: MessageActionsSheet,
    picker: ReactionPicker,
    choose: (ChatEvent) -> Unit,
    onBack: () -> Unit,
) {
    val style = rememberReactionButtonStyle()
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motionScheme
    // Cells grow with the font scale, as the emoji in them do.
    val cellWidth = style.height * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column {
        PickerHeader(onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cellWidth),
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (picker.onMessage.isNotEmpty()) {
                sectionHeader(R.string.reaction_picker_on_message)
                item(key = ON_MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = ON_MESSAGE_KEY) {
                    MessageReactionsCarousel(sheet, picker.onMessage, choose)
                }
            }
            picker.sections.forEach { section ->
                sectionHeader(section.kind.title)
                items(
                    items = section.reactions,
                    key = { it.label.key },
                    contentType = { REACTION_CONTENT_TYPE },
                ) { reaction ->
                    ReactionToggleButton(
                        reaction = reaction,
                        picture = sheet.pictureOf(reaction.label),
                        style = style,
                        onToggle = { choose(ChatEvent.ToggleReaction(sheet.reactionsMessageId, reaction.label)) },
                        modifier =
                            Modifier.fillMaxWidth().animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                            ),
                    )
                }
            }
        }
    }
}

/** The picker's header: the back button to the actions and its title, `titleLargeEmphasized`, a heading. */
@Composable
private fun PickerHeader(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(UiR.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.reaction_picker_back),
            )
        }
        Text(
            text = stringResource(R.string.reaction_picker_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A section's name across the grid: `titleSmallEmphasized` in `primary` (`docs/DESIGN_SYSTEM.md` §3.3), a heading. */
private fun LazyGridScope.sectionHeader(
    @StringRes title: Int,
) {
    item(key = "header:$title", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER_CONTENT_TYPE) {
        Text(
            text = stringResource(title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
            modifier =
                Modifier
                    .padding(top = MaterialTheme.spacing.medium)
                    .semantics { heading() },
        )
    }
}

/**
 * The reactions on the message, all of them, as a [HorizontalUncontainedCarousel] of reaction chips: each its emoji
 * over how often it was added, on `surfaceContainerHighest`, or `secondaryContainer` with a `primary` outline if the
 * user added it too (the carousel's mask border, so not by color alone), masked to the `large` shape. Tapping one but
 * the paid star adds or takes back the user's reaction with a toggle haptic, as the badges on the bubble do. The chips
 * are [reactionTile][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes]
 * wide, wider with the font scale, so a count never clips.
 */
@Composable
private fun MessageReactionsCarousel(
    sheet: MessageActionsSheet,
    reactions: ImmutableList<ReactionChip>,
    choose: (ChatEvent) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    HorizontalUncontainedCarousel(
        state = rememberCarouselState { reactions.size },
        itemWidth = MaterialTheme.sizes.reactionTile * fontScale.coerceAtLeast(1f),
        itemSpacing = MaterialTheme.spacing.small,
    ) { index ->
        val reaction = reactions[index]
        MessageReactionChip(
            reaction = reaction,
            picture = sheet.pictureOf(reaction.label),
            onToggle =
                { choose(ChatEvent.ToggleReaction(sheet.reactionsMessageId, reaction.label)) }
                    .takeIf { reaction.label != ReactionLabel.Paid },
            modifier =
                Modifier.maskClip(MaterialTheme.shapes.large).then(
                    // The user's own reaction shows by more than its color: a primary outline along the mask.
                    if (reaction.isChosen) {
                        Modifier.maskBorder(
                            BorderStroke(MaterialTheme.sizes.reactionBadgeRing, MaterialTheme.colorScheme.primary),
                            MaterialTheme.shapes.large,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** A chip of [MessageReactionsCarousel]: a button if [onToggle] is not null, one item for screen readers. */
@Composable
private fun MessageReactionChip(
    reaction: ReactionChip,
    picture: Int?,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (reaction.isChosen) scheme.secondaryContainer else scheme.surfaceContainerHighest
    val content = if (reaction.isChosen) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    val clickable =
        onToggle?.let { toggle ->
            Modifier.clickable(role = Role.Button) {
                val haptic = if (reaction.isChosen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                haptics.performHapticFeedback(haptic)
                toggle()
            }
        } ?: Modifier
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(container)
                .then(clickable)
                .clearAndSetSemantics {
                    contentDescription = reactionChipLabel(resources, reaction, isButton = onToggle != null)
                    if (onToggle != null) role = Role.Button
                }.padding(vertical = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val style = MaterialTheme.typography.titleLarge
        // A line of the emoji's style tall, so a custom emoji's chip is as tall as the others.
        Box(
            modifier = Modifier.heightIn(min = with(LocalDensity.current) { style.lineHeight.toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            ReactionGlyph(
                label = reaction.label,
                style = style,
                customEmojiSize = MaterialTheme.sizes.inlineIcon,
                placeholderColor = { content },
                customEmojiPicture = picture,
            )
        }
        Text(
            text = reaction.count.toString(),
            color = content,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** The picture of [label] if it is a custom emoji the sheet has one for. */
internal fun MessageActionsSheet.pictureOf(label: ReactionLabel): Int? =
    (label as? ReactionLabel.CustomEmoji)?.let { customEmojiPictures[it.customEmojiId] }

/** A lazy grid key for [this] (a saveable string): the emoji, or the custom emoji's id. */
private val ReactionLabel.key: String
    get() =
        when (this) {
            is ReactionLabel.Emoji -> "emoji:$emoji"
            is ReactionLabel.CustomEmoji -> "custom:$customEmojiId"
            ReactionLabel.Paid -> "paid"
        }

private val ReactionSectionKind.title: Int
    @StringRes get() =
        when (this) {
            ReactionSectionKind.Top -> R.string.reaction_picker_top
            ReactionSectionKind.Recent -> R.string.reaction_picker_recent
            ReactionSectionKind.Popular -> R.string.reaction_picker_popular
        }

private const val ON_MESSAGE_KEY = "onMessage"
private const val HEADER_CONTENT_TYPE = "header"
private const val REACTION_CONTENT_TYPE = "reaction"

@PingogramPreviews
@Composable
private fun ReactionPickerPreview() {
    PingogramPreview {
        MessageActionsBottomSheet(ChatPreviewData.incomingMessageActions.copy(showsPicker = true), onEvent = {})
    }
}
