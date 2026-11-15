package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * The forums' topics with unread messages, which their rows count instead of the messages (owner decision 2026-11-15),
 * a part of [ChatListViewModel]. td_api.tl gives a chat no such count, so it comes from the forum's own topics
 * ([ForumTopicRepository.unreadTopicCount]), loaded only for the forums the list has shown ([onShown]) while they
 * have unread messages (`docs/PERFORMANCE.md` §3): a forum far down the list, or with nothing unread, asks TDLib for
 * nothing. A forum the list showed once keeps its count while it is in the list, so it does not change as it scrolls
 * back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ForumUnreadTopics(
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
     * The unread topics of the forums among [chats] that the list showed and that have unread messages, by chat id,
     * those counted so far; none at first, so the list's first content never waits for them.
     */
    fun of(chats: Flow<List<Chat>>): Flow<Map<Long, Int>> {
        val repository = repository ?: return flowOf(emptyMap())
        return combine(chats, groupRepository.supergroups, shown) { list, supergroups, seen ->
            list
                .filter { it.id in seen && it.unreadCount > 0 && it.isForum(supergroups) }
                .mapTo(HashSet()) { it.id }
        }.distinctUntilChanged()
            .flatMapLatest { forums ->
                if (forums.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(forums.map { id -> repository.unreadTopicCount(id).map { id to it } }) { counts ->
                        counts.mapNotNull { (id, count) -> count?.let { id to it } }.toMap()
                    }
                }
            }.onStart { emit(emptyMap()) }
            .distinctUntilChanged()
    }
}

private fun Chat.isForum(supergroups: Map<Long, Supergroup>): Boolean =
    (type as? ChatType.Supergroup)?.let { supergroups[it.supergroupId]?.isForum } == true
