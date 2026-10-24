package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the power saving page shows.
 *
 * @property mode when power saving is on; null until the settings are read.
 * @property parts what it turns off while on.
 * @property isBatterySaverOn whether Android's Battery Saver is on now, which "With Battery Saver" follows.
 */
@Immutable
data class PowerSavingUiState(
    val mode: PowerSavingMode? = null,
    val parts: ImmutableSet<PowerSavingPart> = persistentSetOf(),
    val isBatterySaverOn: Boolean = false,
) {
    /** Whether power saving is on now. */
    val isOn: Boolean
        get() = mode == PowerSavingMode.Always || (mode == PowerSavingMode.WithBatterySaver && isBatterySaverOn)
}

/** What the user does on the power saving page, handled by [PowerSavingViewModel.onEvent]. */
sealed interface PowerSavingEvent {
    /** A mode was picked. */
    data class SetMode(
        val mode: PowerSavingMode,
    ) : PowerSavingEvent

    /** A part's switch was turned: power saving turns [part] off while on ([off]), or leaves it. */
    data class SetPart(
        val part: PowerSavingPart,
        val off: Boolean,
    ) : PowerSavingEvent
}

/** One-off side effects of [PowerSavingViewModel], handled once by its route. */
sealed interface PowerSavingEffect {
    /** The change could not be saved, shown in a snackbar; the page shows what was saved before. */
    data class ShowError(
        val error: DomainError,
    ) : PowerSavingEffect
}
