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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
 * A chat, contact, public chat or recent chat in search (`docs/DESIGN_SYSTEM.md` §6): an M3 [ListItem] with the small
 * avatar, the name with the chat list's badges ([ChatTitle]), and the username or member count below, on the search
 * bar's container, or on whatever [containerColor] the row lies on. A recent chat has a button that removes it
 * ([onRemove]). Screen readers hear the row as one ([chatDescription]), and the remove button on its own.
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
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = title,
                id = item.id,
                size = MaterialTheme.sizes.avatarSmall,
                isOnline = item.isOnline,
                placeholderIcon = item.placeholderIcon,
                onlineRingColor = containerColor,
            )
        },
        trailingContent =
            onRemove?.let { remove ->
                {
                    IconButton(onClick = remove) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.search_remove_recent, title),
                        )
                    }
                }
            },
        supportingContent =
            subtitle?.let { text ->
                {
                    Text(
                        text = text,
                        modifier = Modifier.clearAndSetSemantics {},
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        colors = rowColors(),
        content = { ChatTitle(title, item.isSecret, item.badge, item.isMuted, isUnread = false) },
    )
}

/**
 * A message in search: its chat's small avatar and name, the date at the end, and two lines of the message after its
 * sender in a group, the query's words highlighted ([highlight]) in `primary`. Screen readers hear "Chat, Sender:
 * text, date".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchMessageRow(
    item: SearchMessageItem,
    highlight: MatchHighlight,
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
    ListItem(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = chatTitle,
                id = item.chatId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = item.placeholderIcon,
                onlineRingColor = rowContainerColor,
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
        colors = rowColors(),
        content = { ChatTitle(chatTitle, isSecret = false, badge = item.badge, isMuted = false, isUnread = false) },
    )
}

/**
 * The people the user writes to most, side by side: each an avatar over the name, opening the chat with them
 * ([onOpen]); a button for screen readers, named by the person.
 */
@Composable
internal fun TopPeople(
    people: ImmutableList<SearchChatItem>,
    onOpen: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.small, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        items(people, key = { it.id }) { person -> Person(person, onClick = { onOpen(person.id) }) }
    }
}

@Composable
private fun Person(
    item: SearchChatItem,
    onClick: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sizes = MaterialTheme.sizes
    val title = item.title.resolve()
    val description = chatDescription(item, title, subtitle = null)
    Column(
        modifier =
            Modifier
                .width(sizes.avatarMedium + spacing.extraLarge)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = spacing.small)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            photo = item.photo,
            name = title,
            id = item.id,
            size = sizes.avatarMedium,
            isOnline = item.isOnline,
            placeholderIcon = item.placeholderIcon,
            onlineRingColor = rowContainerColor,
        )
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = spacing.extraSmall),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** The list item's colors on the search bar's container instead of its own. */
@Composable
private fun rowColors(): ListItemColors {
    val colors = ListItemDefaults.colors()
    return colors.copy(containerColor = Color.Transparent, contentColor = colors.contentColor)
}

/** [label] ("Ada:") in [labelColor], then [text] with every match of [highlight] in [match]. */
internal fun highlighted(
    label: String?,
    text: String,
    highlight: MatchHighlight,
    labelColor: Color,
    match: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        if (label != null) {
            withStyle(SpanStyle(color = labelColor)) { append(label) }
            append(' ')
        }
        val start = length
        append(text)
        highlight.ranges(text).forEach { range -> addStyle(match, start + range.first, start + range.last + 1) }
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
            SearchChatRow(SearchPreviewData.chats.last(), onClick = {}, onRemove = {})
            SearchMessageRow(SearchPreviewData.messages.first(), SearchPreviewData.highlight, onClick = {})
        }
    }
}
