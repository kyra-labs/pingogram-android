package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The groups of Settings home that open the other settings pages, without headings (docs/DESIGN_SYSTEM.md §6).

/**
 * The groups of rows that open the other settings pages: General (Appearance, Notifications, Data and storage,
 * Language), Privacy and security, About, their icons in circles of one container role per group: `primary`,
 * `tertiary`, `secondary` (`docs/DESIGN_SYSTEM.md` §2.2).
 */
@Composable
internal fun PageGroups(onOpenPage: (SettingsPage) -> Unit) {
    GeneralGroup(onOpenPage)
    SettingsGroup(iconTone = SettingsIconTone.Tertiary) {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_privacy_security),
                summary = stringResource(R.string.settings_privacy_security_summary),
                icon = R.drawable.ic_shield,
                shapes = shapes,
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
                onClick = { onOpenPage(SettingsPage.About) },
            )
        }
    }
}

/** General: Appearance, Notifications, Data and storage, Language. */
@Composable
private fun GeneralGroup(onOpenPage: (SettingsPage) -> Unit) {
    SettingsGroup {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                icon = R.drawable.ic_palette,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.Appearance) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_notifications),
                summary = stringResource(R.string.settings_notifications_summary),
                icon = UiR.drawable.ic_notifications,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.Notifications) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_data_storage),
                summary = stringResource(R.string.settings_data_storage_summary),
                icon = R.drawable.ic_storage,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.DataStorage) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_language),
                summary = stringResource(R.string.settings_language_summary),
                icon = R.drawable.ic_language,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.Language) },
            )
        }
    }
}
