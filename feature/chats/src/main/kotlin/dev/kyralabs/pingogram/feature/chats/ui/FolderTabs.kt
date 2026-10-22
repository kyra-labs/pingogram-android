package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.ImmutableList

/**
 * The folder tabs above the chat list (`REQUIREMENTS.md` FR-L3, `docs/DESIGN_SYSTEM.md` §6): "All chats" and the
 * user's folders, each with the number of its unread unmuted chats. Up to [MAX_CONNECTED_TABS] tabs are a connected
 * [ButtonGroup] of [ToggleButton]s that scrolls sideways when it does not fit; more are a [PrimaryScrollableTabRow].
 *
 * @param selectedFolderId the folder shown; null for "All chats".
 * @param onSelect called with the folder's id, or null for "All chats".
 * @param enabled false disables every tab, e.g. in selection mode.
 * @param margin the window margin before the first tab and after the last (`docs/DESIGN_SYSTEM.md` §7).
 */
@Composable
internal fun FolderTabs(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    margin: Dp = MaterialTheme.spacing.windowMarginCompact,
) {
    if (tabs.size > MAX_CONNECTED_TABS) {
        FolderTabRow(tabs, selectedFolderId, onSelect, enabled, margin, modifier)
    } else {
        FolderButtonGroup(tabs, selectedFolderId, onSelect, enabled, margin, modifier)
    }
}

/**
 * The tabs as a connected [ButtonGroup] with Material 3 Expressive's press physics: a pressed button grows and
 * squeezes its neighbours (`animateWidth`, on the motion scheme's fast spatial spring), and the connected shapes morph
 * on press and to a pill when checked ([connectedShapes]). The selected folder's label is `labelLargeEmphasized`, the
 * others `labelLarge`.
 *
 * In a horizontal scroll the group is measured with an unbounded width, so every button fits: its overflow menu,
 * which would hide folders and their unread counts, never shows (material3 1.5.0-beta01 overflows only past the
 * maximum width). A press shares the width out anew without changing its sum, so the scroll stays put. Each tab still
 * has a menu entry, should the group overflow after all. With animations off (reduced motion) the press and the
 * shapes change at once.
 *
 * The selected tab is always in view ([rememberTabScroll]), and the row fades out at an edge it can scroll past
 * ([fadingEdges]), so a tab is never cut by the window's edge without a hint that more follow.
 */
@Composable
private fun FolderButtonGroup(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    enabled: Boolean,
    margin: Dp,
    modifier: Modifier = Modifier,
) {
    // The bar is surfaceContainer, the default unchecked container: the next step of the ladder keeps the tabs apart.
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val labels = tabs.map { it.label() }
    val descriptions = tabs.map { tabDescription(it) }
    val scroll =
        rememberTabScroll(tabs.size, selectedIndex = tabs.indexOfFirst { it.folderId == selectedFolderId }, margin)
    ButtonGroup(
        // Never measured: the group always fits in its scroll.
        overflowIndicator = {},
        modifier =
            modifier
                .fillMaxWidth()
                .fadingEdges(scroll.state)
                .horizontalScroll(scroll.state)
                .selectableGroup()
                // A 48 dp row: the buttons' touch targets, 4 dp above and below their 40 dp containers.
                .padding(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = tab.folderId == selectedFolderId
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    FolderButton(
                        tab = tab,
                        label = labels[index],
                        description = descriptions[index],
                        selected = selected,
                        onSelect = onSelect,
                        enabled = enabled,
                        shapes = connectedShapes(index, tabs.lastIndex),
                        colors = colors,
                        interactionSource = interactionSource,
                        modifier =
                            Modifier
                                .animateWidth(interactionSource)
                                .onPlaced { scroll.buttonBounds[index] = it.boundsInParent() },
                    )
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(descriptions[index]) },
                        onClick = {
                            onSelect(tab.folderId)
                            menu.dismiss()
                        },
                        enabled = enabled,
                    )
                },
            )
        }
    }
}

/** One tab of [FolderButtonGroup]: its label, and its unread count in a badge. */
@Suppress("LongParameterList") // The tab, its texts and state, and the group's shapes, colors and interactions.
@Composable
private fun FolderButton(
    tab: FolderTab,
    label: String,
    description: String,
    selected: Boolean,
    onSelect: (Int?) -> Unit,
    enabled: Boolean,
    shapes: ToggleButtonShapes,
    colors: ToggleButtonColors,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onSelect(tab.folderId) },
        // Tabs rather than check boxes: one is always selected (Material's single-select button group sample).
        modifier =
            modifier.semantics {
                role = Role.Tab
                contentDescription = description
            },
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        val typography = MaterialTheme.typography
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            maxLines = 1,
            style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
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
    enabled: Boolean,
    margin: Dp,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.folderId == selectedFolderId }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        edgePadding = margin,
    ) {
        tabs.forEachIndexed { index, tab ->
            val description = tabDescription(tab)
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(tab.folderId) },
                modifier = Modifier.semantics { contentDescription = description },
                enabled = enabled,
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
