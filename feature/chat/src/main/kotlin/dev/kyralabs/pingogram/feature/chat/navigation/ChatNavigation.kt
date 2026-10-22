package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chat.ui.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute as ChatDestination

/**
 * Adds the conversation of a chat, [ChatDestination], to the graph, opened at its message if the route names one;
 * [onBack] leaves it, and [onOpenMedia] opens a tapped photo, video or GIF of the chat in the media viewer, which it
 * grows into (the destination provides its animated visibility scope for that container transform). [onOpenChat]
 * opens the chat messages were forwarded to, from the snackbar after forwarding, and a group or channel a mention
 * names; [onOpenProfile] a user's profile, from the title of a private chat, a sender's avatar or name, or a mention;
 * [onOpenChatInfo] the info page of a group or channel, from its title.
 */
fun NavGraphBuilder.chatScreen(
    onBack: () -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit = {},
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
) {
    composable<ChatDestination> { entry ->
        val route = entry.toRoute<ChatDestination>()
        ProvideNavAnimatedVisibilityScope(this) {
            ChatRoute(
                chatId = route.chatId,
                messageId = route.messageId,
                onBack = onBack,
                onOpenMedia = onOpenMedia,
                onOpenChat = onOpenChat,
                onOpenProfile = onOpenProfile,
                onOpenChatInfo = onOpenChatInfo,
            )
        }
    }
}

/**
 * The conversation of chat [chatId] in the detail pane of the Chats tab's list-detail layout (`docs/ARCHITECTURE.md`,
 * Navigation): the same screen as [ChatDestination], with the top bar's back button only where [onBack] is given,
 * i.e. when the pane shows alone, opened at [messageId] if not null (a message found by search). [onOpenMedia] opens a
 * tapped photo, video or GIF in the media viewer, full screen above the whole layout; [onOpenChat] the chat messages
 * were forwarded to; [onOpenProfile] a user's profile and [onOpenChatInfo] a group's or channel's info page, which the
 * layout pushes above itself or shows beside the conversation.
 *
 * Each chat, and each message it is opened at, gets a fresh composition and view model: its own saved UI state (the
 * scroll position), and a [ViewModelStoreOwner][androidx.lifecycle.ViewModelStoreOwner] of its own
 * ([rememberViewModelStoreOwner]), which keeps the view model across rotations and clears it as soon as the pane shows
 * another chat or leaves, closing the chat first. In the store of the Chats tab every chat ever shown would keep its
 * view model, and its messages, until logout (`docs/PERFORMANCE.md` §3). The cost: the view model's `SavedStateHandle`
 * (where the unread divider was) survives rotations but not process death, since the tab's saved state holds only the
 * handles of its own store.
 */
@Composable
fun ChatPane(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
) {
    key(chatId, messageId) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
            ChatRoute(
                chatId = chatId,
                messageId = messageId,
                onBack = onBack,
                onOpenMedia = onOpenMedia,
                modifier = modifier,
                onOpenChat = onOpenChat,
                onOpenProfile = onOpenProfile,
                onOpenChatInfo = onOpenChatInfo,
            )
        }
    }
}
