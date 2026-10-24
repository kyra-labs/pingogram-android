package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [PhoneNumberVisibilityRepository] in memory, numbers shown at first as in the app. */
class FakePhoneNumberVisibilityRepository(
    hidden: Boolean = false,
) : PhoneNumberVisibilityRepository {
    override val hidden = MutableStateFlow(hidden)

    override suspend fun setHidden(hidden: Boolean): Outcome<Unit> {
        this.hidden.value = hidden
        return Outcome.Success(Unit)
    }
}
