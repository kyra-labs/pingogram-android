package dev.kyralabs.pingogram.feature.settings.ui.profilecolor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.AccentColorRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "Your color" page shows (`docs/DESIGN_SYSTEM.md` §6 "Your color"). */
@Immutable
sealed interface ProfileColorUiState {
    data object Loading : ProfileColorUiState

    /**
     * The signed-in user ([userId], [name], [photo]), their name color now ([current], -1 while Telegram has not set
     * one) and the one [chosen] on the page; the built-in colors they may pick ([colors]) and those that come with
     * Telegram Premium ([premiumColors], shown as information); [isSaving] while Telegram answers.
     */
    data class Content(
        val userId: Long,
        val name: String,
        val photo: ProfilePhoto?,
        val current: Int,
        val chosen: Int,
        val colors: ImmutableList<AccentColorOption>,
        val premiumColors: ImmutableList<AccentColorOption>,
        val isSaving: Boolean = false,
    ) : ProfileColorUiState {
        val canSave: Boolean get() = !isSaving && chosen != current && chosen >= 0
    }
}

sealed interface ProfileColorEvent {
    data class Choose(
        val id: Int,
    ) : ProfileColorEvent

    data object Save : ProfileColorEvent
}

sealed interface ProfileColorEffect {
    /** Telegram took the color: the page closes with the `Confirm` haptic. */
    data object Saved : ProfileColorEffect

    data class ShowError(
        val error: DomainError,
    ) : ProfileColorEffect
}

/**
 * The user's name color (the owner's board 22b) as [uiState]: the signed-in user from [UserRepository], Telegram's
 * colors from [AccentColorRepository]; the color picked is kept in the [SavedStateHandle] until "Save" sends it
 * (`setAccentColor`). Telegram decides who may set which: its refusal is a snackbar, never worked around.
 */
@HiltViewModel
class ProfileColorViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        userRepository: UserRepository,
        private val accentColors: AccentColorRepository,
    ) : ViewModel() {
        private val chosen = MutableStateFlow(savedStateHandle.get<Int>(KEY_CHOSEN))
        private val isSaving = MutableStateFlow(false)
        private val effectChannel = Channel<ProfileColorEffect>(Channel.BUFFERED)

        /** One-off effects for the screen: closing once saved, errors. */
        val effects: Flow<ProfileColorEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<ProfileColorUiState> =
            combine(userRepository.myUserId, userRepository.users, accentColors.options, chosen, isSaving) {
                myId,
                users,
                options,
                chosen,
                saving,
                ->
                val me = myId?.let { users[it] } ?: return@combine ProfileColorUiState.Loading
                ProfileColorUiState.Content(
                    userId = me.id,
                    name = listOf(me.firstName, me.lastName).filter { it.isNotBlank() }.joinToString(" "),
                    photo = me.profilePhoto,
                    current = me.accentColorId,
                    chosen = chosen ?: me.accentColorId,
                    colors = options.filter { it.isBuiltIn }.toImmutableList(),
                    premiumColors = options.filterNot { it.isBuiltIn }.toImmutableList(),
                    isSaving = saving,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileColorUiState.Loading)

        fun onEvent(event: ProfileColorEvent) {
            when (event) {
                is ProfileColorEvent.Choose -> choose(event.id)
                ProfileColorEvent.Save -> save()
            }
        }

        private fun choose(id: Int) {
            val content = uiState.value as? ProfileColorUiState.Content ?: return
            if (content.isSaving || content.colors.none { it.id == id }) return
            chosen.value = id
            savedStateHandle[KEY_CHOSEN] = id
        }

        private fun save() {
            val content = uiState.value as? ProfileColorUiState.Content ?: return
            if (!content.canSave) return
            isSaving.value = true
            viewModelScope.launch {
                val outcome = accentColors.setAccentColor(content.chosen)
                isSaving.value = false
                when (outcome) {
                    is Outcome.Success -> {
                        effectChannel.trySend(ProfileColorEffect.Saved)
                    }

                    is Outcome.Failure -> {
                        if (outcome.error != DomainError.Silent) {
                            effectChannel.trySend(ProfileColorEffect.ShowError(outcome.error))
                        }
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val KEY_CHOSEN = "profileColor.chosen"
        }
    }
