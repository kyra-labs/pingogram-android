package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * What the forums' rows show of their topics, a part of [ChatListViewModel]: the topics with unread messages, which
 * their badges count instead of the messages, and the newest topics, which they name on a line of their own (owner
 * decisions 2026-11-15). td_api.tl gives a chat neither, so they come from the forum's own topics
 * ([ForumTopicRepository.recentTopics]), loaded only for the forums the list has shown ([onShown]), as Telegram's apps
 * load them (`docs/PERFORMANCE.md` §3): a forum far down the list asks TDLib for nothing. A forum the list showed once
 * keeps its topics while it is in the list, so they do not change as it scrolls back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ForumRowTopics(
    private val repository: ForumTopicRepository?,
    private val groupRepository: GroupRepository,
) {
    /** The chats the list has shown, by id. */
    private val shown = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The list shows the rows of chats [chatIds]. */
    fun onShown(chatIds: Set<Long>) {
        shown.update { seen -> if (seen.containsAll(chatIds)) seen else seen.addingAll(chatIds) }
    }

    /**
     * The topics of the forums among [chats] that the list showed, by chat id, those loaded so far; none at first, so
     * the list's first content never waits for them.
     */
    fun of(chats: Flow<List<Chat>>): Flow<Map<Long, ForumRowSummary>> {
        val repository = repository ?: return flowOf(emptyMap())
        // The shown forums' unread messages, which say how many of their topics to load.
        val unreadMessages = MutableStateFlow<Map<Long, Int>>(emptyMap())
        return combine(chats, groupRepository.supergroups, shown) { list, supergroups, seen ->
            list
                .filter { it.id in seen && it.isForum(supergroups) }
                .associate { it.id to it.unreadCount }
        }.onEach { unreadMessages.value = it }
            .map { it.keys }
            .distinctUntilChanged()
            .flatMapLatest { forums ->
                if (forums.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        forums.map { id ->
                            repository
                                .recentTopics(id, unreadMessages.map { it[id] ?: 0 })
                                .map { topics -> id to topics?.toSummary() }
                                .distinctUntilChanged()
                        },
                    ) { summaries ->
                        summaries.mapNotNull { (id, summary) -> summary?.let { id to it } }.toMap()
                    }
                }
            }.onStart { emit(emptyMap()) }
            .distinctUntilChanged()
    }
}

/**
 * What a forum's row shows of its topics: how many have unread messages ([unreadTopicCount]), and the newest ones it
 * names ([topics], at most [SHOWN_TOPICS], which a line holds at most).
 */
internal data class ForumRowSummary(
    val unreadTopicCount: Int,
    val topics: ImmutableList<RowTopic>,
)

private fun List<ForumTopic>.toSummary() =
    ForumRowSummary(
        unreadTopicCount = count { it.unreadCount > 0 },
        topics =
            take(SHOWN_TOPICS)
                .map { topic ->
                    RowTopic(
                        id = topic.id,
                        name = topic.name,
                        color = topic.icon.color.takeUnless { topic.isGeneral },
                        isUnread = topic.unreadCount > 0,
                    )
                }.toImmutableList(),
    )

/** The most topics a forum's row names: more never fit its line. */
private const val SHOWN_TOPICS = 5

private fun Chat.isForum(supergroups: Map<Long, Supergroup>): Boolean =
    (type as? ChatType.Supergroup)?.let { supergroups[it.supergroupId]?.isForum } == true
