package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The profile on now as the chat list's header shows it (board 31): its [name] and [icon], and when it ends ([endsAt],
 * Unix seconds), or null while it lasts until turned off.
 */
@Immutable
data class FocusPill(
    val name: String,
    val icon: FocusIcon,
    val endsAt: Long?,
) {
    override fun toString() = "FocusPill(icon=$icon, endsAt=$endsAt, …)"
}

/** What a profile's row in the switcher says under its name. */
@Immutable
sealed interface FocusProfileStatus {
    /** On by its schedule until [at]. */
    data class UntilScheduleEnds(
        val at: Long,
    ) : FocusProfileStatus

    /** On by hand until [at]. */
    data class Until(
        val at: Long,
    ) : FocusProfileStatus

    /** On by hand until the user turns it off. */
    data object UntilTurnedOff : FocusProfileStatus

    /** Off; its schedule next starts at [at]. */
    data class StartsAt(
        val at: Long,
    ) : FocusProfileStatus

    /** Off, without a schedule: it runs only when turned on by hand. */
    data object ByHand : FocusProfileStatus
}

/** A profile as the switcher lists it. */
@Immutable
data class FocusSwitchProfile(
    val id: Long,
    val name: String,
    val icon: FocusIcon,
    val isOn: Boolean,
    val status: FocusProfileStatus,
) {
    override fun toString() = "FocusSwitchProfile(id=$id, isOn=$isOn, status=$status, …)"
}

/** How long a choice in the switcher lasts ("Keep it on"). */
enum class FocusKeep {
    /** Until the schedule's next change. */
    ScheduleChange,

    /** For an hour. */
    OneHour,

    /** Until the user turns it off. */
    TurnedOff,
}

/**
 * The chat list's Focus: the header's [pill] while a profile is on and the header shows it, and the switcher, open
 * while [switcherOpen]: the [profiles] (none on means Off is chosen), how long a choice lasts ([keep]), and when the
 * schedule next changes ([scheduleChangeAt], Unix seconds; null without schedules, when "Until …" is not offered).
 */
@Immutable
data class FocusSwitchUiState(
    val pill: FocusPill? = null,
    val switcherOpen: Boolean = false,
    val profiles: ImmutableList<FocusSwitchProfile> = persistentListOf(),
    val keep: FocusKeep = FocusKeep.ScheduleChange,
    val scheduleChangeAt: Long? = null,
)

/** What the user does with the chat list's Focus, handled by [FocusSwitchViewModel.onEvent]. */
sealed interface FocusSwitchEvent {
    /** Opens the switcher (a tap on the header's pill). */
    data object Open : FocusSwitchEvent

    /** Closes it. */
    data object Close : FocusSwitchEvent

    /** Turns profile [profileId] on, or Focus off (null), for as long as [FocusSwitchUiState.keep] says. */
    data class Choose(
        val profileId: Long?,
    ) : FocusSwitchEvent

    /** Keeps the choice for [keep]. */
    data class Keep(
        val keep: FocusKeep,
    ) : FocusSwitchEvent
}

/** One-off side effects of [FocusSwitchViewModel], handled once by the chat list's route. */
sealed interface FocusSwitchEffect {
    data class ShowError(
        val error: DomainError,
    ) : FocusSwitchEffect
}
