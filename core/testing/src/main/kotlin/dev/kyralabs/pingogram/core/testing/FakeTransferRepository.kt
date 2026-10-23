package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [TransferRepository] in memory: set [transfers] directly; [cancelAll] counts its calls and ends every transfer. */
class FakeTransferRepository(
    initial: Transfers = Transfers.None,
) : TransferRepository {
    override val transfers = MutableStateFlow(initial)

    /** How often [cancelAll] was called. */
    var cancelled = 0
        private set

    override suspend fun cancelAll(): Outcome<Unit> {
        cancelled++
        transfers.value = Transfers.None
        return Outcome.Success(Unit)
    }
}
