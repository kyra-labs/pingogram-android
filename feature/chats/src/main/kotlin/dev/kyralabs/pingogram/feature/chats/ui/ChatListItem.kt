package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One chat in a chat list (`REQUIREMENTS.md` FR-L2, `docs/DESIGN_SYSTEM.md` §6): an M3 [ListItem] with the avatar, the
 * name with its badges, a one-line preview ([ChatPreviewText]), and the time with the unread badges at the end
 * ([ChatMeta]). At large font scales ([timeOnPreviewLine]) the time starts the preview's line instead, so the end
 * column is only as wide as the badges and the name keeps most of its line; the row stays two lines tall. The preview
 * stays one plain text: the list item reads its baselines while it measures, to tell one line from several. For the
 * main list, the archive and folders alike; swipe actions and selection wrap or extend it.
 *
 * Everything shown is in [item], mapped by the ViewModel: the row resolves texts and builds the preview's styled text,
 * and runs no effects (`docs/PERFORMANCE.md` §4). For screen readers it is one node that reads the whole row, unread
 * count included ([rowDescription], `docs/DESIGN_SYSTEM.md` §9), built only when something reads the semantics tree.
 * No fixed heights, so it grows with the font scale.
 *
 * The row's container sits in from its place in the list ([rowInsets]), while its content keeps the list item's
 * padding ([insetContentPadding]): at rest nothing shows it, but a pressed, selected or open row stands apart from the
 * edges of the list's sheet and from the rows around it (`docs/DESIGN_SYSTEM.md` §4, §6).
 *
 * Selected ([selected], `REQUIREMENTS.md` FR-L8), the row takes the list item's selected shape (`large` corners) in its
 * selected container (`secondaryContainer`) and its avatar morphs into a check. The list item morphs its shape on the
 * motion scheme's fast spatial spring and animates its container color. The colors come from the clickable list item's
 * own selected colors: its selectable variant would make every row a radio button for screen readers, and swapping
 * variants on entering selection mode would recreate the rows and skip the morphs.
 *
 * Open ([open]), its conversation showing beside the list on expanded windows, the row takes the same selected
 * container, in the list item's selected shape (`large` corners, where resting rows have `extraSmall` ones), and keeps
 * its avatar: the current item, as a navigation indicator marks the current destination, distinct from a row checked
 * in selection mode. Screen readers hear it as selected.
 *
 * @param onClick opens the chat, or selects it in selection mode; null makes the row not clickable.
 * @param onLongClick e.g. starts selection; only with [onClick].
 * @param onLongClickLabel what [onLongClick] does, for screen readers.
 * @param dragged the row is being swiped: it takes its `large` container, as when pressed ([ChatRow]).
 */
@Composable
internal fun ChatListItem(
    item: ChatListItemModel,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    selected: Boolean = false,
    open: Boolean = false,
    dragged: Boolean = false,
) {
    val title = item.title.resolve()
    val preview = previewText(item.preview, isFailed = item.outgoingStatus == OutgoingStatus.Failed)
    // Built in the semantics blocks, so only when something reads the semantics tree.
    val resources = LocalResources.current
    val leading = @Composable { ChatAvatar(item, title, selected, open) }
    val timeOnPreviewLine = timeOnPreviewLine()
    val shownPreview = if (timeOnPreviewLine) remember(preview, item.time) { preview.after(item.time) } else preview
    val trailing = @Composable { ChatMeta(item, showTime = !timeOnPreviewLine) }
    val supporting = @Composable { ChatPreviewText(shownPreview) }
    val headline = @Composable { ChatTitle(title, item.isSecret, item.badge, item.isMuted, item.isUnread) }
    val insets = rowInsets()
    val contentPadding = insetContentPadding(insets)
    if (onClick != null) {
        // A row being swiped covers what the swipe reveals from its first frame with its resting container, in the list
        // item's selected shape (`large`): the list item's own container is transparent at rest and would fade in.
        val draggedContainer =
            if (dragged) {
                val color = ListItemDefaults.colors().containerColor
                val shape = ListItemDefaults.shapes().selectedShape
                Modifier.drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
            } else {
                Modifier
            }
        ListItem(
            onClick = onClick,
            modifier =
                modifier
                    .padding(insets)
                    .then(draggedContainer)
                    .semantics {
                        contentDescription = rowDescription(resources, item, title, preview.text)
                        if (open) this.selected = true
                    },
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            shapes = listItemShapes(rounded = selected || open || dragged),
            colors = listItemColors(selected || open),
            contentPadding = contentPadding,
            content = headline,
        )
    } else {
        ListItem(
            modifier =
                modifier
                    .padding(insets)
                    .semantics(mergeDescendants = true) {
                        contentDescription = rowDescription(resources, item, title, preview.text)
                    },
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            contentPadding = contentPadding,
            content = headline,
        )
    }
}

