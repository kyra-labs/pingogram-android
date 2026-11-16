package dev.kyralabs.pingogram.feature.chat.ui.suggested

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.SuggestedPostRepository
import dev.kyralabs.pingogram.feature.chat.ui.SuggestedPostEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The answers of a channel's administrators to the posts suggested in chat [chatId], its direct messages
 * (`docs/DESIGN_SYSTEM.md` §6 "Suggested posts"): "Approve" or "Decline" on a card asks first ([dialog]), declining
 * with an optional comment for the post's author ([comment]); only "Publish" or "Decline" there sends the answer
 * (`approveSuggestedPost`, `declineSuggestedPost`). TDLib then reports the post's new state, which its card shows.
 * The open question survives process death; the comment typed does not.
 */
@HiltViewModel(assistedFactory = SuggestedPostsViewModel.Factory::class)
class SuggestedPostsViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        private val repository: SuggestedPostRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): SuggestedPostsViewModel
        }

        private val mutableDialog =
            MutableStateFlow(
                savedStateHandle.get<Long>(KEY_MESSAGE)?.let { messageId ->
                    SuggestedPostDialog(messageId, approve = savedStateHandle[KEY_APPROVE] ?: true)
                },
            )

        /** The question before answering; null while none shows. */
        val dialog: StateFlow<SuggestedPostDialog?> = mutableDialog.asStateFlow()

        /** The comment a decline sends its post's author; empty for none. */
        val comment = TextFieldState()

        fun onEvent(event: SuggestedPostEvent) {
            when (event) {
                is SuggestedPostEvent.Answer -> open(event.messageId, event.approve)
                SuggestedPostEvent.Confirm -> confirm()
                SuggestedPostEvent.Dismiss -> close()
            }
        }

        private fun open(
            messageId: Long,
            approve: Boolean,
        ) {
            comment.edit { replace(0, length, "") }
            mutableDialog.value = SuggestedPostDialog(messageId, approve)
            savedStateHandle[KEY_MESSAGE] = messageId
            savedStateHandle[KEY_APPROVE] = approve
        }

        private fun confirm() {
            val current = mutableDialog.value?.takeIf { !it.isWorking } ?: return
            mutableDialog.value = current.copy(isWorking = true, error = null)
            viewModelScope.launch {
                val result =
                    if (current.approve) {
                        repository.approve(chatId, current.messageId)
                    } else {
                        repository.decline(chatId, current.messageId, comment.text.toString())
                    }
                when (result) {
                    is Outcome.Success -> clear()
                    is Outcome.Failure -> mutableDialog.value = current.copy(isWorking = false, error = result.error)
                }
            }
        }

        /** Closes the question, unless Telegram is answering it. */
        private fun close() {
            if (mutableDialog.value?.isWorking != true) clear()
        }

        private fun clear() {
            mutableDialog.value = null
            savedStateHandle.remove<Long>(KEY_MESSAGE)
            savedStateHandle.remove<Boolean>(KEY_APPROVE)
        }

        private companion object {
            const val KEY_MESSAGE = "suggestedPost.messageId"
            const val KEY_APPROVE = "suggestedPost.approve"
        }
    }

/**
 * The question before answering the post of message [messageId]: publishing it ([approve]) or declining it; while
 * Telegram answers ([isWorking]); why it refused ([error]).
 */
@Immutable
data class SuggestedPostDialog(
    val messageId: Long,
    val approve: Boolean,
    val isWorking: Boolean = false,
    val error: DomainError? = null,
)
