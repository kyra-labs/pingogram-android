package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.AboutRoute as AboutScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ActiveSessionsRoute as ActiveSessionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AppearanceRoute as AppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.BlockedUsersRoute as BlockedUsersScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageRoute as DataStorageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityRoute as PrivacySecurityScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySettingRoute as PrivacySettingScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRoute as SettingsScreenRoute

/**
 * Adds the Settings tab, [SettingsRoute], to the graph. Its rows call [onOpen] with the route of the page they open,
 * which the app pushes above the tab: [AppearanceRoute], [DataStorageRoute], [AboutRoute] or [PrivacySecurityRoute].
 */
fun NavGraphBuilder.settingsScreen(onOpen: (route: Any) -> Unit) {
    composable<SettingsRoute> { SettingsScreenRoute(onOpenPage = { onOpen(it.route) }) }
}

/**
 * Adds the settings pages pushed above the Settings tab: [AppearanceRoute], [DataStorageRoute], [AboutRoute] and
 * [PrivacySecurityRoute], and the ones Privacy and security pushes above itself, which it opens with [onOpen]:
 * [PrivacySettingRoute], [BlockedUsersRoute] and [ActiveSessionsRoute]. [onBack] leaves each of them.
 */
fun NavGraphBuilder.settingsPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    composable<AppearanceRoute> { AppearanceScreenRoute(onBack = onBack) }
    composable<DataStorageRoute> { DataStorageScreenRoute(onBack = onBack) }
    composable<AboutRoute> { AboutScreenRoute(onBack = onBack) }
    composable<PrivacySecurityRoute> {
        PrivacySecurityScreenRoute(onOpen = { onOpen(it.route) }, onBack = onBack)
    }
    composable<PrivacySettingRoute> { entry ->
        val setting = PrivacySetting.entries.firstOrNull { it.name == entry.toRoute<PrivacySettingRoute>().setting }
        if (setting != null) {
            PrivacySettingScreenRoute(setting = setting, onBack = onBack)
        } else {
            // A setting this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    composable<BlockedUsersRoute> { BlockedUsersScreenRoute(onBack = onBack) }
    composable<ActiveSessionsRoute> { ActiveSessionsScreenRoute(onBack = onBack) }
}

private val SettingsPage.route: Any
    get() =
        when (this) {
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.DataStorage -> DataStorageRoute
            SettingsPage.About -> AboutRoute
            SettingsPage.PrivacySecurity -> PrivacySecurityRoute
        }

private val PrivacySecurityPage.route: Any
    get() =
        when (this) {
            is PrivacySecurityPage.Setting -> PrivacySettingRoute(setting.name)
            PrivacySecurityPage.BlockedUsers -> BlockedUsersRoute
            PrivacySecurityPage.ActiveSessions -> ActiveSessionsRoute
        }
