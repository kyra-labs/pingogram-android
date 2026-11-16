package dev.kyralabs.pingogram.core.domain.focus

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.coroutines.flow.first

/** What [FocusToggle.toggle] did. */
sealed interface FocusToggleResult {
    /** Focus is on with the profile [profileId] now, or off (null). */
    data class Done(
        val profileId: Long?,
    ) : FocusToggleResult

    /** There is no profile to turn on: the user makes one first. */
    data object NoProfiles : FocusToggleResult

    data class Failed(
        val error: DomainError,
    ) : FocusToggleResult
}

/**
 * Turns Focus on or off in one tap, as the Quick Settings tile does (board 31, owner decision 2026-11-15): off while a
 * profile is on, else the profile used last (the first profile before any was) on. Either holds until the schedule's
 * next change, or until turned off where no profile has a schedule, as the switcher's "Keep it on" starts.
 */
class FocusToggle(
    private val repository: FocusRepository,
    private val clock: WallClock,
) {
    suspend fun toggle(): FocusToggleResult {
        val profiles = repository.profiles.first()
        if (profiles.isEmpty()) return FocusToggleResult.NoProfiles
        val now = clock.now()
        val zone = clock.zone()
        val active = FocusClock.active(profiles, repository.manual.first(), now, zone)
        val profileId =
            if (active != null) {
                null
            } else {
                val lastUsed = repository.lastUsedId.first()
                (profiles.firstOrNull { it.id == lastUsed } ?: profiles.first()).id
            }
        val until =
            if (FocusClock.nextChange(profiles, now.epochSecond, zone) != null) {
                FocusUntil.ScheduleChange
            } else {
                FocusUntil.TurnedOff
            }
        return when (val saved = repository.setManual(ManualFocus(profileId, now.epochSecond, until))) {
            is Outcome.Success -> FocusToggleResult.Done(profileId)
            is Outcome.Failure -> FocusToggleResult.Failed(saved.error)
        }
    }
}
