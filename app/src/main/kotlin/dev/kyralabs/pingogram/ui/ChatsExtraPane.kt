package dev.kyralabs.pingogram.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.core.navigation.ChatInfoRoute
import dev.kyralabs.pingogram.core.navigation.ProfileRoute
import dev.kyralabs.pingogram.core.ui.component.PaneAsWindow
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopEnter
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopExit
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.core.ui.transition.LocalSharedTransitionScope
import dev.kyralabs.pingogram.feature.profile.navigation.chatInfoScreens
import dev.kyralabs.pingogram.feature.profile.navigation.popBackStackAfterGroupChange
import dev.kyralabs.pingogram.feature.profile.navigation.profileScreen

/**
 * What a pane of the Chats tab shows beside the list, as the pane navigator's content key ([key], a string, which the
 * navigator saves in a `Bundle`): the conversation of a chat ([Chat]), or beside it, in the extra pane, a user's
 * profile ([Profile]) or a group's or channel's info page ([ChatInfo]). Each names the chat whose conversation the
 * detail pane shows ([chatId]), so the detail pane knows it while the extra pane is the navigator's current one.
 */
@Immutable
internal sealed interface ChatsPaneContent {
    /** The chat whose conversation the detail pane shows. */
    val chatId: Long

    /** The navigator's content key. */
    val key: String

    /** The conversation of [chatId]. */
    data class Chat(
        override val chatId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$CHAT:$chatId"
    }

    /** User [userId]'s profile beside the conversation of [chatId]. */
    data class Profile(
        override val chatId: Long,
        val userId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$PROFILE:$chatId:$userId"
    }

    /** The info page of group or channel [infoChatId] beside the conversation of [chatId]. */
    data class ChatInfo(
        override val chatId: Long,
        val infoChatId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$CHAT_INFO:$chatId:$infoChatId"
    }

    companion object {
        private const val CHAT = "chat"
        private const val PROFILE = "profile"
        private const val CHAT_INFO = "info"

        /** The content of navigator content key [key]; null for none, or a key this layout did not make. */
        fun of(key: String?): ChatsPaneContent? {
            val parts = key?.split(':') ?: return null
            val ids = parts.drop(1).map { it.toLongOrNull() ?: return null }
            return when {
                parts[0] == CHAT && ids.size == 1 -> Chat(ids[0])
                parts[0] == PROFILE && ids.size == 2 -> Profile(ids[0], ids[1])
                parts[0] == CHAT_INFO && ids.size == 2 -> ChatInfo(ids[0], ids[1])
                else -> null
            }
        }
    }
}

/**
 * The Chats tab's extra pane on extra-large windows (`docs/DESIGN_SYSTEM.md` §7): [content]'s profile or info page
 * beside the conversation, with the pages it opens above it in a `NavHost` of its own (another profile from a mention
 * or a member, the members, permissions and invite links of a group), sliding on the shared axis on X and leaving with
 * the predictive back gesture, as pushed pages do; as Settings' detail pane does. Back from the first page, and its
 * back button, call [onClose].
 *
 * Laid out as the window it is ([PaneAsWindow]), so the pages show their one-pane layouts at the pane's width. Each
 * page gets its view models from a store of the pane's own ([rememberViewModelStoreOwner]), which survives rotation
 * and goes with the pane; the `NavHost`'s back stack is saved, so the pages survive process death too. A chat a page
 * opens goes to the detail pane ([onOpenChat], [onOpenMessage]), media to the viewer ([onOpenMedia]), the contact
 * editor above the layout ([onEditContact]); once the user left or deleted the group, [onLeft]. The container
 * transform from the conversation's avatar is not run here: the pane comes in with the scaffold's own motion.
 */
@Composable
internal fun ChatsExtraPane(
    content: ChatsPaneContent,
    onClose: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onEditContact: (userId: Long) -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start: Any =
        when (content) {
            is ChatsPaneContent.Profile -> ProfileRoute(content.userId)
            is ChatsPaneContent.ChatInfo -> ChatInfoRoute(content.infoChatId)
            is ChatsPaneContent.Chat -> return
        }
    // Each page its own back stack and view models, gone with it.
    key(content.key) {
        PaneAsWindow(modifier) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides rememberViewModelStoreOwner(),
                LocalSharedTransitionScope provides null,
            ) {
                ExtraPaneNavHost(start, onClose, onOpenChat, onOpenMessage, onOpenMedia, onEditContact, onLeft)
            }
        }
    }
}

/** The extra pane's `NavHost`, starting at [start] ([ChatsExtraPane]). */
@Suppress("LongParameterList") // The pane's own start and the callbacks of ChatsExtraPane, passed through.
@Composable
private fun ExtraPaneNavHost(
    start: Any,
    onClose: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onEditContact: (userId: Long) -> Unit,
    onLeft: () -> Unit,
) {
    val navController = rememberNavController()
    val motionScheme = MaterialTheme.motionScheme
    val axisDistance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
    val back = { navController.popOrElse(onClose) }
    val openProfile = { userId: Long -> navController.navigate(ProfileRoute(userId)) { launchSingleTop = true } }
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { sharedAxisXEnter(motionScheme, axisDistance, forward = true) },
        exitTransition = { sharedAxisXExit(motionScheme, axisDistance, forward = true) },
        popEnterTransition = { sharedAxisXEnter(motionScheme, axisDistance, forward = false) },
        popExitTransition = { sharedAxisXExit(motionScheme, axisDistance, forward = false) },
        predictivePopEnterTransition = { predictiveBackPopEnter() },
        predictivePopExitTransition = { predictiveBackPopExit(motionScheme) },
    ) {
        profileScreen(
            onBack = back,
            onOpenChat = onOpenChat,
            onOpenProfile = openProfile,
            onOpenMedia = onOpenMedia,
            onOpenMessage = onOpenMessage,
            onEditContact = onEditContact,
        )
        chatInfoScreens(
            onOpen = { route -> navController.navigate(route) { launchSingleTop = true } },
            onBack = back,
            onLeft = onLeft,
            onOpenChat = onOpenChat,
            onOpenProfile = openProfile,
            onOpenMedia = onOpenMedia,
            onOpenMessage = onOpenMessage,
            onSaved = { navController.popBackStackAfterGroupChange() },
        )
    }
}

/** Pops the page shown, or [orElse] for the first. */
private fun NavController.popOrElse(orElse: () -> Unit) {
    if (previousBackStackEntry != null) popBackStack() else orElse()
}
