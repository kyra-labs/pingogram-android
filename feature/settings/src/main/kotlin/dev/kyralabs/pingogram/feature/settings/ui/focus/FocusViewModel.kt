package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.focus.FocusClock
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Focus page (board 31): the profiles, and which is on now, worked out from the clock each minute while the page
 * shows ([WallClock.minutes]), so nothing needs an alarm. A profile's switch turns it on by hand, or Focus off, until
 * the schedule's next change (owner decision 2026-11-15); a change that fails to save leaves things as they were, and a
 * snackbar says why.
 */
@HiltViewModel
class FocusViewModel
    @Inject
    constructor(
        private val repository: FocusRepository,
        private val clock: WallClock,
    ) : ViewModel() {
        private val effectChannel = Channel<FocusEffect>(Channel.BUFFERED)
        val effects: Flow<FocusEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<FocusUiState> =
            combine(repository.profiles, repository.manual, clock.minutes) { profiles, manual, now ->
                val on = FocusClock.active(profiles, manual, now, clock.zone())?.profile?.id
                FocusUiState(
                    profiles
                        .map { profile ->
                            FocusProfileItem(
                                id = profile.id,
                                name = profile.name,
                                icon = profile.icon,
                                schedule = profile.schedule,
                                folderCount = profile.allowedFolderIds.size,
                                chatCount = profile.allowedChatIds.size,
                                allowsCalls = profile.allowsCallsFromContacts,
                                isOn = profile.id == on,
                            )
                        }.toImmutableList(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FocusUiState())

        fun onEvent(event: FocusEvent) {
            when (event) {
                is FocusEvent.Toggle -> {
                    val manual =
                        ManualFocus(
                            profileId = event.id.takeIf { event.on },
                            setAt = clock.now().epochSecond,
                            until = FocusUntil.ScheduleChange,
                        )
                    viewModelScope.launch {
                        val saved = repository.setManual(manual)
                        if (saved is Outcome.Failure) effectChannel.trySend(FocusEffect.ShowError(saved.error))
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
