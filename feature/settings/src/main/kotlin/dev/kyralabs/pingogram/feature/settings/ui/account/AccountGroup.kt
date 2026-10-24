package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.account.autodelete.autoDeleteText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Privacy and security's "Account" group, each row opening its page with [onOpen]: "Login email" with Telegram's
 * pattern of it ([loginEmailPattern], only where the account has one, as Telegram lets change it only then),
 * "Auto-delete messages" with the timer of new chats ([autoDeleteSeconds]), and "Delete account" with how long the
 * account may stay unused before Telegram deletes it ([accountTtlDays]).
 */
@Composable
internal fun AccountGroup(
    loginEmailPattern: String?,
    autoDeleteSeconds: Int,
    accountTtlDays: Int,
    onOpen: (PrivacySecurityPage) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_account)) {
        if (loginEmailPattern != null) {
            item(key = PrivacySecurityPage.LoginEmail) { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_login_email),
                    shapes = shapes,
                    icon = UiR.drawable.ic_alternate_email,
                    supportingContent = { Text(loginEmailPattern, style = LocalTextStyle.current.numericContent) },
                    onClick = { onOpen(PrivacySecurityPage.LoginEmail) },
                )
            }
        }
        item(key = PrivacySecurityPage.AutoDelete) { shapes ->
            PageItem(
                title = stringResource(R.string.settings_auto_delete),
                summary = autoDeleteText(autoDeleteSeconds),
                icon = UiR.drawable.ic_timer,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.AutoDelete) },
            )
        }
        item(key = PrivacySecurityPage.DeleteAccount) { shapes ->
            PageItem(
                title = stringResource(R.string.settings_delete_account),
                summary = stringResource(R.string.settings_delete_account_summary, ttlText(accountTtlDays)),
                icon = UiR.drawable.ic_delete,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.DeleteAccount) },
            )
        }
    }
}
