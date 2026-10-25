package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** [ChatsShownFirstRepository] in memory; [failure] makes saving fail. */
class FakeChatsShownFirstRepository(
    chatIds: List<Long> = emptyList(),
) : ChatsShownFirstRepository {
    val ids = MutableStateFlow<ImmutableList<Long>>(chatIds.toImmutableList())

    override val chatIds = ids

    var failure: DomainError? = null

    override suspend fun setChatIds(chatIds: List<Long>): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        ids.value = chatIds.distinct().take(ChatsShownFirstRepository.MAX).toImmutableList()
        return Outcome.Success(Unit)
    }
}
