package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [AutoDownloadRepository] in memory, with the defaults unless [initial]; [failure] makes saving fail. */
class FakeAutoDownloadRepository(
    initial: AutoDownloadSettings = AutoDownloadSettings(),
) : AutoDownloadRepository {
    override val settings = MutableStateFlow(initial)

    /** What saving fails with; null saves. */
    var failure: DomainError? = null

    override suspend fun setRules(
        network: AutoDownloadNetwork,
        rules: AutoDownloadRules,
    ): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = settings.value.with(network, rules)
        return Outcome.Success(Unit)
    }
}
