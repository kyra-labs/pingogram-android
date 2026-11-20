package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.ShownChatListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Jumping to the next unread channel or forum topic (docs/audit/feature-gaps-octogram.md P2, docs/DESIGN_SYSTEM.md §6).

/**
 * What a read channel's or forum topic's conversation offers next: channel [chatId], or its forum topic [topicId]
 * (null for a channel), its [title], [photo] for its avatar (none for a topic), and its [unreadCount] messages the
 * user has not read.
 */
@Immutable
data class NextChannel(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val unreadCount: Int,
    val topicId: Int? = null,
)

/**
 * What the conversation of chat [chatId] offers next, for `ChatViewModel` ([next]): in a channel without unread
 * posts, the first other channel with unread posts of the list the Chats tab shows (the main list or a folder,
 * [ThreadRepositories.shownLists]), in its order; in a forum topic ([topic]) without unread messages, the forum's
 * first other topic with unread messages, in the forum's order ([ThreadRepositories.forumTopics]). Null in any other
 * chat, a thread, and while what shows has unread messages. It follows the lists only while what shows is read, and
 * only while someone collects it. Opening it goes to [onEffect].
 */
internal class ChatNextChannel(
    private val chatId: Long,
    topic: MessageTopic?,
    repositories: ThreadRepositories,
    scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    val next: StateFlow<NextChannel?> =
        when (topic) {
            null -> nextChannel(repositories.chats, repositories.shownLists)
            is MessageTopic.Forum -> nextTopic(topic.topicId, repositories.chats, repositories.forumTopics)
            else -> flowOf(null)
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun nextChannel(
        chats: ChatRepository,
        shownLists: ShownChatListRepository,
    ): Flow<NextChannel?> =
        chats
            .chat(chatId)
            .map { it?.isChannel == true && it.unreadCount == 0 }
            .distinctUntilChanged()
            .flatMapLatest { read ->
                if (read) {
                    shownLists.shown.flatMapLatest(chats::chatList).map { nextChannelIn(it, chatId) }
                } else {
                    flowOf(null)
                }
            }

    /** The forum's loaded topics, enough to hold its unread ones, as the chat list counts them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun nextTopic(
        topicId: Int,
        chats: ChatRepository,
        forumTopics: ForumTopicRepository,
    ): Flow<NextChannel?> =
        forumTopics
            .topic(chatId, topicId)
            .map { it?.unreadCount == 0 }
            .distinctUntilChanged()
            .flatMapLatest { read ->
                if (read) {
                    val unread = chats.chat(chatId).map { it?.unreadCount ?: 0 }
                    combine(forumTopics.recentTopics(chatId, unread), flowOf(topicId)) { topics, current ->
                        topics?.let { nextTopicIn(it, current) }
                    }
                } else {
                    flowOf(null)
                }
            }

    /**
     * Opens [target] in this conversation's place ([ChatEffect.OpenNextChannel]), where [next] still offers it: the
     * one a pull showed may have been read meanwhile.
     */
    fun open(target: NextChannel) {
        val offered = next.value ?: return
        if (offered.chatId == target.chatId && offered.topicId == target.topicId) {
            onEffect(ChatEffect.OpenNextChannel(target.chatId, target.topicId))
        }
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
        ?.let { NextChannel(it.id, it.title, it.photo, it.unreadCount) }

/** The first topic of [topics] but [current] with unread messages, in the forum's order, as [NextChannel]. */
internal fun nextTopicIn(
    topics: List<ForumTopic>,
    current: Int,
): NextChannel? =
    topics
        .sortedByDescending { it.order }
        .firstOrNull { it.id != current && it.unreadCount > 0 }
        ?.let { NextChannel(it.chatId, it.name, photo = null, unreadCount = it.unreadCount, topicId = it.id) }
