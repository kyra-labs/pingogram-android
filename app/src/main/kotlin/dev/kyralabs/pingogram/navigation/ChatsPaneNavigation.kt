package dev.kyralabs.pingogram.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import kotlinx.coroutines.flow.StateFlow

// Opening a chat in the Chats tab's detail pane from elsewhere (docs/ARCHITECTURE.md, Navigation): a deep link, full
// screen search, a profile's "Message".

/**
 * Whether the Chats tab shows the conversation beside the chat list in a window of this size (`docs/DESIGN_SYSTEM.md`
 * §7): expanded and wider. Global search docks there too.
 */
internal fun WindowSizeClass.showsChatBesideList(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** [showsChatBesideList] for the current window, as state that callbacks read when they run. */
@Composable
internal fun rememberChatBesideList(): State<Boolean> =
    rememberUpdatedState(currentWindowAdaptiveInfoV2().windowSizeClass.showsChatBesideList())

/** A chat the Chats tab is asked to show in its detail pane, at [messageId] if not null. */
@Immutable
internal data class ChatToShow(
    val chatId: Long,
    val messageId: Long? = null,
)

/**
 * Opens chat [chatId], at [messageId] if not null: [besideList], in the Chats tab's detail pane, back on the Chats tab
 * below everything pushed above it, as a chat from a notification opens above it; otherwise pushed above the current
 * screen. The request waits in the Chats tab's saved state ([chatToShow]) until the tab takes it, so it survives a
 * configuration change or process death on the way.
 */
internal fun NavHostController.navigateToChat(
    chatId: Long,
    messageId: Long? = null,
    besideList: Boolean,
) {
    val chats = if (besideList) runCatching { getBackStackEntry<ChatsRoute>() }.getOrNull() else null
    if (chats == null) {
        navigate(ChatRoute(chatId, messageId)) { launchSingleTop = true }
        return
    }
    chats.savedStateHandle[CHAT_TO_SHOW] = longArrayOf(chatId, messageId ?: NO_MESSAGE)
    popBackStack<ChatsRoute>(inclusive = false)
}

/** The chat [navigateToChat] asked the Chats tab, whose saved state this is, to show; null once taken ([chatShown]). */
internal fun SavedStateHandle.chatToShow(): StateFlow<LongArray?> = getStateFlow(CHAT_TO_SHOW, null)

/** The chat of a [chatToShow] request; null for none. */
internal fun LongArray?.toChatToShow(): ChatToShow? =
    this?.let { ChatToShow(it[0], it[1].takeUnless { id -> id == NO_MESSAGE }) }

/** Takes the chat [chatToShow] asked for, so it is shown once. */
internal fun SavedStateHandle.chatShown() {
    set(CHAT_TO_SHOW, null)
}

private const val CHAT_TO_SHOW = "chats.chatToShow"

/** No message: the chat opens where it usually does. TDLib's message ids are positive. */
private const val NO_MESSAGE = 0L
