package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AccountNotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [AccountNotificationsRepository] in memory, on at first unless [initial]; [failure] makes saving fail. */
class FakeAccountNotificationsRepository(
    initial: Boolean = true,
) : AccountNotificationsRepository {
    override val fromAllAccounts = MutableStateFlow(initial)

    /** What saving fails with; null saves. */
    var failure: DomainError? = null

    override suspend fun setFromAllAccounts(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        fromAllAccounts.value = enabled
        return Outcome.Success(Unit)
    }
}
