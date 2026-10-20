package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.AutoDownloadRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.LanguageRoute
import dev.kyralabs.pingogram.core.navigation.NotificationExceptionsRoute
import dev.kyralabs.pingogram.core.navigation.NotificationSettingsRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLibraryRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLicensesRoute
import dev.kyralabs.pingogram.core.navigation.PasscodeLockRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.SetPasscodeRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationFlowRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationRoute
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationResult
import dev.kyralabs.pingogram.feature.settings.ui.AboutRoute as AboutScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ActiveSessionsRoute as ActiveSessionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AppearanceRoute as AppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AutoDownloadRoute as AutoDownloadScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.BlockedUsersRoute as BlockedUsersScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageRoute as DataStorageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileRoute as EditProfileScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.LanguageRoute as LanguageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.NotificationExceptionsRoute as NotificationExceptionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsRoute as NotificationSettingsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLibraryRoute as OpenSourceLibraryScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLicensesRoute as OpenSourceLicensesScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityRoute as PrivacySecurityScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySettingRoute as PrivacySettingScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRoute as SettingsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeLockRoute as PasscodeLockScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.passcode.SetPasscodeRoute as SetPasscodeScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlowRoute as TwoStepVerificationFlowScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationRoute as TwoStepVerificationScreenRoute

/**
 * Adds the Settings tab, [SettingsRoute], to the graph. Its rows call [onOpen] with the route of the page they open,
 * which the app pushes above the tab: [EditProfileRoute], [AppearanceRoute], [NotificationSettingsRoute],
 * [DataStorageRoute], [LanguageRoute], [AboutRoute] or [PrivacySecurityRoute]. Once Edit profile saved the profile
 * ([popBackStackAfterProfileSaved]), the tab says so.
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
 * Adds the settings pages pushed above the Settings tab: [EditProfileRoute], [AppearanceRoute],
 * [NotificationSettingsRoute], [DataStorageRoute], [LanguageRoute], [AboutRoute] and [PrivacySecurityRoute], and the
 * ones these push above themselves, which they open with [onOpen]: [NotificationExceptionsRoute] above Notifications,
 * [AutoDownloadRoute] above Data and storage,
 * [PrivacySettingRoute], [BlockedUsersRoute], [ActiveSessionsRoute], [TwoStepVerificationRoute] and
 * [PasscodeLockRoute] above Privacy and security, [TwoStepVerificationFlowRoute] above 2-step verification,
 * [SetPasscodeRoute] above Passcode lock, [OpenSourceLicensesRoute] above About and [OpenSourceLibraryRoute] above it.
 * [onBack] leaves each of them; Edit profile leaves with [onProfileSaved] once it saved, which the app implements with
 * [popBackStackAfterProfileSaved], and a flow of 2-step verification with [onTwoStepVerificationDone] once it is done,
 * which the app implements with [popBackStackAfterTwoStepVerification].
 */
