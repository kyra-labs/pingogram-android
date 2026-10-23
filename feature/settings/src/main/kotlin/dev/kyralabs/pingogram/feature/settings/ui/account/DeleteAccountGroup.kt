package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Privacy and security's "Account" group: "Delete account", with how long the account may stay unused before Telegram
 * deletes it ([accountTtlDays]), opening its page with [onClick].
 */
@Composable
internal fun DeleteAccountGroup(
    accountTtlDays: Int,
    onClick: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_account)) {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_delete_account),
                summary = stringResource(R.string.settings_delete_account_summary, ttlText(accountTtlDays)),
                icon = UiR.drawable.ic_delete,
                shapes = shapes,
                onClick = onClick,
            )
        }
    }
}
