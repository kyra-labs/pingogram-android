package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [ComposerSettingsRepository] in memory: off at first ([initial]), [failure] making the setter fail. */
class FakeComposerSettingsRepository(
    initial: Boolean = false,
) : ComposerSettingsRepository {
    private val saved = MutableStateFlow(initial)

    /** What the setter fails with; null saves. */
    var failure: DomainError? = null

    override val sendWithCtrlEnter: Flow<Boolean> = saved

    override suspend fun setSendWithCtrlEnter(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        saved.value = enabled
        return Outcome.Success(Unit)
    }
}
