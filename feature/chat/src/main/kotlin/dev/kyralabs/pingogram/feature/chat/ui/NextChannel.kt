package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Jumping to the next unread channel (docs/audit/feature-gaps-octogram.md P2, docs/DESIGN_SYSTEM.md §6).

/** The channel a read channel's conversation offers next: [chatId], its [title] and [photo] for its avatar. */
@Immutable
data class NextChannel(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
)

/**
 * The next unread channel for the conversation of channel [chatId], for `ChatViewModel`: while the channel has no
 * unread posts, the first other channel of the main chat list, in its order, with unread posts ([next]); null in any
 * other chat, a topic or a thread ([wholeChat] false), and while the channel has unread posts. It follows the chat
 * list only while the conversation is a read channel, and only while someone collects it. Opening it goes to
 * [onEffect].
 */
internal class ChatNextChannel(
    private val chatId: Long,
    wholeChat: Boolean,
    chats: ChatRepository,
    scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val next: StateFlow<NextChannel?> =
        (if (wholeChat) chats.chat(chatId) else flowOf(null))
            .map { it?.isChannel == true && it.unreadCount == 0 }
            .distinctUntilChanged()
            .flatMapLatest { read ->
                if (read) chats.chatList(ChatListType.Main).map { nextChannelIn(it, chatId) } else flowOf(null)
            }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Opens channel [target] in this one's place ([ChatEffect.OpenNextChannel]), where [next] still offers it: the one
     * a pull showed may have been read meanwhile.
     */
    fun open(target: Long) {
        if (next.value?.chatId == target) onEffect(ChatEffect.OpenNextChannel(target))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The first channel of [chats] but [current] with unread posts, as [NextChannel]. */
internal fun nextChannelIn(
    chats: List<Chat>,
    current: Long,
): NextChannel? =
    chats
        .firstOrNull { it.id != current && it.isChannel && it.unreadCount > 0 }
        ?.let { NextChannel(it.id, it.title, it.photo) }
