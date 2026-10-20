package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [KeepConnectedRepository] in memory, off at first unless [initial]; [failure] makes saving fail. */
class FakeKeepConnectedRepository(
    initial: Boolean = false,
) : KeepConnectedRepository {
    override val keepConnected = MutableStateFlow(initial)

    /** What saving fails with; null saves. */
    var failure: DomainError? = null

    override suspend fun setKeepConnected(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        keepConnected.value = enabled
        return Outcome.Success(Unit)
    }
}
