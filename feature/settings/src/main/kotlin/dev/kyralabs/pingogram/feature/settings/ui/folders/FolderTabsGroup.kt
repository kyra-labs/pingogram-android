package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.FolderTabStyle
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.nestedItemColors
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import kotlinx.collections.immutable.toImmutableList

/**
 * The "Folder tabs" group of the Chat folders page (owner decision 2026-11-19, moved from Appearance with the keys
 * they had): what each tab shows ("Folder tabs", owner decision 2026-11-08), whether the tabs end in "Edit folders"
 * ("Edit folders button", owner decision 2026-11-07), whether the Feed tab starts them ("Show Feed tab",
 * `docs/DESIGN_SYSTEM.md` §6 "Channel feed"), and whether they show their unread counts ("Unread counters",
 * `REQUIREMENTS.md` FR-X10).
 */
internal fun LazyListScope.folderTabs(
    tabs: ChatListSettings,
    style: SettingsListRowStyle,
    onEvent: (ChatFoldersEvent) -> Unit,
) {
    item(key = HEADER_TABS, contentType = HEADER_TABS) {
        SettingsSectionHeader(
            stringResource(R.string.settings_chat_folders_section_tabs),
            // A group's distance from the one above it, less the gap every item keeps.
            Modifier
                .settingsListRow(this, style)
                .padding(top = MaterialTheme.spacing.extraLarge - ListItemDefaults.SegmentedGap),
        )
    }
    item(key = TAB_STYLE, contentType = TAB_STYLE) {
        FolderTabStyleItem(
            selected = tabs.folderTabStyle,
            shapes = ListItemDefaults.segmentedShapes(0, TAB_ROWS),
            modifier = Modifier.settingsListRow(this, style),
            onSelect = { onEvent(ChatFoldersEvent.SetFolderTabStyle(it)) },
        )
    }
    item(key = EDIT_BUTTON, contentType = TAB_SWITCH) {
        SwitchItem(
            title = stringResource(R.string.settings_edit_folders_button),
            summary = stringResource(R.string.settings_edit_folders_button_summary),
            checked = tabs.showEditFoldersButton,
            shapes = ListItemDefaults.segmentedShapes(1, TAB_ROWS),
            modifier = Modifier.settingsListRow(this, style),
            onCheckedChange = { onEvent(ChatFoldersEvent.SetShowEditFoldersButton(it)) },
        )
    }
    item(key = FEED_TAB, contentType = TAB_SWITCH) {
        SwitchItem(
            title = stringResource(R.string.settings_show_feed_tab),
            summary = stringResource(R.string.settings_show_feed_tab_summary),
            checked = tabs.showFeedTab,
            shapes = ListItemDefaults.segmentedShapes(2, TAB_ROWS),
            modifier = Modifier.settingsListRow(this, style),
            onCheckedChange = { onEvent(ChatFoldersEvent.SetShowFeedTab(it)) },
        )
    }
    item(key = TAB_COUNTERS, contentType = TAB_SWITCH) {
        SwitchItem(
            title = stringResource(R.string.settings_folder_tab_counters),
            summary = stringResource(R.string.settings_folder_tab_counters_summary),
            checked = tabs.showFolderTabCounters,
            shapes = ListItemDefaults.segmentedShapes(TAB_ROWS - 1, TAB_ROWS),
            modifier = Modifier.settingsListRow(this, style),
            onCheckedChange = { onEvent(ChatFoldersEvent.SetShowFolderTabCounters(it)) },
        )
    }
}

/**
 * "Folder tabs": what each folder tab above the chat list shows, its name, its icon and name, or its icon alone, as
 * [ChoiceRow]'s connected buttons in a segment of their own.
 */
@Composable
private fun FolderTabStyleItem(
    selected: FolderTabStyle,
    shapes: ListItemShapes,
    modifier: Modifier,
    onSelect: (FolderTabStyle) -> Unit,
) {
    SettingsSegment(shapes, modifier) {
        SettingsListItem(
            shapes = ListItemDefaults.shapes(),
            supportingContent = { Text(stringResource(R.string.settings_folder_tabs_summary)) },
            colors = nestedItemColors(),
        ) {
            Text(stringResource(R.string.settings_folder_tabs))
        }
        ChoiceRow(
            options = FolderTabStyles,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
            contentPadding =
                PaddingValues(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
        )
    }
}

private val FolderTabStyles = FolderTabStyle.entries.toImmutableList()

private fun FolderTabStyle.labelRes(): Int =
    when (this) {
        FolderTabStyle.Text -> R.string.settings_folder_tabs_text
        FolderTabStyle.IconAndText -> R.string.settings_folder_tabs_icon_and_text
        FolderTabStyle.Icon -> R.string.settings_folder_tabs_icon
    }

private const val HEADER_TABS = "headerTabs"
private const val TAB_STYLE = "tabStyle"
private const val TAB_SWITCH = "tabSwitch"
private const val EDIT_BUTTON = "editFoldersButton"
private const val FEED_TAB = "feedTab"
private const val TAB_COUNTERS = "folderTabCounters"
private const val TAB_ROWS = 4
