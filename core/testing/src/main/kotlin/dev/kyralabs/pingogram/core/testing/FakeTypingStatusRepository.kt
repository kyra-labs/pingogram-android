package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.TypingStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [TypingStatusRepository] in memory, on at first as in the app. */
class FakeTypingStatusRepository(
    enabled: Boolean = true,
) : TypingStatusRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
