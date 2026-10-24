package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AutoDeleteRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.ConnectedWebsitesRoute
import dev.kyralabs.pingogram.core.navigation.DeleteAccountRoute
import dev.kyralabs.pingogram.core.navigation.LoginEmailRoute
import dev.kyralabs.pingogram.core.navigation.NewChatPrivacyRoute
import dev.kyralabs.pingogram.core.navigation.PasscodeLockRoute
import dev.kyralabs.pingogram.core.navigation.PrivacyExceptionsRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityPage
import dev.kyralabs.pingogram.feature.settings.ui.BlockedUsersRoute as BlockedUsersScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySecurityRoute as PrivacySecurityScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.PrivacySettingRoute as PrivacySettingScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.PrivacyExceptionsRoute as PrivacyExceptionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.privacy.messages.NewChatPrivacyRoute as NewChatPrivacyScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.websites.ConnectedWebsitesRoute as ConnectedWebsitesScreenRoute

/**
 * Privacy and security and the privacy pages above it: each setting, its exceptions, who can message the user, and
 * blocked users. The pages of the account, sessions, the passcode and 2-step verification it opens are elsewhere.
 */
internal fun NavGraphBuilder.privacyPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<PrivacySecurityRoute> {
        PrivacySecurityScreenRoute(onOpen = { onOpen(it.route) }, onBack = onBack)
    }
    settingsPage<NewChatPrivacyRoute> { NewChatPrivacyScreenRoute(onBack = onBack) }
    settingsPage<PrivacyExceptionsRoute> { entry ->
        val route = entry.toRoute<PrivacyExceptionsRoute>()
        val setting = PrivacySetting.entries.firstOrNull { it.name == route.setting } ?: PrivacySetting.LastSeen
        val side = if (route.always) PrivacyExceptionSide.Always else PrivacyExceptionSide.Never
        PrivacyExceptionsScreenRoute(setting = setting, side = side, onBack = onBack)
    }
    settingsPage<PrivacySettingRoute> { entry ->
        val setting = PrivacySetting.entries.firstOrNull { it.name == entry.toRoute<PrivacySettingRoute>().setting }
        if (setting != null) {
            PrivacySettingScreenRoute(
                setting = setting,
                onBack = onBack,
                onOpenExceptions = { side ->
                    onOpen(PrivacyExceptionsRoute(setting.name, always = side == PrivacyExceptionSide.Always))
                },
            )
        } else {
            // A setting this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    settingsPage<BlockedUsersRoute> { BlockedUsersScreenRoute(onBack = onBack) }
    settingsPage<ConnectedWebsitesRoute> { ConnectedWebsitesScreenRoute(onBack = onBack) }
}

private val PrivacySecurityPage.route: Any
    get() =
        when (this) {
            is PrivacySecurityPage.Setting -> PrivacySettingRoute(setting.name)
            PrivacySecurityPage.BlockedUsers -> BlockedUsersRoute
            PrivacySecurityPage.ActiveSessions -> ActiveSessionsRoute
            PrivacySecurityPage.Websites -> ConnectedWebsitesRoute
            PrivacySecurityPage.TwoStepVerification -> TwoStepVerificationRoute
            PrivacySecurityPage.PasscodeLock -> PasscodeLockRoute
            PrivacySecurityPage.DeleteAccount -> DeleteAccountRoute
            PrivacySecurityPage.LoginEmail -> LoginEmailRoute
            PrivacySecurityPage.AutoDelete -> AutoDeleteRoute
            PrivacySecurityPage.NewChats -> NewChatPrivacyRoute
        }
