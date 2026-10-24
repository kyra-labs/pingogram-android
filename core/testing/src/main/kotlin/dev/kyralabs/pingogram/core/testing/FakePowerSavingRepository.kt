package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PowerSaving
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.domain.model.PowerSavingSettings
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingPolicy
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [PowerSavingRepository] in memory, starting at [initial]; [failure] makes the setters fail. */
class FakePowerSavingRepository(
    initial: PowerSavingSettings = PowerSavingSettings(),
) : PowerSavingRepository {
    /** The saved settings; tests may set them. */
    val saved = MutableStateFlow(initial)

    /** What the setters fail with; null saves. */
    var failure: DomainError? = null

    override val settings: Flow<PowerSavingSettings> = saved

    override suspend fun setMode(mode: PowerSavingMode): Outcome<Unit> = save { it.copy(mode = mode) }

    override suspend fun setPart(
        part: PowerSavingPart,
        off: Boolean,
    ): Outcome<Unit> = save { it.copy(parts = if (off) it.parts + part else it.parts - part) }

    private fun save(change: (PowerSavingSettings) -> PowerSavingSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        saved.value = change(saved.value)
        return Outcome.Success(Unit)
    }
}

/** [BatterySaverRepository] whose state tests set. */
class FakeBatterySaverRepository(
    on: Boolean = false,
) : BatterySaverRepository {
    override val isOn = MutableStateFlow(on)
}

/** [PowerSavingPolicy] whose parts in effect tests set; off at first. */
class FakePowerSavingPolicy(
    initial: PowerSaving = PowerSaving.None,
) : PowerSavingPolicy {
    override val inEffect = MutableStateFlow(initial)
}
