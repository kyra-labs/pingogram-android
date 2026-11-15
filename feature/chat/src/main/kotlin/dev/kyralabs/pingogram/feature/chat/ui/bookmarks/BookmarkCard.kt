package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A bookmark's card (the owner's board 34b), a filled `Card` on `surfaceContainerLow` with `large` corners: the chat's
 * avatar (`avatarSmall`, in its kind's shape) and name in `titleSmallEmphasized`, when it was saved in `labelSmall`,
 * and "Remove bookmark" (`IconButtonDefaults.shapes()`); who sent the message in `labelLarge` `primary` over three
 * lines of it in `bodyMedium`; the note in `bodySmall` `onSurfaceVariant`; the tags as small pills with a dot of their
 * hue. A tap opens the message in its chat, a long press its bookmark's sheet ("Edit tags", [onEdit]); screen readers
 * hear the card as one item ("Open in chat" as its click) with "Edit tags" and "Remove bookmark" as its custom actions.
 */
@Composable
internal fun BookmarkCard(
    item: BookmarkItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) {
    val remove = LocalResources.current.getString(R.string.bookmark_remove)
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().clip(shape).cardGestures(onOpen, onEdit, onRemove),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            CardHeader(item, onRemove, remove)
            if (item.sender.isNotEmpty()) {
                Text(
                    text = item.sender,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.userContent,
                )
            }
            Text(
                text = item.text,
                maxLines = TEXT_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
            if (item.note.isNotEmpty()) {
                Text(
                    text = item.note,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = NOTE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.userContent,
                )
            }
            if (item.tags.isNotEmpty()) TagPills(item)
        }
    }
}

/** A tap opens, a long press edits; screen readers hear "Open in chat" with "Edit tags" and "Remove bookmark". */
@Composable
private fun Modifier.cardGestures(
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
): Modifier {
    val resources = LocalResources.current
    val openLabel = resources.getString(R.string.bookmarks_open_in_chat)
    val edit = resources.getString(R.string.bookmarks_edit_tags)
    val remove = resources.getString(R.string.bookmark_remove)
    return combinedClickable(onLongClickLabel = edit, onLongClick = onEdit, onClick = onOpen).semantics {
        onClick(label = openLabel) { onOpen().let { true } }
        customActions =
            listOf(
                CustomAccessibilityAction(edit) { onEdit().let { true } },
                CustomAccessibilityAction(remove) { onRemove().let { true } },
            )
    }
}

@Composable
private fun CardHeader(
    item: BookmarkItem,
    onRemove: () -> Unit,
    remove: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = item.photo,
            name = item.chatTitle,
            id = item.chatId,
            size = MaterialTheme.sizes.avatarSmall,
            shape = item.avatarShape,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.chatTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmallEmphasized.userContent,
            )
            Text(
                text = item.date,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ic_delete), contentDescription = remove)
        }
    }
}

/** The bookmark's tags as small pills with a dot of their hue. */
@Composable
private fun TagPills(item: BookmarkItem) {
    val hues = MaterialTheme.pingogramColors.senderHues
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        item.tags.forEach { tag ->
            Row(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(MaterialTheme.sizes.badgeIcon).background(hues[tag.color].ink, CircleShape))
                Text(
                    text = tag.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.userContent,
                )
            }
        }
    }
}

private const val TEXT_LINES = 3
private const val NOTE_LINES = 3
