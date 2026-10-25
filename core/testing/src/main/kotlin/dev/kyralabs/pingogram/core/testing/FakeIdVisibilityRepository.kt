package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.IdVisibilityRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [IdVisibilityRepository] in memory, IDs hidden at first as in the app. */
class FakeIdVisibilityRepository(
    shown: Boolean = false,
) : IdVisibilityRepository {
    override val shown = MutableStateFlow(shown)

    override suspend fun setShown(shown: Boolean): Outcome<Unit> {
        this.shown.value = shown
        return Outcome.Success(Unit)
    }
}
