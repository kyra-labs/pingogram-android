package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.AccountNotificationsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * "Notifications from all accounts" (`docs/adr/0031-multiple-accounts.md`), with more than one account: off, only the
 * shown account's notifications show, as Telegram's apps offer; calls ring either way.
 */
@Composable
internal fun AccountsGroup(
    fromAllAccounts: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_notifications_section_accounts)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_notifications_all_accounts),
                summary = stringResource(R.string.settings_notifications_all_accounts_summary),
                checked = fromAllAccounts,
                shapes = shapes,
                onCheckedChange = onChange,
            )
        }
    }
}

/**
 * What the Notifications page needs of the device's accounts (`docs/adr/0031-multiple-accounts.md`): the shown
 * account's slot, whose channels it opens, and "Notifications from all accounts" while several are signed in.
 */
class NotificationAccounts
    @Inject
    constructor(
        private val deviceAccounts: DeviceAccountsRepository,
        private val settings: AccountNotificationsRepository,
    ) {
        /** The shown account's slot and the switch (null with one account), then every change; none while hidden. */
        val state: Flow<Pair<Int, Boolean?>> =
            if (!deviceAccounts.isAvailable) {
                emptyFlow()
            } else {
                combine(deviceAccounts.accounts, settings.fromAllAccounts) { accounts, all ->
                    (accounts.firstOrNull { it.isActive }?.slot ?: 0) to all.takeIf { accounts.size > 1 }
                }
            }

        suspend fun setFromAllAccounts(enabled: Boolean): Outcome<Unit> = settings.setFromAllAccounts(enabled)
    }

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenAccountsPreview() {
    PingogramPreview {
        NotificationSettingsScreen(
            state = SettingsPreviewData.notificationsWithAccounts,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}
