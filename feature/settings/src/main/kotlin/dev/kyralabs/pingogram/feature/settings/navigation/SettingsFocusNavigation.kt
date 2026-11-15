package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.FocusProfileRoute
import dev.kyralabs.pingogram.core.navigation.FocusRoute
import dev.kyralabs.pingogram.feature.settings.ui.focus.FocusEditRoute
import dev.kyralabs.pingogram.feature.settings.ui.focus.FocusTemplate
import dev.kyralabs.pingogram.feature.settings.ui.focus.FocusRoute as FocusScreenRoute

/** Focus ([FocusRoute]) and a profile's editor ([FocusProfileRoute]), pushed above it. */
internal fun NavGraphBuilder.focusPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<FocusRoute> {
        FocusScreenRoute(
            onOpenProfile = { id, template -> onOpen(FocusProfileRoute(id, template?.name)) },
            onBack = onBack,
        )
    }
    settingsPage<FocusProfileRoute> { entry ->
        val name = entry.toRoute<FocusProfileRoute>().template
        FocusEditRoute(template = FocusTemplate.entries.firstOrNull { it.name == name }, onBack = onBack)
    }
}
