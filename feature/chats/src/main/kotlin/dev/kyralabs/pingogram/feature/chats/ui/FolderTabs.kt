package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.ImmutableList

/**
 * The folder tabs above the chat list (`REQUIREMENTS.md` FR-L3, `docs/DESIGN_SYSTEM.md` §6): "All chats" and the
 * user's folders, each with the number of its unread unmuted chats. Up to [MAX_CONNECTED_TABS] tabs are a connected
 * button group of [ToggleButton]s that scrolls sideways when it does not fit; more are a [PrimaryScrollableTabRow].
 *
 * A row of [ToggleButton]s with the connected shapes and spacing of [ButtonGroupDefaults], as Material builds connected
 * button groups, rather than the `ButtonGroup` layout: that one moves the buttons that do not fit into an overflow
 * menu, which would hide folders and their unread counts, and it cannot scroll.
 *
 * @param selectedFolderId the folder shown; null for "All chats".
 * @param onSelect called with the folder's id, or null for "All chats".
 */
@Composable
internal fun FolderTabs(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size > MAX_CONNECTED_TABS) {
        FolderTabRow(tabs, selectedFolderId, onSelect, modifier)
    } else {
        FolderButtonGroup(tabs, selectedFolderId, onSelect, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderButtonGroup(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ToggleButtonDefaults.colors()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(
                    horizontal = MaterialTheme.spacing.windowMarginCompact,
                    vertical = MaterialTheme.spacing.small,
                ),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = tab.folderId == selectedFolderId
            val description = tabDescription(tab)
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(tab.folderId) },
                // Tabs rather than check boxes: one is always selected (Material's single-select button group sample).
                modifier =
                    Modifier.semantics {
                        role = Role.Tab
                        contentDescription = description
                    },
                shapes = connectedShapes(index, tabs.lastIndex),
                colors = colors,
            ) {
                Text(
                    text = tab.label(),
                    modifier = Modifier.clearAndSetSemantics {},
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
                if (tab.unreadCount > 0) {
                    // On the selected button's container the badge inverts its colors to stay visible.
                    val scheme = MaterialTheme.colorScheme
                    TabBadge(
                        count = tab.unreadCount,
                        containerColor = if (selected) colors.checkedContentColor else scheme.primary,
                        contentColor = if (selected) colors.checkedContainerColor else scheme.onPrimary,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(
    index: Int,
    lastIndex: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

/** For more tabs than a button group holds well: Material's scrollable tabs, with the same labels and badges. */
@Composable
private fun FolderTabRow(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.folderId == selectedFolderId }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        edgePadding = MaterialTheme.spacing.windowMarginCompact,
    ) {
        tabs.forEachIndexed { index, tab ->
            val description = tabDescription(tab)
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(tab.folderId) },
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Row(
                    modifier =
                        Modifier
                            .clearAndSetSemantics {}
                            .padding(vertical = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = tab.label(), maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    if (tab.unreadCount > 0) {
                        TabBadge(
                            count = tab.unreadCount,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Badge(modifier = modifier, containerColor = containerColor, contentColor = contentColor) {
        Text(stringResource(R.string.chat_list_unread_count, count))
    }
}

private val FolderTab.folderId: Int?
    get() = (this as? FolderTab.Folder)?.id

@Composable
@ReadOnlyComposable
private fun FolderTab.label(): String =
    when (this) {
        is FolderTab.AllChats -> stringResource(R.string.chat_list_all_chats)
        is FolderTab.Folder -> title
    }

/** "Work, 2 unread chats" for screen readers. */
@Composable
@ReadOnlyComposable
private fun tabDescription(tab: FolderTab): String {
    val label = tab.label()
    if (tab.unreadCount == 0) return label
    val count = tab.unreadCount
    val unread = LocalResources.current.getQuantityString(R.plurals.chat_list_a11y_unread_chats, count, count)
    return stringResource(R.string.chat_list_a11y_tab, label, unread)
}

/** More tabs than this are a tab row: a connected button group gets crowded with more (`docs/DESIGN_SYSTEM.md` §6). */
private const val MAX_CONNECTED_TABS = 6

@PingogramPreviews
@Composable
private fun FolderTabsPreview() {
    PingogramPreview {
        FolderTabs(tabs = ChatListPreviewData.folders, selectedFolderId = 1, onSelect = {})
    }
}
