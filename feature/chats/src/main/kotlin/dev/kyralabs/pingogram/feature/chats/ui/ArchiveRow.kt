package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The "Archived chats" row at the top of the main list (`REQUIREMENTS.md` FR-L3), which opens the archive: an archive
 * on a neutral avatar placeholder, the names of the first archived chats TDLib has loaded or else how many chats the
 * archive has, and its unread chats in a badge that is always in the muted style, as the archive mostly holds chats
 * the user put out of the way. Its title is emphasized while it has unread chats, as a chat's is
 * (`docs/DESIGN_SYSTEM.md` §3.3). It cannot be selected; in selection mode it is disabled ([enabled]).
 *
 * - **Compact** ([ArchiveEntry.isCompact]): Material's one-line list item, the archive symbol where the avatar was (so
 *   its title lines up with the chats'), the title and the badge.
 * - **Its style** ([onStyle]; none: the row only opens the archive): a long press (with the list item's `LongPress`
 *   haptic) opens an expressive menu ([ArchiveRowMenu]) to make it compact or full again, or to hide it; a swipe either
 *   way hides it ([ArchiveRowSwipe]); screen readers get the same as custom actions. While its menu is open it takes
 *   the list item's `large` selected shape in `secondaryContainer`, as a contact's row does.
 *
 * For screen readers it is one node: "Archived chats, Ada, Grace and Alan, 2 unread chats".
 */
@Composable
internal fun ArchiveRow(
    entry: ArchiveEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onStyle: ((ArchiveRowStyle) -> Unit)? = null,
) {
    val title = stringResource(R.string.chat_list_archived_chats)
    val supporting = archiveSummary(entry)
    val unread =
        if (entry.unreadCount > 0) {
            val count = entry.unreadCount
            LocalResources.current.getQuantityString(R.plurals.chat_list_a11y_unread_chats, count, count)
        } else {
            null
        }
    val description = spokenList(LocalResources.current, listOf(title, supporting, unread))
    val insets = rowInsets()
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    val styles = if (enabled && onStyle != null) onStyle else null
    val restingContainer = RestingContainer(ListItemDefaults.colors().containerColor, ListItemDefaults.shapes())
    ArchiveRowSwipe(onHide = styles?.let { { it(ArchiveRowStyle.Hidden) } }, modifier = modifier) { dragged ->
        Box {
            ListItem(
                onClick = onClick,
                // Inset like the chats' rows ([ChatListItem]).
                modifier =
                    Modifier
                        .padding(insets)
                        .then(if (dragged) restingContainer.modifier else Modifier)
                        .semantics {
                            contentDescription = description
                            if (styles != null) customActions = styleActions(entry, resources, styles)
                        },
                enabled = enabled,
                onLongClick = if (styles != null) ({ menuOpen = true }) else null,
                onLongClickLabel = if (styles != null) stringResource(R.string.chat_list_archive_row_menu) else null,
                shapes = listItemShapes(rounded = menuOpen || dragged),
                colors = listItemColors(selected = menuOpen),
                contentPadding = insetContentPadding(insets),
                leadingContent = { ArchiveLeading(entry.isCompact, title, selected = menuOpen) },
                trailingContent = { if (entry.unreadCount > 0) ArchiveUnreadBadge(entry.unreadCount) },
                supportingContent = if (entry.isCompact) null else ({ ArchiveSummaryText(supporting) }),
            ) { ArchiveTitle(title, unread = entry.unreadCount > 0) }
            if (styles != null) {
                ArchiveRowMenu(
                    expanded = menuOpen,
                    isCompact = entry.isCompact,
                    onDismiss = { menuOpen = false },
                    onStyle = {
                        menuOpen = false
                        styles(it)
                    },
                )
            }
        }
    }
}

/**
 * What a row being swiped draws behind itself from its first frame: its resting container, in the list item's selected
 * shape (`large`), covering what the swipe reveals; at rest its own container is transparent, as the chats' rows'
 * ([ChatListItem]).
 */
