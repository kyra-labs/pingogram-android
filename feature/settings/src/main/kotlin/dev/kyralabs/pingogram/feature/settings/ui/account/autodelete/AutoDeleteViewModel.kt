package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The self-destruct timer of new chats (`setDefaultMessageAutoDeleteTime`): Telegram's choices (off, a day, a week, a
 * month) and a value another app set, saved as soon as one is picked; a refusal brings the previous one back with a
 * snackbar.
 */
@HiltViewModel
class AutoDeleteViewModel
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<AutoDeleteUiState>(AutoDeleteUiState.Loading)
        val uiState: StateFlow<AutoDeleteUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<AutoDeleteEffect>(Channel.BUFFERED)
        val effects: Flow<AutoDeleteEffect> = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: AutoDeleteEvent) {
            when (event) {
                AutoDeleteEvent.Retry -> load()
                is AutoDeleteEvent.Select -> select(event.seconds)
            }
        }

        private fun load() {
            mutableUiState.value = AutoDeleteUiState.Loading
            viewModelScope.launch {
                mutableUiState.value =
                    when (val outcome = repository.defaultMessageAutoDeleteTime()) {
                        is Outcome.Success -> AutoDeleteUiState.Content(outcome.value, optionsWith(outcome.value))
                        is Outcome.Failure -> AutoDeleteUiState.Error(outcome.error)
                    }
            }
        }

        private fun select(seconds: Int) {
            val content = mutableUiState.value as? AutoDeleteUiState.Content ?: return
            if (content.isSaving || seconds == content.seconds) return
            mutableUiState.value = content.copy(seconds = seconds, isSaving = true)
            viewModelScope.launch {
                val outcome = repository.setDefaultMessageAutoDeleteTime(seconds)
                mutableUiState.update { state ->
                    val now = state as? AutoDeleteUiState.Content ?: return@update state
                    if (outcome is Outcome.Failure) {
                        now.copy(seconds = content.seconds, isSaving = false)
                    } else {
                        now.copy(isSaving = false)
                    }
                }
                val error = (outcome as? Outcome.Failure)?.error
                if (error != null &&
                    error != DomainError.Silent
                ) {
                    effectChannel.trySend(AutoDeleteEffect.ShowError(error))
                }
            }
        }

        internal companion object {
            /** Telegram's choices: off, a day, a week and a month (31 days), as its apps offer them. */
            val Choices = listOf(0, 86_400, 604_800, 2_678_400)

            fun optionsWith(seconds: Int) = (if (seconds in Choices) Choices else Choices + seconds).toImmutableList()
        }
    }
