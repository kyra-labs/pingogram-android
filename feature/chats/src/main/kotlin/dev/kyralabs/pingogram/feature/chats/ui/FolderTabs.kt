package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.repository.FolderTabStyle
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.folderIconDrawable
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.ImmutableList

/**
 * The folder tabs above the chat list (`REQUIREMENTS.md` FR-L3, `docs/DESIGN_SYSTEM.md` §6): "All chats" and the
 * user's folders, each with the number of its unread unmuted chats: [ToggleButton] chips apart in a [ButtonGroup]
 * that scrolls sideways, whatever their number (`docs/DESIGN_SYSTEM.md` §13.3).
 *
 * A long press on a tab (or a right click) opens its menu below it ([FolderTabMenuAnchor]): "Edit folder" ("Edit
 * folders" on "All chats") where [onEditFolder] is given, and "Mark all as read" ([onMarkAllAsRead]) while any of
 * its chats is unread; screen readers get the same as the tab's custom actions. A tab with neither has no menu.
 * The selected chip is a `primary` pill with its count in an inverted badge, the others `small`-cornered on
 * `surfaceContainerHighest` with a `primary` badge ([folderButtonColors], [chipShapes]). With [onEditFolder], "Edit
 * folders" ([EditFoldersButton]) ends the row, opening the folder settings (null), unless Appearance → Chat list
 * turned the button off ([ChatSwipes.editFoldersButton]); the tabs' menus keep it.
 *
 * @param selectedFolderId the folder shown; null for "All chats".
 * @param onSelect called with the folder's id, or null for "All chats".
 * @param enabled false disables every tab and its menu, e.g. in selection mode.
 * @param margin the window margin before the first tab and after the last (`docs/DESIGN_SYSTEM.md` §7).
 * @param onMarkAllAsRead called with the folder's id, or null for "All chats".
 * @param onEditFolder called with the folder's id, or null for "All chats"; null leaves "Edit folder" out.
 */
@Suppress("LongParameterList") // The tabs and their selection, their menu's two actions, and the layout's margin.
@Composable
internal fun FolderTabs(
    tabs: ImmutableList<FolderTab>,
    selectedFolderId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    margin: Dp = MaterialTheme.spacing.windowMarginCompact,
    onMarkAllAsRead: (Int?) -> Unit = {},
    onEditFolder: ((Int?) -> Unit)? = null,
) {
    val menu = remember { FolderTabMenuState() }
    val canEditFolders = onEditFolder != null
    val onAction: (FolderTab, FolderTabAction) -> Unit = { tab, action ->
        when (action) {
            FolderTabAction.EditFolder -> onEditFolder?.invoke(tab.folderId)
            FolderTabAction.MarkAllAsRead -> onMarkAllAsRead(tab.folderId)
        }
    }
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    // One instance while nothing it holds changes, so the tabs skip when the top bar composes again.
    val tabMenu =
        remember(menu, canEditFolders, enabled, haptics, resources, onAction) {
            TabMenu(menu, canEditFolders, enabled, haptics, resources, onAction)
        }
    // spacing.medium below the row, between it and the list's sheet (owner decision 2026-11-06).
    Row(
        modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).folderTabMenuContainer(menu)) {
            FolderButtonGroup(tabs, selectedFolderId, onSelect, margin, tabMenu)
            if (enabled) FolderTabMenuAnchor(menu, tabs, canEditFolders, onAction)
        }
        if (onEditFolder != null && LocalChatSwipes.current.editFoldersButton) {
            // spacing.small from the tabs' fade; at the end in line with the header's buttons: the window margin
            // less the touch target's room around the button.
            EditFoldersButton(
                onClick = { onEditFolder(null) },
                enabled = enabled,
                modifier =
                    Modifier.padding(
                        start = MaterialTheme.spacing.small,
                        end = margin - MaterialTheme.spacing.extraSmall,
                    ),
            )
        }
    }
}

