package dev.kyralabs.pingogram.feature.settings.ui.personal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PersonalChannelRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Personal channel page shows. */
@Immutable
sealed interface PersonalChannelUiState {
    data object Loading : PersonalChannelUiState

    data class Failed(
        val error: DomainError,
    ) : PersonalChannelUiState

    /**
     * The [channels] the user may show, the one shown ([chosen], 0 for none, a choice on its way shown at once), and
     * whether a choice is on its way to Telegram ([isSaving]).
     */
    data class Content(
        val channels: ImmutableList<PersonalChannelItem>,
        val chosen: Long,
        val isSaving: Boolean = false,
    ) : PersonalChannelUiState
}

/** A channel the user owns, by its chat [id] and [title]. */
@Immutable
data class PersonalChannelItem(
    val id: Long,
    val title: String,
)

sealed interface PersonalChannelEvent {
    data object Retry : PersonalChannelEvent

    /** Show channel [chatId] on the profile; 0 for none. */
    data class Choose(
        val chatId: Long,
    ) : PersonalChannelEvent
}

sealed interface PersonalChannelEffect {
    data class ShowError(
        val error: DomainError,
    ) : PersonalChannelEffect
}

/**
 * Edit profile → Personal channel (`docs/audit/feature-gaps-octogram.md`, P2): which of the user's public channels
 * their profile shows ([PersonalChannelRepository]), or none. A choice shows
 * at once and is sent; a refusal takes it back with a snackbar. Nothing to restore after process death: it is read
 * again.
 */
@HiltViewModel
class PersonalChannelViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val personalChannelRepository: PersonalChannelRepository,
        private val chatRepository: ChatRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<PersonalChannelUiState>(PersonalChannelUiState.Loading)
        val uiState: StateFlow<PersonalChannelUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<PersonalChannelEffect>(Channel.BUFFERED)
        val effects: Flow<PersonalChannelEffect> = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: PersonalChannelEvent) {
            when (event) {
                PersonalChannelEvent.Retry -> if (uiState.value is PersonalChannelUiState.Failed) load()
                is PersonalChannelEvent.Choose -> choose(event.chatId)
            }
        }

        private fun load() {
            mutableUiState.value = PersonalChannelUiState.Loading
            viewModelScope.launch {
                val profile = profileRepository.editableProfile()
                val candidates = personalChannelRepository.candidates()
                mutableUiState.value =
                    when {
                        profile is Outcome.Failure -> {
                            PersonalChannelUiState.Failed(profile.error)
                        }

                        candidates is Outcome.Failure -> {
                            PersonalChannelUiState.Failed(candidates.error)
                        }

                        else -> {
                            val chosen = (profile as Outcome.Success).value.personalChatId
                            val ids = (candidates as Outcome.Success).value
                            PersonalChannelUiState.Content(channels(ids, chosen), chosen)
                        }
                    }
            }
        }

        /** The channels of [ids] TDLib knows, and [chosen] first if it is not among them (it may have changed). */
        private suspend fun channels(
            ids: List<Long>,
            chosen: Long,
        ): ImmutableList<PersonalChannelItem> {
            val all = if (chosen != 0L && chosen !in ids) listOf(chosen) + ids else ids
            return all
                .mapNotNull { id -> chatRepository.chat(id).first()?.let { PersonalChannelItem(id, it.title) } }
                .toImmutableList()
        }

        private fun choose(chatId: Long) {
            val content = uiState.value as? PersonalChannelUiState.Content ?: return
            if (content.isSaving || chatId == content.chosen) return
            mutableUiState.value = content.copy(chosen = chatId, isSaving = true)
            viewModelScope.launch {
                val saved = personalChannelRepository.setPersonalChannel(chatId)
                mutableUiState.update { state ->
                    val current = state as? PersonalChannelUiState.Content ?: return@update state
                    if (saved is Outcome.Failure) {
                        current.copy(chosen = content.chosen, isSaving = false)
                    } else {
                        current.copy(isSaving = false)
                    }
                }
                if (saved is Outcome.Failure && saved.error != DomainError.Silent) {
                    effectChannel.trySend(PersonalChannelEffect.ShowError(saved.error))
                }
            }
        }
    }
