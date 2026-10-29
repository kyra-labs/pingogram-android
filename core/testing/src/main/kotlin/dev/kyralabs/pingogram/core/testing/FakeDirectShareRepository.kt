package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.DirectShareRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [DirectShareRepository] in memory, chats offered at first as in the app. */
class FakeDirectShareRepository(
    enabled: Boolean = true,
) : DirectShareRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
