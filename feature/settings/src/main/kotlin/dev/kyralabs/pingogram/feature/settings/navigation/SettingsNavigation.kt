package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
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
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileRoute as EditProfileScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityRoute as PrivacySecurityScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySettingRoute as PrivacySettingScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRoute as SettingsScreenRoute

/**
 * Adds the Settings tab, [SettingsRoute], to the graph. Its rows call [onOpen] with the route of the page they open,
 * which the app pushes above the tab: [EditProfileRoute], [AppearanceRoute], [DataStorageRoute], [AboutRoute] or
 * [PrivacySecurityRoute]. Once Edit profile saved the profile ([popBackStackAfterProfileSaved]), the tab says so.
 */
fun NavGraphBuilder.settingsScreen(onOpen: (route: Any) -> Unit) {
    composable<SettingsRoute> { entry ->
        val results = entry.savedStateHandle
        val profileSaved by results.getStateFlow(PROFILE_SAVED_RESULT, false).collectAsStateWithLifecycle()
        SettingsScreenRoute(
            onOpenPage = { onOpen(it.route) },
            profileSaved = profileSaved,
            onProfileSavedShown = { results[PROFILE_SAVED_RESULT] = false },
        )
    }
}

/**
 * Adds the settings pages pushed above the Settings tab: [EditProfileRoute], [AppearanceRoute], [DataStorageRoute],
 * [AboutRoute] and [PrivacySecurityRoute], and the ones Privacy and security pushes above itself, which it opens with
 * [onOpen]: [PrivacySettingRoute], [BlockedUsersRoute] and [ActiveSessionsRoute]. [onBack] leaves each of them; Edit
 * profile leaves with [onProfileSaved] once it saved, which the app implements with [popBackStackAfterProfileSaved].
 */
fun NavGraphBuilder.settingsPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
) {
    composable<EditProfileRoute> { EditProfileScreenRoute(onBack = onBack, onSaved = onProfileSaved) }
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

/**
 * Leaves Edit profile after it saved the profile, back to the Settings tab below it, which then says so in a
 * snackbar: a result in the tab's saved state, as Navigation returns results.
 */
fun NavController.popBackStackAfterProfileSaved() {
    previousBackStackEntry?.savedStateHandle?.set(PROFILE_SAVED_RESULT, true)
    popBackStack()
}

/** The result Edit profile leaves for the Settings tab: true once it saved the profile. */
private const val PROFILE_SAVED_RESULT = "settings.profileSaved"

private val SettingsPage.route: Any
    get() =
        when (this) {
            SettingsPage.EditProfile -> EditProfileRoute
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
