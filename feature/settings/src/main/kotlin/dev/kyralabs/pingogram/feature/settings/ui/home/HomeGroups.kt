package dev.kyralabs.pingogram.feature.settings.ui.home

import android.text.format.Formatter
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.CategoryIconBadge
import dev.kyralabs.pingogram.core.ui.component.LocalBookmarksLauncher
import dev.kyralabs.pingogram.core.ui.component.LocalRecentCallsLauncher
import dev.kyralabs.pingogram.core.ui.theme.CategoryColors
import dev.kyralabs.pingogram.core.ui.theme.CategoryHue
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageSelection
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import java.util.Locale

/**
 * Settings home's groups ([HomeGroups], `docs/DESIGN_SYSTEM.md` §13.9): each under its
 * `titleSmallEmphasized` `primary` header, segmented rows ([HomeEntryRow]) with their icons in circles of the group's
 * own category color, and its footer where it has one.
 * Recent calls and Bookmarks are left out where the app does not provide their pages, and a group without a row is
 * not shown.
 */
@Composable
internal fun SettingsHomeGroups(
    summaries: SettingsHomeSummaries,
    onOpen: (HomeDestination) -> Unit,
    selection: SettingsPageSelection?,
    groups: List<HomeGroup> = HomeGroups,
) {
    val hasRecentCalls = LocalRecentCallsLauncher.current != null
    val hasBookmarks = LocalBookmarksLauncher.current != null
    val rowState = HomeRowState(summaries, selection)
    groups.forEach { group ->
        val entries =
            group.entries.filter {
                (hasRecentCalls || it.destination != HomeDestination.RecentCalls) &&
                    (hasBookmarks || it.destination != HomeDestination.Bookmarks)
            }
        if (entries.isEmpty()) return@forEach
        SettingsGroup(
            title = stringResource(group.title),
            footer = group.footer?.let { stringResource(it) },
            category = group.category,
        ) {
            entries.forEach { entry ->
                item(key = entry.title) { shapes ->
                    HomeEntryRow(entry, group.category, shapes, rowState, onClick = { onOpen(entry.destination) })
                }
            }
        }
    }
}

/**
 * An entry's row: Material's segmented list item (two lines, at least 72 dp), its icon in a circle of [category]'s
 * color (`primary` on the selected row), its title and summary, and a value at its end in `labelLarge` `primary` where
 * the entry has one (the storage used, the language).
 */
@Composable
internal fun HomeEntryRow(
    entry: HomeEntry,
    category: CategoryHue,
    shapes: ListItemShapes,
    state: HomeRowState,
    onClick: () -> Unit,
) {
    val summaries = state.summaries
    val selection = state.selection
    val value = entryValue(entry, summaries)
    val summary = entrySummary(entry, summaries)
    val page = (entry.destination as? HomeDestination.Page)?.page
    val selected = selection?.let { page != null && it.openPage == page }
    val leading = @Composable { EntryBadge(entry, category, selected == true) }
    val trailing: (@Composable () -> Unit)? =
        value?.let {
            {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    if (selected != null) {
        SettingsListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = { Text(summary) },
            colors = settingsRowColors(),
        ) { Text(stringResource(entry.title)) }
    } else {
        SettingsListItem(
            onClick = onClick,
            shapes = shapes,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = { Text(summary) },
            colors = settingsRowColors(),
        ) { Text(stringResource(entry.title)) }
    }
}

/** The entry's icon in a circle of [category]'s color, or `primary` / `onPrimary` on the [selected] row. */
@Composable
private fun EntryBadge(
    entry: HomeEntry,
    category: CategoryHue,
    selected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    CategoryIconBadge(
        icon = painterResource(entry.icon),
        colors =
            if (selected) {
                CategoryColors(colors.primary, colors.onPrimary)
            } else {
                MaterialTheme.pingogramColors.categories[category]
            },
    )
}

/** Devices' summary counts the active sessions once known, Reminders' the reminders still to come. */
@Composable
private fun entrySummary(
    entry: HomeEntry,
    summaries: SettingsHomeSummaries,
): String {
    val sessions = summaries.sessionCount
    val reminders = summaries.upcomingReminders
    val resources = LocalResources.current
    return when {
        entry.destination == HomeDestination.Page(SettingsPage.ActiveSessions) && sessions != null -> {
            resources.getQuantityString(R.plurals.settings_home_devices_count, sessions, sessions.toString())
        }

        entry.destination == HomeDestination.Page(SettingsPage.Reminders) && reminders != null && reminders > 0 -> {
            resources.getQuantityString(R.plurals.settings_reminders_upcoming, reminders, reminders.toString())
        }

        else -> {
            stringResource(entry.summary)
        }
    }
}

/** The value at a row's end: the storage Data and storage's files take, and the app's language. */
@Composable
private fun entryValue(
    entry: HomeEntry,
    summaries: SettingsHomeSummaries,
): String? {
    val page = (entry.destination as? HomeDestination.Page)?.page
    return when (page) {
        SettingsPage.DataStorage -> {
            summaries.storageBytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
        }

        SettingsPage.Language -> {
            val tag = summaries.languageTag
            if (tag == null) {
                stringResource(R.string.settings_language_system)
            } else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            }
        }

        else -> {
            summaries.bookmarkCount
                ?.takeIf { it > 0 && entry.destination == HomeDestination.Bookmarks }
                ?.toString()
        }
    }
}
