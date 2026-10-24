package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import dev.kyralabs.pingogram.core.navigation.AutoDeleteRoute
import dev.kyralabs.pingogram.core.navigation.ChangePhoneNumberRoute
import dev.kyralabs.pingogram.core.navigation.DeleteAccountRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.LoginEmailRoute
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileRoute as EditProfileScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.account.DeleteAccountRoute as DeleteAccountScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.account.autodelete.AutoDeleteRoute as AutoDeleteScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.account.email.LoginEmailRoute as LoginEmailScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberRoute as ChangePhoneNumberScreenRoute

/**
 * Edit profile, which leaves with [onProfileSaved] once it saved, and Change number above it; the account's pages above
 * Privacy and security: Login email, Auto-delete messages and Delete account.
 */
internal fun NavGraphBuilder.accountPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
) {
    settingsPage<EditProfileRoute> {
        EditProfileScreenRoute(
            onBack = onBack,
            onSaved = onProfileSaved,
            onChangeNumber = { onOpen(ChangePhoneNumberRoute) },
        )
    }
    settingsPage<ChangePhoneNumberRoute> { ChangePhoneNumberScreenRoute(onBack = onBack) }
    settingsPage<LoginEmailRoute> { LoginEmailScreenRoute(onBack = onBack) }
    settingsPage<AutoDeleteRoute> { AutoDeleteScreenRoute(onBack = onBack) }
    settingsPage<DeleteAccountRoute> { DeleteAccountScreenRoute(onBack = onBack) }
}
