package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ProfileRoute
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToEditContact
import dev.kyralabs.pingogram.feature.media.navigation.navigateToMediaViewer
import dev.kyralabs.pingogram.feature.profile.navigation.profileScreen

/**
 * A user's profile (`REQUIREMENTS.md` FR-P2), pushed above the screen it is opened from. Its "Message" returns to the
 * conversation below it when that is the user's chat, and pushes the chat otherwise; another chat (a secret chat just
 * started, a group a mention named), another profile, a file at its message in the chat, and a photo, video or GIF in
 * the media viewer are pushed above it, so back returns to the profile. "Add to contacts" and "Edit contact" push
 * the contact editor (`EditContactRoute`, `feature:contacts`) above it.
 */
internal fun NavGraphBuilder.profile(navController: NavHostController) {
    profileScreen(
        onBack = { navController.popBackStack() },
        onOpenChat = { chatId -> navController.openChatFromProfile(chatId) },
        onOpenProfile = { userId -> navController.navigateToProfile(userId) },
        onOpenMedia = { chatId, messageId -> navController.navigateToMediaViewer(chatId, messageId) },
        onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, messageId)) },
        onEditContact = { userId -> navController.navigateToEditContact(userId) },
    )
}

/** Pushes the profile of user [userId], unless it is shown already. */
internal fun NavHostController.navigateToProfile(userId: Long) {
    navigate(ProfileRoute(userId)) { launchSingleTop = true }
}

/**
 * Opens chat [chatId] from a profile: back to the conversation below the profile if it is that chat (the profile was
 * opened from its title), else the chat pushed above the profile.
 */
private fun NavHostController.openChatFromProfile(chatId: Long) {
    val below = previousBackStackEntry
    val isBelow = below?.destination?.hasRoute<ChatRoute>() == true && below.toRoute<ChatRoute>().chatId == chatId
    if (isBelow) popBackStack() else navigate(ChatRoute(chatId)) { launchSingleTop = true }
}
