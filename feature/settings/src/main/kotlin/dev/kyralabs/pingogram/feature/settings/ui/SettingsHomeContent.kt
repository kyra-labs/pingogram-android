package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.component.LocalIsDemo
import dev.kyralabs.pingogram.feature.settings.ui.home.AccountSwitcher
import dev.kyralabs.pingogram.feature.settings.ui.home.ExperimentalAccountCard
import dev.kyralabs.pingogram.feature.settings.ui.home.ExperimentalHomeGroups
import dev.kyralabs.pingogram.feature.settings.ui.home.HomeDestination
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeSummaries

// What Settings home shows in each look (SettingsScreen, docs/DESIGN_SYSTEM.md §6 and §13.9).

/** The standard look's home: the account card, the other accounts, the page groups, the switch row, "Log out". */
@Suppress("LongParameterList") // The screen's state and callbacks, passed on.
@Composable
internal fun StandardHome(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    selection: SettingsPageSelection?,
    experimentalUi: Boolean?,
    onExperimentalUiChange: (Boolean) -> Unit,
) {
    val isDemo = LocalIsDemo.current
    state.account?.let {
        AccountCard(
            account = it,
            onEditProfile = { onOpenPage(SettingsPage.EditProfile) },
            selected = selection?.let { selection -> selection.openPage == SettingsPage.EditProfile },
        )
    }
    // The demo's graph is no account: no switching or adding from it (docs/adr/0034).
    if (!isDemo) {
        OtherAccountsGroup(
            accounts = state.otherAccounts,
            canAdd = state.canAddAccount,
            onSwitch = { onEvent(SettingsEvent.SwitchAccount(it)) },
            onAdd = { onEvent(SettingsEvent.AddAccount) },
        )
    }
    PageGroups(onOpenPage, selection)
    if (experimentalUi != null) ExperimentalUiGroup(experimentalUi, onExperimentalUiChange)
    LogOutGroup(state, onEvent)
}

/**
 * The experimental look's home (`docs/DESIGN_SYSTEM.md` §13.9): the account card with its own account menu
 * ([ExperimentalAccountCard]), the labs card, the groups of [HomeGroups], "Log out".
 */
@Suppress("LongParameterList") // The screen's state and callbacks, passed on.
@Composable
internal fun ExperimentalHome(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpen: (HomeDestination) -> Unit,
    selection: SettingsPageSelection?,
    experimentalUi: Boolean?,
    onExperimentalUiChange: (Boolean) -> Unit,
    summaries: SettingsHomeSummaries,
) {
    val isDemo = LocalIsDemo.current
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
        ExperimentalAccountCard(
            account = it,
            onEditProfile = { onOpen(HomeDestination.Page(SettingsPage.EditProfile)) },
            onQrCode = { onOpen(HomeDestination.Page(SettingsPage.MyQrCode)) },
            switcher = switcher,
        )
    }
    if (experimentalUi != null) ExperimentalUiCard(checked = experimentalUi, onCheckedChange = onExperimentalUiChange)
    ExperimentalHomeGroups(summaries, onOpen, selection)
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
