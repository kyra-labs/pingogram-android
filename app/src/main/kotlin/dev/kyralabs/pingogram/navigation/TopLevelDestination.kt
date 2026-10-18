package dev.kyralabs.pingogram.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The destinations of the navigation bar or rail, in display order. Icons are Material Symbols Rounded:
 * outlined when unselected, filled when selected (`docs/DESIGN_SYSTEM.md` §8). The label is also the
 * item's accessibility name.
 */
internal enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val unselectedIcon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    Chats(ChatsRoute, R.string.nav_chats, UiR.drawable.ic_chat, UiR.drawable.ic_chat_filled),
    Contacts(ContactsRoute, R.string.nav_contacts, UiR.drawable.ic_contacts, UiR.drawable.ic_contacts_filled),
    Settings(SettingsRoute, R.string.nav_settings, UiR.drawable.ic_settings, UiR.drawable.ic_settings_filled),
    ;

    companion object {
        /** The start destination of the main graph. Back from any other tab returns here. */
        val Start = Chats
    }
}

/** The tab this destination is, or null for any other destination, e.g. one pushed on top of a tab. */
internal fun NavDestination?.topLevelDestination(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { this?.hasRoute(it.route::class) == true }

/** Whether this destination is [destination] or nested inside it. */
internal fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/**
 * Switches tabs the standard Material way: one instance per tab, the back stack never grows past
 * `Start → tab`, and each tab's own back stack and UI state are saved when leaving it and restored on return.
 */
internal fun NavController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(TopLevelDestination.Start.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