private class RestingContainer(
    color: Color,
    shapes: ListItemShapes,
) {
    val modifier: Modifier =
        Modifier.drawBehind { drawOutline(shapes.selectedShape.createOutline(size, layoutDirection, this), color) }
}

/** The full row's archive on the neutral avatar placeholder, or the compact row's archive symbol. */
@Composable
private fun ArchiveLeading(
    isCompact: Boolean,
    title: String,
    selected: Boolean,
) {
    if (isCompact) {
        CompactArchiveIcon()
    } else {
        Avatar(
            photo = null,
            name = title,
            id = ARCHIVE_AVATAR_ID,
            size = MaterialTheme.sizes.avatarRow,
            placeholderIcon = UiR.drawable.ic_archive,
            onSelection = selected,
        )
    }
}

/** The archive's unread chats, always in the muted style; the row's description says them. */
@Composable
private fun ArchiveUnreadBadge(count: Int) {
    Badge(
        modifier = Modifier.clearAndSetSemantics {},
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Text(unreadCountText(count)) }
}

/** The full row's second line: the names of the first archived chats, or how many there are. */
@Composable
private fun ArchiveSummaryText(text: String) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics {},
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium.userContent,
    )
}

/** "Archived chats", emphasized while the archive has [unread] chats, as a chat's title is. */
@Composable
private fun ArchiveTitle(
    title: String,
    unread: Boolean,
) {
    Text(
        text = title,
        modifier = Modifier.clearAndSetSemantics {},
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = with(MaterialTheme.typography) { if (unread) titleMediumEmphasized else titleMedium },
    )
}

/**
 * The compact row's leading symbol: the archive in `onSurfaceVariant`, centred in the width of a chat's avatar, so the
 * row stays one line high and its title starts where the chats' titles do.
 */
@Composable
private fun CompactArchiveIcon() {
    Box(Modifier.width(MaterialTheme.sizes.avatarRow), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(UiR.drawable.ic_archive),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An empty list's state, below the archive row while there is one: it stays in reach when every chat is archived. */
@Composable
internal fun ChatListEmptyWithArchive(
    archive: ArchiveEntry?,
    kind: ChatListKind,
    isFolder: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        archive?.let {
            ArchiveRow(
                entry = it,
                enabled = true,
                onClick = { onEvent(ChatListEvent.OpenArchive) },
                onStyle = { style -> onEvent(ChatListEvent.SetArchiveRowStyle(style)) },
            )
        }
        ChatListEmpty(
            kind = kind,
            isFolder = isFolder,
            onNewChat = { onEvent(ChatListEvent.NewChat) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Rocket Club, Old Team, and Newsletter" in the language's list style, or "14 chats" until any are loaded. */
@Composable
private fun archiveSummary(entry: ArchiveEntry): String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(entry.titles, entry.chatCount, resources, locale) {
        if (entry.titles.isEmpty()) {
            resources.getQuantityString(R.plurals.chat_list_archive_chat_count, entry.chatCount, entry.chatCount)
        } else {
            ListFormatter.getInstance(locale).format(entry.titles)
        }
    }
}

/**
 * The avatar placeholder's id: it picks the neutral `surfaceContainerHighest` color pair, the fourth of the avatar
 * colors (`docs/DESIGN_SYSTEM.md` §2.2), so the archive does not look like one of the chats.
 */
private const val ARCHIVE_AVATAR_ID = 3L

@PingogramPreviews
@Composable
private fun ArchiveRowPreview() {
    PingogramPreview {
        Column {
            ArchiveRow(entry = ChatListPreviewData.archive, enabled = true, onClick = {}, onStyle = {})
            ArchiveRow(entry = ChatListPreviewData.archive.copy(isCompact = true), enabled = true, onClick = {})
        }
    }
}
