package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [CustomEmojiRepository] in memory: [load] records what it is asked for ([loads]) and moves the custom emoji of
 * [known] it is asked for into [customEmoji]; [failure] makes it fail instead.
 */
class FakeCustomEmojiRepository(
    var known: Map<Long, CustomEmoji> = emptyMap(),
) : CustomEmojiRepository {
    override val customEmoji = MutableStateFlow<ImmutableMap<Long, CustomEmoji>>(persistentMapOf())

    /** Every set of ids [load] was asked for, in order. */
    val loads = mutableListOf<Set<Long>>()

    /** What loading fails with; null loads. */
    var failure: DomainError? = null

    override suspend fun load(customEmojiIds: Collection<Long>): Outcome<Unit> {
        loads += customEmojiIds.toSet()
        failure?.let { return Outcome.Failure(it) }
        val found = known.filterKeys { it in customEmojiIds }
        customEmoji.value = (customEmoji.value + found).toImmutableMap()
        return Outcome.Success(Unit)
    }
}
