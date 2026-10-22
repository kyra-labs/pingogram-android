package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatBoostStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatBoostRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.navigation.ChatBoostRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the boosts page of a channel or supergroup shows (`REQUIREMENTS.md` FR-P5, read only). */
@Immutable
sealed interface ChatBoostUiState {
    data object Loading : ChatBoostUiState

    data class Error(
        val error: DomainError,
    ) : ChatBoostUiState

    /** The chat's [status]; [isChannel] for the words ("subscribers", "this channel"). */
    data class Content(
        val status: ChatBoostStatus,
        val isChannel: Boolean,
    ) : ChatBoostUiState
}

sealed interface ChatBoostEvent {
    data object Retry : ChatBoostEvent
}

/**
 * The boosts of channel or supergroup [chatId] (`REQUIREMENTS.md` FR-P5): its level, its boosts and what the next level
 * takes, from [ChatBoostRepository.status], read once when the page opens and again on "Try again". Read only: there is
 * nothing to boost with, as boosting needs Telegram Premium, which Pingogram does not offer.
 */
@HiltViewModel
class ChatBoostViewModel internal constructor(
    private val chatId: Long,
    private val boostRepository: ChatBoostRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        boostRepository: ChatBoostRepository,
        chatRepository: ChatRepository,
    ) : this(savedStateHandle.toRoute<ChatBoostRoute>().chatId, boostRepository, chatRepository)

    private val mutableUiState = MutableStateFlow<ChatBoostUiState>(ChatBoostUiState.Loading)
    val uiState: StateFlow<ChatBoostUiState> = mutableUiState.asStateFlow()

    init {
        // After the page's first frame (docs/PERFORMANCE.md §4).
        viewModelScope.launch(Dispatchers.Main) { load() }
    }

    fun onEvent(event: ChatBoostEvent) {
        when (event) {
            ChatBoostEvent.Retry -> {
                if (mutableUiState.value is ChatBoostUiState.Error) {
                    mutableUiState.value = ChatBoostUiState.Loading
                    viewModelScope.launch { load() }
                }
            }
        }
    }

    private suspend fun load() {
        val isChannel =
            when (val chat = chatRepository.getChat(chatId)) {
                is Outcome.Success -> (chat.value.type as? ChatType.Supergroup)?.isChannel == true
                is Outcome.Failure -> return showError(chat.error)
            }
        mutableUiState.value =
            when (val status = boostRepository.status(chatId)) {
                is Outcome.Success -> ChatBoostUiState.Content(status.value, isChannel)
                is Outcome.Failure -> ChatBoostUiState.Error(status.error)
            }
    }

    private fun showError(error: DomainError) {
        mutableUiState.value = ChatBoostUiState.Error(error)
    }
}
