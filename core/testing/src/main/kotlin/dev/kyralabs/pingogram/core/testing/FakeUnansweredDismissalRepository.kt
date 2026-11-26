package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.UnansweredDismissalRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow

/** [UnansweredDismissalRepository] in memory, with none dismissed at first. */
class FakeUnansweredDismissalRepository : UnansweredDismissalRepository {
    override val dismissed = MutableStateFlow<ImmutableMap<Long, Long>>(persistentMapOf())

    override suspend fun dismiss(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> {
        dismissed.value = (dismissed.value + (chatId to messageId)).toImmutableMap()
        return Outcome.Success(Unit)
    }
}
