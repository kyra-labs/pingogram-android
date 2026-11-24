package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.SharePhoneNumberRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [SharePhoneNumberRepository] in memory, off at first as in the app. */
class FakeSharePhoneNumberRepository(
    enabled: Boolean = false,
) : SharePhoneNumberRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
