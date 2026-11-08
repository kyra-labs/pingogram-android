package dev.kyralabs.pingogram.feature.settings.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.LocalRecentCallsLauncher
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageSelection
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import java.util.Locale

/**
 * Settings home's groups ([HomeGroups], `docs/DESIGN_SYSTEM.md` §13.9): each under its
 * `titleSmallEmphasized` `primary` header, segmented rows ([HomeEntryRow]) with their badges' shapes taking
 * `Cookie9Sided`, `Flower`, `Clover4Leaf` and `Sunny` in turn across the page. Recent calls is left out where the app
 * does not provide its page.
 */
@Composable
internal fun SettingsHomeGroups(
    summaries: SettingsHomeSummaries,
    onOpen: (HomeDestination) -> Unit,
    selection: SettingsPageSelection?,
) {
    val hasRecentCalls = LocalRecentCallsLauncher.current != null
    val rowState = HomeRowState(summaries, selection)
    var index = 0
    HomeGroups.forEach { group ->
        val entries = group.entries.filter { hasRecentCalls || it.destination != HomeDestination.RecentCalls }
        val first = index
        index += entries.size
        SettingsGroup(title = stringResource(group.title)) {
            entries.forEachIndexed { position, entry ->
                item(key = entry.title) { shapes ->
                    HomeEntryRow(
                        entry,
                        first + position,
                        shapes,
                        rowState,
                        onClick = { onOpen(entry.destination) },
                    )
                }
            }
        }
    }
}

/**
 * An entry's row: Material's segmented list item (two lines, at least 72 dp), its icon on a badge of
 * `leadingIconContainer` in the shape of its place ([index]) filled with a vivid avatar color, its title and summary,
 * and a value at its end in `labelLarge` `primary` where the entry has one (the storage used, the language).
 */
@Composable
internal fun HomeEntryRow(
    entry: HomeEntry,
    index: Int,
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
    val leading = @Composable { EntryBadge(entry, index) }
    val trailing: (@Composable () -> Unit)? =
        value?.let {
            {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    if (selected != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = { Text(summary) },
            colors = settingsRowColors(),
        ) { Text(stringResource(entry.title)) }
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = { Text(summary) },
            colors = settingsRowColors(),
        ) { Text(stringResource(entry.title)) }
    }
}

/** The entry's icon on its badge: a vivid fill and its text color, in the shape of its place. */
@Composable
private fun EntryBadge(
    entry: HomeEntry,
    index: Int,
) {
    val palette = MaterialTheme.pingogramColors.vividAvatars
    val colors = palette[index % palette.size]
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(colors.container, badgeShape(index)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(entry.icon), contentDescription = null, tint = colors.content)
    }
}

/** The badge shapes in turn: `Cookie9Sided`, `Flower`, `Clover4Leaf`, `Sunny`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun badgeShape(index: Int): Shape =
    when (index % BADGE_SHAPES) {
        0 -> MaterialShapes.Cookie9Sided.toShape()
        1 -> MaterialShapes.Flower.toShape()
        2 -> MaterialShapes.Clover4Leaf.toShape()
        else -> MaterialShapes.Sunny.toShape()
    }

/** Devices' summary counts the active sessions once known. */
@Composable
private fun entrySummary(
    entry: HomeEntry,
    summaries: SettingsHomeSummaries,
): String {
    val count = summaries.sessionCount
    return if (entry.destination == HomeDestination.Page(SettingsPage.ActiveSessions) && count != null) {
        LocalResources.current.getQuantityString(R.plurals.settings_home_devices_count, count, count.toString())
    } else {
        stringResource(entry.summary)
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
            null
        }
    }
}

private const val BADGE_SHAPES = 4
