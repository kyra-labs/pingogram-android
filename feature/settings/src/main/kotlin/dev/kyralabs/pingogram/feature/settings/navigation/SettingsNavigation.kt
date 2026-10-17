package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsScreen

/** Adds the Settings tab, [SettingsRoute], to the graph. */
fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRoute> { SettingsScreen() }
}
