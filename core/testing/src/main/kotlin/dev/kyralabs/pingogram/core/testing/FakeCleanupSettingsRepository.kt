package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [CleanupSettingsRepository] in memory, from [initial] (the app's defaults); saves fail while [fails]. */
class FakeCleanupSettingsRepository(
    initial: CleanupSettings = CleanupSettings(),
) : CleanupSettingsRepository {
    override val settings = MutableStateFlow(initial)

    var fails = false

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = save { it.copy(enabled = enabled) }

    override suspend fun setMonths(months: Int): Outcome<Unit> =
        save { if (months in CleanupSettings.MONTHS) it.copy(months = months) else it }

    private fun save(change: (CleanupSettings) -> CleanupSettings): Outcome<Unit> {
        if (fails) return Outcome.Failure(DomainError.Unknown(code = 0, message = "not saved"))
        settings.value = change(settings.value)
        return Outcome.Success(Unit)
    }
}
