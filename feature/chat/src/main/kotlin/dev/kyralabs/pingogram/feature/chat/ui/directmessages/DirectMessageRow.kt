package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One person's conversation with the channel (the owner's board 7b): Material's two-line list item inset in the sheet
 * like the chat list's rows, the sender's 48 dp avatar, their name (`titleMediumEmphasized` while unread) with a star
 * for a sender who pays per message, one line of the last message, and its time over the unread count. A long press
 * (or a screen reader's custom action) opens a menu that marks it read or unread ([onMarkUnread]), the row in the list
 * item's selected colors and shape meanwhile. Screen readers hear it as one: name, paid, message, time, unread.
 */
@Suppress("LongMethod") // A row's content and its menu, in one list item.
@Composable
internal fun DirectMessageRowItem(
    row: DirectRow,
    onClick: () -> Unit,
    onMarkUnread: (unread: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val insets = sheetRowInsets()
    val resources = LocalResources.current
    val name = row.name.resolve()
    val preview = row.preview?.resolve()
    val markLabel = stringResource(if (row.isUnread) R.string.direct_mark_read else R.string.direct_mark_unread)
    Box(modifier) {
        val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        val shapes = ListItemDefaults.shapes()
        ListItem(
            onClick = onClick,
            onLongClick = { menuOpen = true },
            onLongClickLabel = stringResource(R.string.direct_menu),
            modifier =
                Modifier.padding(insets).semantics(mergeDescendants = true) {
                    contentDescription = rowDescription(resources, row, name, preview)
                    customActions =
                        listOf(
                            CustomAccessibilityAction(markLabel) {
                                onMarkUnread(!row.isUnread)
                                true
                            },
                        )
                },
            colors =
                if (menuOpen) {
                    colors.copy(
                        containerColor = colors.selectedContainerColor,
                        contentColor = colors.selectedContentColor,
                        supportingContentColor = colors.selectedSupportingContentColor,
                        trailingContentColor = colors.selectedTrailingContentColor,
                    )
                } else {
                    colors
                },
            shapes = if (menuOpen) shapes.copy(shape = shapes.selectedShape) else shapes,
            contentPadding = insetContentPadding(insets),
            leadingContent = {
                Avatar(
                    photo = row.photo,
                    name = name,
                    id = row.avatarId,
                    size = MaterialTheme.sizes.avatarRow,
                    shape = row.avatarShape,
                )
            },
            supportingContent =
                preview?.let {
                    {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.userContent,
                        )
                    }
                },
            trailingContent = { RowMeta(row) },
        ) {
            RowName(row, name)
        }
        DropdownMenuPopup(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onMarkUnread(!row.isUnread)
                    },
                    text = { Text(markLabel) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                    leadingIcon = {
                        Icon(
                            painterResource(
                                if (row.isUnread) UiR.drawable.ic_mark_chat_read else UiR.drawable.ic_mark_chat_unread,
                            ),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

/** The sender's name, emphasized while unread, then a star in `tertiary` where they pay per message. */
@Composable
private fun RowName(
    row: DirectRow,
    name: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = nameStyle(row.isUnread).userContent,
        )
        if (row.isPaid) {
            Icon(
                painter = painterResource(UiR.drawable.ic_star_filled),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** The name's style: emphasized while [unread]. */
@Composable
private fun nameStyle(unread: Boolean) =
    if (unread) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium

/** The time over the unread count (a dot where only marked as unread), in `primary`. */
@Composable
private fun RowMeta(row: DirectRow) {
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        row.time?.let {
            Text(
                text = it,
                style =
                    MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TABULAR_FIGURES)
                        .userContent,
                color = with(MaterialTheme.colorScheme) { if (row.isUnread) primary else onSurfaceVariant },
            )
        }
        when {
            row.unreadCount > 0 -> {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(row.unreadCount.toString()) }
            }

            row.isUnread -> {
                Badge(containerColor = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** What screen readers hear of [row]: the name, paid, the last message, its time and the unread messages. */
private fun rowDescription(
    resources: Resources,
    row: DirectRow,
    name: String,
    preview: String?,
): String =
    spokenList(
        resources,
        listOf(
            name,
            resources.getString(R.string.direct_paid).takeIf { row.isPaid },
            preview,
            row.time,
            when {
                row.unreadCount > 0 -> {
                    resources.getQuantityString(
                        R.plurals.direct_unread,
                        row.unreadCount,
                        row.unreadCount,
                    )
                }

                row.isUnread -> {
                    resources.getString(R.string.direct_marked_unread)
                }

                else -> {
                    null
                }
            },
        ),
    )

private const val TABULAR_FIGURES = "tnum"
