package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ExperimentalUiRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [ExperimentalUiRepository] in memory, off at first as in the app; with [failure] set, saving fails with it. */
class FakeExperimentalUiRepository(
    enabled: Boolean = false,
) : ExperimentalUiRepository {
    override val enabled = MutableStateFlow(enabled)

    var failure: DomainError? = null

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
