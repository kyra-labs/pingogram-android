package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** [ChatOpeningsRepository] in memory, from [initial], "now" being [now]; saves fail while [fails]. */
class FakeChatOpeningsRepository(
    initial: ChatOpenings = ChatOpenings(since = Instant.EPOCH),
    var now: Instant = Instant.EPOCH,
) : ChatOpeningsRepository {
    override val openings = MutableStateFlow(initial)

    var fails = false

    override suspend fun markOpened(chatId: Long): Outcome<Unit> {
        if (fails) return Outcome.Failure(DomainError.Unknown(code = 0, message = "not saved"))
        val current = openings.value
        openings.value = current.copy(lastOpened = (current.lastOpened + (chatId to now)).toImmutableMap())
        return Outcome.Success(Unit)
    }
}
