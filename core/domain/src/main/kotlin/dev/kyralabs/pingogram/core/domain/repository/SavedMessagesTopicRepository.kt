package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The topics of Saved Messages (td_api.tl `savedMessagesTopic`), which TDLib announces one by one with
 * `updateSavedMessagesTopic` once asked for them, and which this repository keeps for the session. The messages of a
 * topic are `MessageRepository`'s history of `MessageTopic.SavedMessages`.
 *
 * Requests that get no answer in time fail with `DomainError.Network`, a flood wait with `DomainError.FloodWait`.
 */
interface SavedMessagesTopicRepository {
    /**
     * The topics TDLib announced, in its order (pinned first, then by `order`, highest first), then the list after
     * every change. [loadMore] asks for them.
     */
    val topics: Flow<ImmutableList<SavedMessagesTopic>>

    /**
     * Asks TDLib for more topics (td_api.tl `loadSavedMessagesTopics`), which reach [topics]. True if more may follow,
     * false once all are loaded.
     */
    suspend fun loadMore(): Outcome<Boolean>
}
