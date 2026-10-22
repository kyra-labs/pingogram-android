package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.SettingsPage
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.LanguageRoute
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.navigation.NotificationSettingsRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute

/**
 * Hands [link], a Telegram link another app opened Pingogram with, to [onOpen] once the main graph shows, after
 * `SessionNavigationEffect` moved there, and reports it handled ([onHandled]). Unlike a notification's chat, a link
 * belongs to no account: signed out, it waits for the login and opens after it (`docs/adr/0013-deep-links.md`). The
 * passcode lock holds it too, as the app, and this effect, are not composed while locked.
 */
@Composable
internal fun OpenLinkEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    link: String?,
    onOpen: (link: String) -> Unit,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(link, uiState, inMainGraph) {
        if (link == null || uiState != MainActivityUiState.SignedIn || !inMainGraph) return@LaunchedEffect
        currentOnOpen(link)
        currentOnHandled()
    }
}

/**
 * Opens the screen of a link's [target] (`docs/adr/0013-deep-links.md`); false for a target without a screen yet
 * (`hasScreen`), which then opens nothing.
 *
 * - A **chat** is pushed above the screen the link was tapped on, or, from another app ([fromOutside]), above the chat
 *   list, as from a notification, so back returns to the chat list. A link to the chat already open shows nothing new;
 *   one to a message of it opens the conversation at the message in its place, as "Show in chat" does.
 * - A **user's profile** is pushed, and so is a group's or channel's **info** (`ChatInfoRoute`).
 * - **Contacts** and **Settings** switch tabs, a page of Settings is pushed above its tab; **Edit profile** and **New
 *   chat** are pushed; **search** opens with [onOpenSearch], docked or full screen as the window wants.
 */
@Suppress("CyclomaticComplexMethod") // One branch per kind of target, each a single navigation.
internal fun NavHostController.openLinkTarget(
    target: LinkTarget,
    fromOutside: Boolean,
    onOpenSearch: () -> Unit,
): Boolean {
    when (target) {
        is LinkTarget.Chat -> openLinkedChat(target, fromOutside)
        is LinkTarget.ChatInfo -> navigateToChatInfo(target.chatId)
        is LinkTarget.UserProfile -> navigateToProfile(target.userId)
        LinkTarget.EditProfile -> navigate(EditProfileRoute) { launchSingleTop = true }
        LinkTarget.Contacts -> navigateToTopLevelDestination(TopLevelDestination.Contacts)
        is LinkTarget.Settings -> openSettings(target.page)
        is LinkTarget.NewChat -> navigate(NewChatRoute) { launchSingleTop = true }
        LinkTarget.Search -> onOpenSearch()
        else -> return false
    }
    return true
}

/** Pushes the conversation of [target], [fromOutside] above the chat list; nothing if it shows already. */
private fun NavHostController.openLinkedChat(
    target: LinkTarget.Chat,
    fromOutside: Boolean,
) {
    val current = currentBackStackEntry
    val openChat = current?.takeIf { it.destination.hasRoute<ChatRoute>() }?.toRoute<ChatRoute>()
    val sameChat = openChat?.chatId == target.chatId
    if (sameChat && target.messageId == null) return
    navigate(ChatRoute(target.chatId, target.messageId)) {
        when {
            sameChat -> popUpTo<ChatRoute> { inclusive = true }
            fromOutside -> popUpTo(TopLevelDestination.Start.route)
        }
    }
}

/** The Settings tab, with [page] pushed above it if the link named one. */
private fun NavHostController.openSettings(page: SettingsPage?) {
    navigateToTopLevelDestination(TopLevelDestination.Settings)
    val route: Any =
        when (page) {
            null -> return
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.DataAndStorage -> DataStorageRoute
            SettingsPage.ActiveSessions -> ActiveSessionsRoute
            SettingsPage.Language -> LanguageRoute
            SettingsPage.Notifications -> NotificationSettingsRoute
            SettingsPage.PrivacyAndSecurity -> PrivacySecurityRoute
        }
    navigate(route) { launchSingleTop = true }
}
