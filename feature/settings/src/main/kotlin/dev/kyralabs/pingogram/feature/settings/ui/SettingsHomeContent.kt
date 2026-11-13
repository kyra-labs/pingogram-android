package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.component.LocalIsDemo
import dev.kyralabs.pingogram.feature.settings.ui.home.AccountCard
import dev.kyralabs.pingogram.feature.settings.ui.home.AccountSwitcher
import dev.kyralabs.pingogram.feature.settings.ui.home.FrozenAccountBanner
import dev.kyralabs.pingogram.feature.settings.ui.home.HomeDestination
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeGroups
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeSummaries

// What Settings home shows (SettingsScreen, docs/DESIGN_SYSTEM.md §13.9).

/**
 * Settings home (`docs/DESIGN_SYSTEM.md` §13.9): while Telegram keeps the account frozen, its notice
 * ([FrozenAccountBanner]); the account card with its own account menu ([AccountCard]), the groups of
 * [SettingsHomeGroups], "Log out".
 */
@Suppress("LongParameterList") // The screen's state and callbacks, passed on.
@Composable
internal fun SettingsHome(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpen: (HomeDestination) -> Unit,
    selection: SettingsPageSelection?,
    summaries: SettingsHomeSummaries,
) {
    val isDemo = LocalIsDemo.current
    summaries.freeze?.let { FrozenAccountBanner(it) }
    state.account?.let {
        val switcher =
            if (!isDemo && (state.otherAccounts.isNotEmpty() || state.canAddAccount)) {
                AccountSwitcher(
                    accounts = state.otherAccounts,
                    canAdd = state.canAddAccount,
                    onSwitch = { slot -> onEvent(SettingsEvent.SwitchAccount(slot)) },
                    onAdd = { onEvent(SettingsEvent.AddAccount) },
                )
            } else {
                null
            }
        AccountCard(
            account = it,
            onEditProfile = { onOpen(HomeDestination.Page(SettingsPage.EditProfile)) },
            onQrCode = { onOpen(HomeDestination.Page(SettingsPage.MyQrCode)) },
            switcher = switcher,
        )
    }
    SettingsHomeGroups(summaries, onOpen, selection)
    LogOutGroup(state, onEvent)
}

/** "Log out" ("Leave demo" in the demo) on its own. */
@Composable
private fun LogOutGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val isDemo = LocalIsDemo.current
    SettingsGroup {
        item { shapes ->
            LogOutItem(
                isLoggingOut = state.isLoggingOut,
                shapes = shapes,
                onClick = { onEvent(SettingsEvent.LogOut) },
                isDemo = isDemo,
            )
        }
    }
}
