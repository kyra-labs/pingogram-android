package dev.kyralabs.pingogram.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ProfileRoute
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRoute as ProfileScreenRoute

/**
 * Adds a user's profile, [ProfileRoute], to the graph (`REQUIREMENTS.md` FR-P2), pushed above the screen it is opened
 * from. [onBack] leaves it; [onOpenChat] opens a chat (the private chat, a secret chat just started, a group or
 * channel a mention in the bio named), [onOpenProfile] another user's profile (a mention), [onOpenMedia] a photo, video
 * or GIF of the shared media in the media viewer, and [onOpenMessage] a file, song or voice message in the chat at its
 * message. [onEditContact], where the app has a contact editor, is what "Add to contacts" and "Edit contact" open for
 * the user; without it, those rows are left out.
 */
fun NavGraphBuilder.profileScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    onEditContact: ((userId: Long) -> Unit)? = null,
) {
    composable<ProfileRoute> {
        ProfileScreenRoute(
            onBack = onBack,
            onOpenChat = onOpenChat,
            onOpenProfile = onOpenProfile,
            onOpenMedia = onOpenMedia,
            onOpenMessage = onOpenMessage,
            onEditContact = onEditContact,
        )
    }
}
