package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.focus.FocusClock
import dev.kyralabs.pingogram.core.domain.model.ActiveFocus
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * The chat list's Focus (`docs/DESIGN_SYSTEM.md` §13.13, board 31), apart from [dev.kyralabs.pingogram.feature.chats
 * .ui.ChatListViewModel] so a change recomposes the header alone: the pill while a profile is on and Settings → Focus
 * lets the header show it, and the switcher it opens (open in the saved state). Which profile is on is worked out from
 * the clock each minute while the chat list shows ([WallClock.minutes]); nothing sets an alarm.
 *
 * A profile chosen in the switcher, or Off, is set by hand for as long as "Keep it on" says: until the schedule's next
 * change, an hour, or until turned off. Changing that keeps the current choice for the new time. A change that fails
 * leaves things as they were, and a snackbar says why.
 */
@HiltViewModel
class FocusSwitchViewModel
    @Inject
    constructor(
        private val repository: FocusRepository,
        private val clock: WallClock,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val effectChannel = Channel<FocusSwitchEffect>(Channel.BUFFERED)
        val effects: Flow<FocusSwitchEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<FocusSwitchUiState> =
            combine(
                repository.profiles,
                repository.manual,
                repository.showsInChatList,
                clock.minutes,
                savedStateHandle.getStateFlow(KEY_OPEN, false),
            ) { profiles, manual, showsInChatList, now, open ->
                val active = FocusClock.active(profiles, manual, now, clock.zone())
                FocusSwitchUiState(
                    pill =
                        active?.takeIf { showsInChatList }?.let {
                            FocusPill(
                                it.profile.name,
                                it.profile.icon,
                                it.endsAt,
                            )
                        },
                    switcherOpen = open,
                    profiles = profiles.map { switchProfile(it, active, now) }.toImmutableList(),
                    keep = keepOf(manual, profiles, active, now),
                    scheduleChangeAt = FocusClock.nextChange(profiles, now.epochSecond, clock.zone()),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FocusSwitchUiState())

        fun onEvent(event: FocusSwitchEvent) {
            when (event) {
                FocusSwitchEvent.Open -> savedStateHandle[KEY_OPEN] = true
                FocusSwitchEvent.Close -> savedStateHandle[KEY_OPEN] = false
                is FocusSwitchEvent.Choose -> set { _, keep -> event.profileId to keep }
                is FocusSwitchEvent.Keep -> set { on, _ -> on to event.keep }
            }
        }

        /** Sets Focus by hand: [choice] gets the profile on now and the current "Keep it on", and returns what to set. */
        private fun set(choice: (on: Long?, keep: FocusKeep) -> Pair<Long?, FocusKeep>) {
            viewModelScope.launch {
                val now = clock.now()
                val profiles = repository.profiles.first()
                val manual = repository.manual.first()
                val active = FocusClock.active(profiles, manual, now, clock.zone())
                val (profileId, keep) = choice(active?.profile?.id, keepOf(manual, profiles, active, now))
                val hasChanges = FocusClock.nextChange(profiles, now.epochSecond, clock.zone()) != null
                val until =
                    when (keep) {
                        FocusKeep.OneHour -> FocusUntil.Time(now.epochSecond + HOUR_SECONDS)
                        FocusKeep.TurnedOff -> FocusUntil.TurnedOff
                        FocusKeep.ScheduleChange -> if (hasChanges) FocusUntil.ScheduleChange else FocusUntil.TurnedOff
                    }
                val saved = repository.setManual(ManualFocus(profileId, now.epochSecond, until))
                if (saved is Outcome.Failure) effectChannel.trySend(FocusSwitchEffect.ShowError(saved.error))
            }
        }

        private fun switchProfile(
            profile: FocusProfile,
            active: ActiveFocus?,
            now: Instant,
        ): FocusSwitchProfile {
            val isOn = active?.profile?.id == profile.id
            val ends = active?.endsAt
            val status =
                when {
                    isOn && !active.byHand && ends != null -> {
                        FocusProfileStatus.UntilScheduleEnds(ends)
                    }

                    isOn && ends != null -> {
                        FocusProfileStatus.Until(ends)
                    }

                    isOn -> {
                        FocusProfileStatus.UntilTurnedOff
                    }

                    else -> {
                        profile.schedule
                            ?.let { FocusClock.nextStart(it, now, clock.zone()) }
                            ?.let(FocusProfileStatus::StartsAt) ?: FocusProfileStatus.ByHand
                    }
                }
            return FocusSwitchProfile(profile.id, profile.name, profile.icon, isOn, status)
        }

        /**
         * How long the current choice lasts, as "Keep it on" shows it: what Focus set by hand ([manual]) says while it
         * holds (a profile on by hand, or Focus off by hand), else the schedule decides until its next change.
         */
        private fun keepOf(
            manual: ManualFocus?,
            profiles: List<FocusProfile>,
            active: ActiveFocus?,
            now: Instant,
        ): FocusKeep {
            val holds =
                manual != null &&
                    (active?.byHand == true || (active == null && manual.profileId == null)) &&
                    FocusClock.endOf(manual, profiles, clock.zone()).let { it == null || now.epochSecond < it }
            return when (manual?.until?.takeIf { holds }) {
                is FocusUntil.Time -> FocusKeep.OneHour
                FocusUntil.TurnedOff -> FocusKeep.TurnedOff
                FocusUntil.ScheduleChange, null -> FocusKeep.ScheduleChange
            }
        }

        private companion object {
            const val KEY_OPEN = "focus.switcher_open"
            const val HOUR_SECONDS = 3_600L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
