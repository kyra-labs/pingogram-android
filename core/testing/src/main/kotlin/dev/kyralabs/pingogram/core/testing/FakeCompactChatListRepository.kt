package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CompactChatListRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [CompactChatListRepository] in memory, off at first as in the app. */
class FakeCompactChatListRepository(
    enabled: Boolean = false,
) : CompactChatListRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
