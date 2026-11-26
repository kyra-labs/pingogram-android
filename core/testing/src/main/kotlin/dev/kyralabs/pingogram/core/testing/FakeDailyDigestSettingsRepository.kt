package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

/** [DailyDigestSettingsRepository] in memory, from [initial] (the app's defaults); saves fail while [fails]. */
class FakeDailyDigestSettingsRepository(
    initial: DailyDigestSettings = DailyDigestSettings(),
) : DailyDigestSettingsRepository {
    override val settings = MutableStateFlow(initial)

    var fails = false

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = save { it.copy(enabled = enabled) }

    override suspend fun setTime(time: LocalTime): Outcome<Unit> = save { it.copy(time = time) }

    private fun save(change: (DailyDigestSettings) -> DailyDigestSettings): Outcome<Unit> {
        if (fails) return Outcome.Failure(DomainError.Unknown(code = 0, message = "not saved"))
        settings.value = change(settings.value)
        return Outcome.Success(Unit)
    }
}
