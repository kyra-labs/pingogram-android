package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.AboutRoute as AboutScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AppearanceRoute as AppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageRoute as DataStorageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRoute as SettingsScreenRoute

/**
 * Adds the Settings tab, [SettingsRoute], to the graph. Its rows call [onOpen] with the route of the page they open,
 * which the app pushes above the tab: [AppearanceRoute], [DataStorageRoute] or [AboutRoute].
 */
fun NavGraphBuilder.settingsScreen(onOpen: (route: Any) -> Unit) {
    composable<SettingsRoute> { SettingsScreenRoute(onOpenPage = { onOpen(it.route) }) }
}

/**
 * Adds the settings pages pushed above the Settings tab: [AppearanceRoute], [DataStorageRoute] and [AboutRoute];
 * [onBack] leaves them.
 */
fun NavGraphBuilder.settingsPages(onBack: () -> Unit) {
    composable<AppearanceRoute> { AppearanceScreenRoute(onBack = onBack) }
    composable<DataStorageRoute> { DataStorageScreenRoute(onBack = onBack) }
    composable<AboutRoute> { AboutScreenRoute(onBack = onBack) }
}

private val SettingsPage.route: Any
    get() =
        when (this) {
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.DataStorage -> DataStorageRoute
            SettingsPage.About -> AboutRoute
        }
