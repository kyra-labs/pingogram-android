package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
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
 * Who may start a chat with the user: everybody, or contacts and Telegram Premium subscribers. The second can be chosen
 * only where Telegram lets the account (`NewChatPrivacy.canRestrict`); otherwise the page says so, and offers nothing
 * to buy. A choice shows at once and is saved; a refusal brings the previous
 * one back with a snackbar.
 */
@HiltViewModel
class NewChatPrivacyViewModel
    @Inject
    constructor(
        private val repository: PrivacyRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<NewChatPrivacyUiState>(NewChatPrivacyUiState.Loading)
        val uiState: StateFlow<NewChatPrivacyUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<NewChatPrivacyEffect>(Channel.BUFFERED)
        val effects: Flow<NewChatPrivacyEffect> = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: NewChatPrivacyEvent) {
            when (event) {
                NewChatPrivacyEvent.Retry -> load()
                is NewChatPrivacyEvent.Select -> select(event.allows)
            }
        }

        private fun load() {
            mutableUiState.value = NewChatPrivacyUiState.Loading
            viewModelScope.launch {
                mutableUiState.value =
                    when (val outcome = repository.newChatPrivacy()) {
                        is Outcome.Success -> NewChatPrivacyUiState.Content(outcome.value)
                        is Outcome.Failure -> NewChatPrivacyUiState.Error(outcome.error)
                    }
            }
        }

        private fun select(allows: Boolean) {
            val content = mutableUiState.value as? NewChatPrivacyUiState.Content ?: return
            val before = content.privacy
            val allowed = allows || before.canRestrict
            if (content.isSaving || allows == before.allowsNonContacts || !allowed) return
            val stars = if (allows) before.paidMessageStars else 0
            val chosen = before.copy(allowsNonContacts = allows, paidMessageStars = stars)
            mutableUiState.value = content.copy(privacy = chosen, isSaving = true)
            viewModelScope.launch {
                val outcome = repository.setAllowsNonContacts(allows)
                mutableUiState.update { state ->
                    val now = state as? NewChatPrivacyUiState.Content ?: return@update state
                    now.copy(privacy = if (outcome is Outcome.Failure) before else now.privacy, isSaving = false)
                }
                val error = (outcome as? Outcome.Failure)?.error
                if (error != null && error != DomainError.Silent) {
                    effectChannel.trySend(NewChatPrivacyEffect.ShowError(error))
                }
            }
        }
    }
