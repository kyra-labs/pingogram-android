package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [DoubleTapSettingsRepository] in memory: [initial] at first, [failure] making the setter fail. */
class FakeDoubleTapSettingsRepository(
    initial: DoubleTapReactions = DoubleTapReactions(),
) : DoubleTapSettingsRepository {
    private val saved = MutableStateFlow(initial)

    /** What the setter fails with; null saves. */
    var failure: DomainError? = null

    override val doubleTap: Flow<DoubleTapReactions> = saved

    override suspend fun setDoubleTap(doubleTap: DoubleTapReactions): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        saved.value = doubleTap
        return Outcome.Success(Unit)
    }
}