fun NavGraphBuilder.settingsPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
    onTwoStepVerificationDone: (TwoStepVerificationResult) -> Unit,
) {
    composable<EditProfileRoute> { EditProfileScreenRoute(onBack = onBack, onSaved = onProfileSaved) }
    composable<AppearanceRoute> { AppearanceScreenRoute(onBack = onBack) }
    composable<NotificationSettingsRoute> {
        NotificationSettingsScreenRoute(onOpen = { onOpen(it.route) }, onBack = onBack)
    }
    composable<NotificationExceptionsRoute> { entry ->
        val scope =
            NotificationScope.entries.firstOrNull {
                it.name ==
                    entry.toRoute<NotificationExceptionsRoute>().scope
            }
        if (scope != null) {
            NotificationExceptionsScreenRoute(scope = scope, onBack = onBack)
        } else {
            // A kind of chats this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    composable<DataStorageRoute> {
        DataStorageScreenRoute(onOpenAutoDownload = { onOpen(AutoDownloadRoute(it.name)) }, onBack = onBack)
    }
    composable<AutoDownloadRoute> { entry ->
        val network = AutoDownloadNetwork.entries.firstOrNull { it.name == entry.toRoute<AutoDownloadRoute>().network }
        if (network != null) {
            AutoDownloadScreenRoute(network = network, onBack = onBack)
        } else {
            // A network this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    composable<LanguageRoute> { LanguageScreenRoute(onBack = onBack) }
    composable<AboutRoute> { AboutScreenRoute(onOpenLicenses = { onOpen(OpenSourceLicensesRoute) }, onBack = onBack) }
    composable<OpenSourceLicensesRoute> {
        OpenSourceLicensesScreenRoute(onOpenLibrary = { onOpen(OpenSourceLibraryRoute(it)) }, onBack = onBack)
    }
    composable<OpenSourceLibraryRoute> { entry ->
        OpenSourceLibraryScreenRoute(libraryId = entry.toRoute<OpenSourceLibraryRoute>().libraryId, onBack = onBack)
    }
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
    composable<PasscodeLockRoute> {
        PasscodeLockScreenRoute(onSetPasscode = { onOpen(SetPasscodeRoute) }, onBack = onBack)
    }
    composable<SetPasscodeRoute> { SetPasscodeScreenRoute(onSaved = onBack, onBack = onBack) }
    twoStepVerificationPages(onOpen, onBack, onTwoStepVerificationDone)
}

/** [TwoStepVerificationRoute] and the flows it pushes above itself, [TwoStepVerificationFlowRoute]. */
private fun NavGraphBuilder.twoStepVerificationPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onDone: (TwoStepVerificationResult) -> Unit,
) {
    composable<TwoStepVerificationRoute> { entry ->
        val results = entry.savedStateHandle
        val result by results.getStateFlow<String?>(TWO_STEP_VERIFICATION_RESULT, null).collectAsStateWithLifecycle()
        TwoStepVerificationScreenRoute(
            result = TwoStepVerificationResult.entries.firstOrNull { it.name == result },
            onResultShown = { results[TWO_STEP_VERIFICATION_RESULT] = null },
            onOpen = { onOpen(TwoStepVerificationFlowRoute(it.name)) },
            onBack = onBack,
        )
    }
    composable<TwoStepVerificationFlowRoute> { entry ->
        val name = entry.toRoute<TwoStepVerificationFlowRoute>().flow
        val flow = TwoStepVerificationFlow.entries.firstOrNull { it.name == name }
        if (flow != null) {
            TwoStepVerificationFlowScreenRoute(flow = flow, onFinished = onDone, onBack = onBack)
        } else {
            // A flow this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
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

/**
 * Leaves a flow of 2-step verification once it is done, back to the 2-step verification page below it, which then says
 * what it did ([result]) in a snackbar: a result in the page's saved state, as Navigation returns results.
 */
fun NavController.popBackStackAfterTwoStepVerification(result: TwoStepVerificationResult) {
    previousBackStackEntry?.savedStateHandle?.set(TWO_STEP_VERIFICATION_RESULT, result.name)
    popBackStack()
}

/** The result a flow of 2-step verification leaves for its page: the name of its [TwoStepVerificationResult]. */
private const val TWO_STEP_VERIFICATION_RESULT = "settings.twoStepVerificationResult"

private val SettingsPage.route: Any
    get() =
        when (this) {
            SettingsPage.EditProfile -> EditProfileRoute
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.Notifications -> NotificationSettingsRoute
            SettingsPage.DataStorage -> DataStorageRoute
            SettingsPage.Language -> LanguageRoute
            SettingsPage.About -> AboutRoute
            SettingsPage.PrivacySecurity -> PrivacySecurityRoute
        }

private val PrivacySecurityPage.route: Any
    get() =
        when (this) {
            is PrivacySecurityPage.Setting -> PrivacySettingRoute(setting.name)
            PrivacySecurityPage.BlockedUsers -> BlockedUsersRoute
            PrivacySecurityPage.ActiveSessions -> ActiveSessionsRoute
            PrivacySecurityPage.TwoStepVerification -> TwoStepVerificationRoute
            PrivacySecurityPage.PasscodeLock -> PasscodeLockRoute
        }

private val NotificationSettingsPage.route: Any
    get() =
        when (this) {
            is NotificationSettingsPage.Exceptions -> NotificationExceptionsRoute(scope.name)
        }
