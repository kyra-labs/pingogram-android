package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

// The top people of search's suggestions (SearchResults.kt), above the recent chats.

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
