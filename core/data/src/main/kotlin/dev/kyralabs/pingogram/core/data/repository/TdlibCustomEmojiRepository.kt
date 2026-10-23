package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toCustomEmoji
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CustomEmojiRepository] on TDLib's `getCustomEmojiStickers`, which answers from memory or its database where it can,
 * else asks Telegram. What it answered stays in memory by custom emoji id for the session ([CustomEmojiCache], an
 * [UpdateStore] that empties when TDLib closes, as file ids belong to one TDLib instance); TDLib announces no custom
 * emoji by an update, so nothing is missed before the first request: it is no `TdlibUpdateCache`.
 *
 * - **Deduplicated**: ids already loaded, on their way ([pending]) or that TDLib did not return
 *   ([CustomEmojiCache.unknown], a deleted set's) are not asked for again in this session.
 * - **Batched**: the rest go in requests of at most [MAX_IDS_PER_REQUEST] ids, one after the other.
 * - **Cancellable**: a cancelled caller stops waiting, and its ids leave [pending], so the next caller asks for them;
 *   TDLib's late answer is dropped (`TdlibClient.send`).
 */
@Singleton
class TdlibCustomEmojiRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : CustomEmojiRepository {
        private val store = UpdateStore(client, scope, CustomEmojiCache()) { cache, _ -> cache }

        /** The ids of the requests running now. */
        private val pending: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        override val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>> =
            store.state
                .map { it.loaded }
                .stateIn(scope, SharingStarted.Eagerly, persistentMapOf())

        override suspend fun load(customEmojiIds: Collection<Long>): Outcome<Unit> {
            val cache = store.state.value
            // add() reserves each id at once, so two callers never ask for the same one.
            val wanted =
                customEmojiIds.distinct().filter {
                    it !in cache.loaded && it !in cache.unknown &&
                        pending.add(it)
                }
            var error: DomainError? = null
            val batches = wanted.chunked(MAX_IDS_PER_REQUEST)
            var next = 0
            try {
                while (next < batches.size) {
                    val failure = request(batches[next++])
                    if (error == null) error = failure
                }
            } finally {
                // Cancelled while a batch waited: the ones after it never ran.
                pending.removeAll(batches.drop(next).flatten().toSet())
            }
            return error?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
        }

        /** Asks TDLib for [batch], keeping what it returns; the error if it failed. Its ids leave [pending] after. */
        private suspend fun request(batch: List<Long>): DomainError? =
            try {
                when (val outcome = client.request(TdApi.GetCustomEmojiStickers(batch.toLongArray()))) {
                    is Outcome.Success -> {
                        store.update { it.withAnswer(batch, outcome.value) }
                        null
                    }

                    is Outcome.Failure -> {
                        outcome.error
                    }
                }
            } finally {
                pending.removeAll(batch.toSet())
            }

        private companion object {
            /** td_api.tl `getCustomEmojiStickers`: "identifiers of custom emoji stickers. At most 200". */
            const val MAX_IDS_PER_REQUEST = 200
        }
    }

/**
 * The custom emoji TDLib returned ([loaded]), and the ids it was asked for and did not return ([unknown]): a deleted
 * set's, or of a kind added by a newer TDLib.
 */
internal data class CustomEmojiCache(
    val loaded: PersistentMap<Long, CustomEmoji> = persistentMapOf(),
    val unknown: PersistentSet<Long> = persistentSetOf(),
) {
    /** With TDLib's [answer] to a request for [requested]. */
    fun withAnswer(
        requested: List<Long>,
        answer: TdApi.Stickers,
    ): CustomEmojiCache {
        val emoji = answer.stickers.orEmpty().mapNotNull { it.toCustomEmoji() }
        val returned = emoji.mapTo(HashSet()) { it.id }
        return CustomEmojiCache(
            loaded = loaded.puttingAll(emoji.associateBy { it.id }),
            unknown = unknown.addingAll(requested.filterNot { it in returned }),
        )
    }

    override fun toString() = "CustomEmojiCache(loaded=${loaded.size}, unknown=${unknown.size})"
}
