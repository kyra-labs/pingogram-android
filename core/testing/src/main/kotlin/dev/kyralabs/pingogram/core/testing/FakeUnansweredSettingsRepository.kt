package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [UnansweredSettingsRepository] in memory, starting with [initial] (the app's defaults). */
class FakeUnansweredSettingsRepository(
    initial: UnansweredSettings = UnansweredSettings(),
) : UnansweredSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setShowButton(show: Boolean): Outcome<Unit> {
        settings.value = settings.value.copy(showButton = show)
        return Outcome.Success(Unit)
    }

    override suspend fun setAfterDays(days: Int): Outcome<Unit> {
        if (days in UnansweredSettings.DAYS) settings.value = settings.value.copy(afterDays = days)
        return Outcome.Success(Unit)
    }
}
