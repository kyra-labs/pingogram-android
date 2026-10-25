package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.SavedTopicKind
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTopicRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SavedMessagesTopicRepository] on TDLib's Saved Messages topics. TDLib announces each topic only with
 * `updateSavedMessagesTopic` (after `loadSavedMessagesTopics`, and whenever it changes: a new last message, a pin), so
 * they are kept for the session in an [UpdateStore], which makes this a [TdlibUpdateCache]: one entry per chat the
 * user saved messages from, a few fields each. `loadSavedMessagesTopics` answers 404 once all are loaded, which is no
 * error here.
 */
@AccountScoped
class TdlibSavedMessagesTopicRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : SavedMessagesTopicRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore<PersistentMap<Long, SavedMessagesTopic>>(client, scope, persistentMapOf()) { topics, update ->
                if (update is TdApi.UpdateSavedMessagesTopic) {
                    topics.put(update.topic.id, update.topic.toSavedMessagesTopic())
                } else {
                    topics
                }
            }

        override val topics: Flow<ImmutableList<SavedMessagesTopic>> =
            store.state
                .distinctUntilChanged { old, new -> old === new }
                .map { topics -> topics.values.listed() }
                .distinctUntilChanged()

        override suspend fun loadMore(): Outcome<Boolean> =
            when (val answer = client.request(TdApi.LoadSavedMessagesTopics(PAGE_SIZE))) {
                is Outcome.Success -> Outcome.Success(true)

                // 404: every topic is loaded.
                is Outcome.Failure -> if (answer.error == DomainError.NotFound) Outcome.Success(false) else answer
            }

        private companion object {
            const val PAGE_SIZE = 100
        }
    }

/** The listed topics (an `order` of 0 lists a topic nowhere), pinned first, then by `order`, highest first. */
internal fun Collection<SavedMessagesTopic>.listed(): ImmutableList<SavedMessagesTopic> =
    filter { it.order != 0L }
        .sortedWith(compareByDescending<SavedMessagesTopic> { it.isPinned }.thenByDescending { it.order })
        .toImmutableList()

internal fun TdApi.SavedMessagesTopic.toSavedMessagesTopic() =
    SavedMessagesTopic(
        id = id,
        kind =
            when (val type = type) {
                is TdApi.SavedMessagesTopicTypeSavedFromChat -> SavedTopicKind.SavedFrom(type.chatId)
                is TdApi.SavedMessagesTopicTypeAuthorHidden -> SavedTopicKind.AuthorHidden
                else -> SavedTopicKind.MyNotes
            },
        isPinned = isPinned,
        order = order,
        lastMessage = lastMessage?.toMessage(),
    )
