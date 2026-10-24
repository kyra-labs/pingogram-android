package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.PowerSaving
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingPolicy
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** [PowerSavingPolicy] from the settings and Battery Saver (`PowerSavingSettings.inEffect`). */
class DefaultPowerSavingPolicy
    @Inject
    constructor(
        settingsRepository: PowerSavingRepository,
        batterySaver: BatterySaverRepository,
    ) : PowerSavingPolicy {
        override val inEffect: Flow<PowerSaving> =
            combine(settingsRepository.settings, batterySaver.isOn) { settings, on -> settings.inEffect(on) }
                .distinctUntilChanged()
    }