/**
 * The tabs as a [ButtonGroup] of chips with Material 3 Expressive's press physics: a pressed button grows and
 * squeezes its neighbours (`animateWidth`, on the motion scheme's fast spatial spring), and the chips' shapes morph
 * on press and to a pill when checked ([chipShapes]). The selected folder's label is `labelLargeEmphasized`, the
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
    margin: Dp,
    tabMenu: TabMenu,
) {
    val enabled = tabMenu.enabled
    val colors = folderButtonColors()
    val labels = tabs.map { it.label() }
    val descriptions = tabs.map { tabDescription(it) }
    val scroll =
        rememberTabScroll(tabs.size, selectedIndex = tabs.indexOfFirst { it.folderId == selectedFolderId }, margin)
    ButtonGroup(
        // Never measured: the group always fits in its scroll.
        overflowIndicator = {},
        modifier =
            Modifier
                .fillMaxWidth()
                .fadingEdges(scroll.state)
                .horizontalScroll(scroll.state)
                .selectableGroup()
                // A 48 dp row: the buttons' touch targets, 4 dp above and below their 40 dp containers.
                .padding(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
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
                        shapes = chipShapes(),
                        colors = colors,
                        interactionSource = interactionSource,
                        modifier =
                            Modifier
                                .animateWidth(interactionSource)
                                .onPlaced { scroll.buttonBounds[index] = it.boundsInParent() }
                                .tabMenu(tabMenu, tab, index),
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

/** One tab of [FolderButtonGroup]: its [FolderToggleButton], one tab for screen readers. */
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
    // Tabs rather than check boxes, one always selected (Material's single-select button group sample): screen readers
    // hear a selected tab, without the toggle button's checked state, which TalkBack would read as "Tab, checked".
    Box(
        modifier =
            modifier.clearAndSetSemantics {
                role = Role.Tab
                contentDescription = description
                this.selected = selected
                if (enabled) {
                    onClick {
                        onSelect(tab.folderId)
                        true
                    }
                } else {
                    disabled()
                }
            },
        propagateMinConstraints = true,
    ) {
        FolderToggleButton(tab, label, selected, onSelect, enabled, shapes, colors, interactionSource)
    }
}

/**
 * The button of a [FolderButton], as Appearance → Chat list → "Folder tabs" asks ([ChatSwipes.folderTabStyle]): its
 * label, its icon before its label, or its icon alone, and its unread count in a badge. With icons alone the selected
 * tab still shows its name, as a tab's long press opens its menu rather than a tooltip, and the others carry their
 * count on the icon ([BadgedBox]); screen readers hear every tab's name ([FolderButton]).
 */
@Suppress("LongParameterList") // The tab, its label and state, and the group's shapes, colors and interactions.
@Composable
private fun FolderToggleButton(
    tab: FolderTab,
    label: String,
    selected: Boolean,
    onSelect: (Int?) -> Unit,
    enabled: Boolean,
    shapes: ToggleButtonShapes,
    colors: ToggleButtonColors,
    interactionSource: MutableInteractionSource,
) {
    val style = LocalChatSwipes.current.folderTabStyle
    ToggleButton(
        checked = selected,
        onCheckedChange = { onSelect(tab.folderId) },
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        val showsLabel = style != FolderTabStyle.Icon || selected
        val badge = tabBadgeColors(selected, colors)
        if (style != FolderTabStyle.Text) {
            if (showsLabel || tab.unreadCount == 0) {
                TabIcon(tab)
            } else {
                BadgedBox(badge = { TabBadge(tab.unreadCount, badge.first, badge.second) }) { TabIcon(tab) }
            }
        }
        if (showsLabel) {
            if (style != FolderTabStyle.Text) {
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ToggleButtonDefaults.MinHeight)))
            }
            val typography = MaterialTheme.typography
            Text(
                text = label,
                modifier = Modifier.clearAndSetSemantics {},
                maxLines = 1,
                style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
            )
            if (tab.unreadCount > 0) {
                TabBadge(
                    count = tab.unreadCount,
                    containerColor = badge.first,
                    contentColor = badge.second,
                    modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                )
            }
        }
    }
}

/** The folder's symbol (`core:ui`'s `folderIconDrawable`; "All chats" takes Telegram's "All"), decorative. */
@Composable
private fun TabIcon(tab: FolderTab) {
    val name =
        when (tab) {
            is FolderTab.AllChats -> ALL_CHATS_ICON
            is FolderTab.Folder -> tab.iconName
        }
    Icon(
        painter = painterResource(folderIconDrawable(name)),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ToggleButtonDefaults.MinHeight)),
    )
}

/** The badge's container and content colors: on the selected button's container the badge inverts them. */
@Composable
@ReadOnlyComposable
private fun tabBadgeColors(
    selected: Boolean,
    colors: ToggleButtonColors,
): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return if (selected) {
        colors.checkedContentColor to colors.checkedContainerColor
    } else {
        scheme.primary to scheme.onPrimary
    }
}

/** TDLib's icon name of the main list, the "All chats" tab's symbol. */
private const val ALL_CHATS_ICON = "All"

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

@PingogramPreviews
@Composable
private fun FolderTabsPreview() {
    PingogramPreview {
        FolderTabs(tabs = ChatListPreviewData.folders, selectedFolderId = 1, onSelect = {})
    }
}
