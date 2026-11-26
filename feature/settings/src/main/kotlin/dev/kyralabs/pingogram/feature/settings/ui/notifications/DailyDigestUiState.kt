package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import java.time.LocalTime

/**
 * The Daily digest's rows on the Notifications page (`REQUIREMENTS.md` FR-X23): its [settings] once read (null until
 * then), and whether the time picker is open ([isPickingTime]).
 */
@Immutable
data class DailyDigestUiState(
    val settings: DailyDigestSettings? = null,
    val isPickingTime: Boolean = false,
)

/** What the user does with the Daily digest's rows. */
sealed interface DailyDigestEvent {
    /** "Daily digest" turned on or off. */
    data class SetEnabled(
        val enabled: Boolean,
    ) : DailyDigestEvent

    /** "Time" tapped: the time picker opens. */
    data object PickTime : DailyDigestEvent

    /** A time chosen in the picker. */
    data class SetTime(
        val time: LocalTime,
    ) : DailyDigestEvent

    /** The picker closed without a time. */
    data object DismissTime : DailyDigestEvent
}

/** One-off side effects of the Daily digest's rows, handled once by the Notifications page's route. */
sealed interface DailyDigestEffect {
    /** A setting could not be saved, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : DailyDigestEffect
}
