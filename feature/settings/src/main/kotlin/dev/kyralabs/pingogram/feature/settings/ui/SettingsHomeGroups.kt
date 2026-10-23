package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The groups of Settings home that open the other settings pages, without headings (docs/DESIGN_SYSTEM.md §6).

/**
 * The groups of rows that open the other settings pages: General (Appearance, Notifications, Data and storage,
 * Language), Privacy and security, About, their icons in circles of one container role per group: `primary`,
 * `tertiary`, `secondary` (`docs/DESIGN_SYSTEM.md` §2.2). With a [selection] they are selectable, the open page's
 * selected.
 */
@Composable
internal fun PageGroups(
    onOpenPage: (SettingsPage) -> Unit,
    selection: SettingsPageSelection? = null,
) {
    GeneralGroup(onOpenPage, selection)
    SettingsGroup(iconTone = SettingsIconTone.Tertiary) {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_privacy_security),
                summary = stringResource(R.string.settings_privacy_security_summary),
                icon = R.drawable.ic_shield,
                shapes = shapes,
                selected = selection?.let { it.openPage == SettingsPage.PrivacySecurity },
                onClick = { onOpenPage(SettingsPage.PrivacySecurity) },
            )
        }
    }
    SettingsGroup(iconTone = SettingsIconTone.Secondary) {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                icon = R.drawable.ic_info,
                shapes = shapes,
                selected = selection?.let { it.openPage == SettingsPage.About },
                onClick = { onOpenPage(SettingsPage.About) },
            )
        }
    }
}

/** General: Appearance, Chat folders, Stickers, Notifications, Data and storage, Language. */
@Composable
private fun GeneralGroup(
    onOpenPage: (SettingsPage) -> Unit,
    selection: SettingsPageSelection?,
) {
    SettingsGroup {
        GeneralPages.forEach { page ->
            item(key = page.page) { shapes ->
                PageItem(
                    title = stringResource(page.title),
                    summary = stringResource(page.summary),
                    icon = page.icon,
                    shapes = shapes,
                    selected = selection?.let { it.openPage == page.page },
                    onClick = { onOpenPage(page.page) },
                )
            }
        }
    }
}

/** A row of the General group: the [page] it opens, its [title], [summary] and [icon]. */
private class GeneralPage(
    val page: SettingsPage,
    val title: Int,
    val summary: Int,
    val icon: Int,
)

private val GeneralPages =
    listOf(
        GeneralPage(
            SettingsPage.Appearance,
            R.string.settings_appearance,
            R.string.settings_appearance_summary,
            R.drawable.ic_palette,
        ),
        GeneralPage(
            SettingsPage.ChatFolders,
            R.string.settings_chat_folders,
            R.string.settings_chat_folders_summary,
            R.drawable.ic_folder,
        ),
        GeneralPage(
            SettingsPage.Stickers,
            R.string.settings_stickers,
            R.string.settings_stickers_summary,
            UiR.drawable.ic_mood,
        ),
        GeneralPage(
            SettingsPage.Notifications,
            R.string.settings_notifications,
            R.string.settings_notifications_summary,
            UiR.drawable.ic_notifications,
        ),
        GeneralPage(
            SettingsPage.DataStorage,
            R.string.settings_data_storage,
            R.string.settings_data_storage_summary,
            R.drawable.ic_storage,
        ),
        GeneralPage(
            SettingsPage.Language,
            R.string.settings_language,
            R.string.settings_language_summary,
            R.drawable.ic_language,
        ),
    )
