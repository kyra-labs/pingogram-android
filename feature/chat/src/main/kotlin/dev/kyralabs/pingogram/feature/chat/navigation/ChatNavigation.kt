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
import dev.kyralabs.pingogram.feature.chat.ui.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute as ChatDestination

/** Adds the conversation of a chat, [ChatDestination], to the graph; [onBack] leaves it. */
fun NavGraphBuilder.chatScreen(onBack: () -> Unit) {
    composable<ChatDestination> { entry ->
        ChatRoute(chatId = entry.toRoute<ChatDestination>().chatId, onBack = onBack)
    }
}

/**
 * The conversation of chat [chatId] in the detail pane of the Chats tab's list-detail layout (`docs/ARCHITECTURE.md`,
 * Navigation): the same screen as [ChatDestination], with the top bar's back button only where [onBack] is given,
 * i.e. when the pane shows alone.
 *
 * Each chat gets a fresh composition and view model: its own saved UI state (the scroll position), and a
 * [ViewModelStoreOwner][androidx.lifecycle.ViewModelStoreOwner] of its own ([rememberViewModelStoreOwner]), which
 * keeps the view model across rotations and clears it as soon as the pane shows another chat or leaves, closing the
 * chat first. In the store of the Chats tab every chat ever shown would keep its view model, and its messages, until
 * logout (`docs/PERFORMANCE.md` §3). The cost: the view model's `SavedStateHandle` (where the unread divider was)
 * survives rotations but not process death, since the tab's saved state holds only the handles of its own store.
 */
@Composable
fun ChatPane(
    chatId: Long,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    key(chatId) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
            ChatRoute(chatId = chatId, onBack = onBack, modifier = modifier)
        }
    }
}