/**
 * How far a row's container sits in from its place in the list: [MaterialTheme.spacing]'s small step from the sides of
 * the sheet, and half of Material's gap between segmented list items from the rows above and below, so the containers
 * of two selected rows stay that gap apart.
 */
@Composable
internal fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/**
 * The list item's content padding less [insets]: a row padded by [insets] keeps its content where the list item puts it
 * without them.
 */
internal fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

/**
 * The list item's colors, or its selected colors in place of its resting ones. At rest its container is transparent:
 * it would be the sheet's own `surface` under it, and every row would fill its area once more in every frame of a
 * scroll. It fades in and out from the same color, so a row turning selected looks as it did over its opaque
 * container. A row being swiped draws its container itself ([ChatListItem]), at once.
 */
@Composable
internal fun listItemColors(selected: Boolean): ListItemColors {
    val colors = ListItemDefaults.colors()
    if (!selected) return colors.copy(containerColor = colors.containerColor.copy(alpha = 0f))
    return colors.copy(
        containerColor = colors.selectedContainerColor,
        contentColor = colors.selectedContentColor,
        leadingContentColor = colors.selectedLeadingContentColor,
        trailingContentColor = colors.selectedTrailingContentColor,
        supportingContentColor = colors.selectedSupportingContentColor,
    )
}

/** The list item's shapes, with its selected shape (`large`) in place of its resting one while [rounded]. */
@Composable
internal fun listItemShapes(rounded: Boolean): ListItemShapes {
    val shapes = ListItemDefaults.shapes()
    return if (rounded) shapes.copy(shape = shapes.selectedShape) else shapes
}

/**
 * Decorative: the row's description names the chat, and its semantics say whether it is selected. On an [open] row the
 * placeholder takes the selection colors and the online dot's ring the row's container, `secondaryContainer`.
 */
@Composable
private fun ChatAvatar(
    item: ChatListItemModel,
    title: String,
    selected: Boolean,
    open: Boolean,
) {
    Avatar(
        photo = item.photo,
        name = title,
        id = item.id,
        size = MaterialTheme.sizes.avatarRow,
        isOnline = item.isOnline,
        placeholderIcon = item.placeholderIcon,
        onlineRingColor =
            if (open) ListItemDefaults.colors().selectedContainerColor else MaterialTheme.colorScheme.surface,
        selected = selected,
        onSelection = open,
    )
}

/**
 * The lock of a secret chat ([isSecret]), the name ([title]), its [badge], and the mute icon ([isMuted]); the name in
 * the emphasized style if [isUnread]. Also the title of a search result.
 */
@Composable
internal fun ChatTitle(
    title: String,
    isSecret: Boolean,
    badge: TitleBadge?,
    isMuted: Boolean,
    isUnread: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSecret) InlineIcon(UiR.drawable.ic_lock, colors.primary, MaterialTheme.sizes.inlineIcon)
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = with(MaterialTheme.typography) { if (isUnread) titleMediumEmphasized else titleMedium }.userContent,
        )
        when (badge) {
            TitleBadge.Verified -> {
                InlineIcon(
                    UiR.drawable.ic_verified_filled,
                    colors.primary,
                    MaterialTheme.sizes.inlineIcon,
                )
            }

            TitleBadge.Scam -> {
                WarningLabel(stringResource(R.string.chat_list_badge_scam))
            }

            TitleBadge.Fake -> {
                WarningLabel(stringResource(R.string.chat_list_badge_fake))
            }

            null -> {
                Unit
            }
        }
        if (isMuted) {
            InlineIcon(UiR.drawable.ic_notifications_off, colors.onSurfaceVariant, MaterialTheme.sizes.inlineIconSmall)
        }
    }
}

/** "Scam" or "Fake" in the error role, as Telegram's warning badges. */
@Composable
private fun WarningLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmallEmphasized,
    )
}

/** A decorative icon within a line of text: the row's description says what it means. */
@Composable
internal fun InlineIcon(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp,
) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(size), tint = tint)
}

@PingogramPreviews
@Composable
private fun ChatListItemPreview() {
    PingogramPreview {
        Column {
            ChatListPreviewData.items.take(PREVIEW_ROWS).forEach { ChatListItem(item = it, onClick = {}) }
        }
    }
}

private const val PREVIEW_ROWS = 4
