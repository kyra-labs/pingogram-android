package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import dev.kyralabs.pingogram.core.navigation.ChangePhoneNumberRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileRoute as EditProfileScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberRoute as ChangePhoneNumberScreenRoute

/** Edit profile, which leaves with [onProfileSaved] once it saved, and Change number above it. */
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
}
