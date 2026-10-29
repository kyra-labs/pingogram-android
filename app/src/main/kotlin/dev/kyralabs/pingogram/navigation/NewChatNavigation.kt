package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.NewChannelRoute
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.navigation.NewGroupRoute
import dev.kyralabs.pingogram.feature.chats.navigation.newChannelScreens
import dev.kyralabs.pingogram.feature.chats.navigation.newChatScreen
import dev.kyralabs.pingogram.feature.chats.navigation.newGroupScreens
import dev.kyralabs.pingogram.feature.contacts.navigation.navigateToNewContact

/**
 * New chat and the flows it opens, creating a group or a channel. A contact's chat takes the New chat screen's place,
 * so back returns to the chat list rather than to a picker that has done its job. So does a new group's or channel's,
 * in place of the New chat screen and the flow that created it.
 */
internal fun NavGraphBuilder.newChatScreens(navController: NavHostController) {
    val openChatInPlaceOfNewChat = { chatId: Long ->
        navController.navigate(ChatRoute(chatId)) {
            popUpTo<NewChatRoute> { inclusive = true }
            launchSingleTop = true
        }
    }
    newChatScreen(
        onBack = { navController.popBackStack() },
        onOpenChat = openChatInPlaceOfNewChat,
        onNewGroup = { navController.navigate(NewGroupRoute) { launchSingleTop = true } },
        onNewChannel = { navController.navigate(NewChannelRoute) { launchSingleTop = true } },
        onAddContact = { navController.navigateToNewContact() },
    )
    newGroupScreens(navController, onOpenChat = openChatInPlaceOfNewChat)
    newChannelScreens(navController, onOpenChat = openChatInPlaceOfNewChat)
}
