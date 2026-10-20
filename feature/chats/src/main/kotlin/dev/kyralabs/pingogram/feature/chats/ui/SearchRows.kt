package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat or contact in a plain list, as the New chat screen lists contacts (`docs/DESIGN_SYSTEM.md` §6): an M3
 * [ListItem] with the small avatar, the name with the chat list's badges ([ChatTitle]), and the username or member
 * count below, on whatever [containerColor] the row lies on. Search shows the same rows in segmented groups
 * ([SearchChatSegment]). A recent chat has a button that removes it ([onRemove]). Screen readers hear the row as one
 * ([chatDescription]), and the remove button on its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchChatRow(
    item: SearchChatItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    containerColor: Color = rowContainerColor,
) {
    val title = item.title.resolve()
    val subtitle = item.subtitle?.resolve()
    val description = chatDescription(item, title, subtitle)
    ListItem(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        leadingContent = { ChatAvatar(item, title, containerColor) },
        trailingContent = onRemove?.let { remove -> { RemoveRecentButton(title, remove) } },
        supportingContent = subtitle?.let { text -> { ChatSubtitle(text) } },
        colors = rowColors(),
        content = { ChatTitle(title, item.isSecret, item.badge, item.isMuted, isUnread = false) },
    )
}

/**
 * A chat, contact, public chat or recent chat in search's results (`docs/DESIGN_SYSTEM.md` §6, "Global search"): what
 * [SearchChatRow] shows, as a [SegmentedListItem] of its section's group, its corners from its place there ([shapes]),
 * on the segmented list item's container. Screen readers hear it as one, and the remove button ([onRemove]) on its
 * own.
 */
@Composable
internal fun SearchChatSegment(
    item: SearchChatItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val title = item.title.resolve()
    val subtitle = item.subtitle?.resolve()
    val description = chatDescription(item, title, subtitle)
    val colors = ListItemDefaults.segmentedColors()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.semantics { contentDescription = description },
        leadingContent = { ChatAvatar(item, title, colors.containerColor) },
        trailingContent = onRemove?.let { remove -> { RemoveRecentButton(title, remove) } },
        supportingContent = subtitle?.let { text -> { ChatSubtitle(text) } },
        colors = colors,
        content = { ChatTitle(title, item.isSecret, item.badge, item.isMuted, isUnread = false) },
    )
}

/** A chat's small avatar, its online dot ringed in [ringColor], the color the row lies on. */
@Composable
private fun ChatAvatar(
    item: SearchChatItem,
    title: String,
    ringColor: Color,
) {
    Avatar(
        photo = item.photo,
        name = title,
        id = item.id,
        size = MaterialTheme.sizes.avatarSmall,
        isOnline = item.isOnline,
        placeholderIcon = item.placeholderIcon,
        onlineRingColor = ringColor,
    )
}

/** The button that takes the recent chat [title] out of the recent chats. */
@Composable
private fun RemoveRecentButton(
    title: String,
    onRemove: () -> Unit,
) {
    IconButton(onClick = onRemove) {
        Icon(
            painter = painterResource(UiR.drawable.ic_close),
            contentDescription = stringResource(R.string.search_remove_recent, title),
        )
    }
}

/** A chat's second line, which screen readers hear with the row. */
@Composable
private fun ChatSubtitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics {},
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * A message in search, a [SegmentedListItem] of the messages' group ([shapes]): its chat's small avatar and name, the
 * date at the end, and two lines of the message after its sender in a group, the query's words highlighted
 * ([highlight]) in `primary`. Screen readers hear "Chat, Sender: text, date".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchMessageRow(
    item: SearchMessageItem,
    highlight: MatchHighlight,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatTitle = item.chatTitle.resolve()
    val sender = item.sender?.let { stringResource(R.string.chat_list_sender, it.resolve()) }
    val text = item.preview.resolve()
    val colors = MaterialTheme.colorScheme
    val match = SpanStyle(color = colors.primary, fontWeight = MaterialTheme.typography.bodyMediumEmphasized.fontWeight)
    val preview =
        remember(sender, text, highlight, colors.onSurface, match) {
            highlighted(sender, text, highlight, colors.onSurface, match)
        }
    val description =
        listOfNotNull(
            chatTitle,
            listOfNotNull(sender, text).joinToString(" "),
            item.date,
        ).joinToString(", ")
    val itemColors = ListItemDefaults.segmentedColors()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.semantics { contentDescription = description },
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = chatTitle,
                id = item.chatId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = item.placeholderIcon,
                onlineRingColor = itemColors.containerColor,
            )
        },
        trailingContent = {
            Text(
                text = item.date,
                modifier = Modifier.clearAndSetSemantics {},
                color = colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        supportingContent = {
            Text(
                text = preview,
                modifier = Modifier.clearAndSetSemantics {},
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        colors = itemColors,
        content = { ChatTitle(chatTitle, isSecret = false, badge = item.badge, isMuted = false, isUnread = false) },
    )
}

/** The list item's colors on the search bar's container instead of its own. */
@Composable
private fun rowColors(): ListItemColors {
    val colors = ListItemDefaults.colors()
    return colors.copy(containerColor = Color.Transparent, contentColor = colors.contentColor)
}

/**
 * What screen readers say for a chat in search (`docs/DESIGN_SYSTEM.md` §9): "Ada Lovelace, Verified, Online, Muted,
 * @ada", with the chat list's words for the states.
 */
@Composable
@ReadOnlyComposable
internal fun chatDescription(
    item: SearchChatItem,
    title: String,
    subtitle: String?,
): String {
    val resources: Resources = LocalResources.current
    return buildList {
        add(title)
        when (item.badge) {
            TitleBadge.Verified -> add(resources.getString(R.string.chat_list_a11y_verified))
            TitleBadge.Scam -> add(resources.getString(R.string.chat_list_badge_scam))
            TitleBadge.Fake -> add(resources.getString(R.string.chat_list_badge_fake))
            null -> Unit
        }
        if (item.isSecret) add(resources.getString(R.string.chat_list_a11y_secret))
        if (item.isOnline) add(resources.getString(R.string.chat_list_a11y_online))
        if (item.isMuted) add(resources.getString(R.string.chat_list_a11y_muted))
        if (subtitle != null) add(subtitle)
    }.joinToString(", ")
}

@PingogramPreviews
@Composable
private fun SearchRowsPreview() {
    PingogramPreview {
        Column {
            SearchChatRow(SearchPreviewData.chats.first(), onClick = {})
            SearchChatSegment(SearchPreviewData.chats.last(), ListItemDefaults.segmentedShapes(0, 2), {}, onRemove = {})
            SearchMessageRow(
                item = SearchPreviewData.messages.first(),
                highlight = SearchPreviewData.highlight,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                onClick = {},
            )
        }
    }
}
