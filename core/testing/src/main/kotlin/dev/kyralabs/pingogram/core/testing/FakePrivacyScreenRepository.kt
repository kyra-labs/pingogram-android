package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [PrivacyScreenRepository] in memory, off at first as in the app. */
class FakePrivacyScreenRepository(
    enabled: Boolean = false,
) : PrivacyScreenRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
