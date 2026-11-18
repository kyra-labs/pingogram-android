package dev.kyralabs.pingogram.feature.settings.ui.snippets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.SnippetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Snippets page shows. */
@Immutable
sealed interface SnippetsUiState {
    data object Loading : SnippetsUiState

    /** The snippets in the user's order. */
    data class Content(
        val snippets: ImmutableList<Snippet>,
    ) : SnippetsUiState
}

/** What the user does on the page, handled by [SnippetsViewModel.onEvent]. */
sealed interface SnippetsEvent {
    /** The order a drag left the snippets in. */
    data class Reorder(
        val ids: List<Long>,
    ) : SnippetsEvent

    /** Moves a snippet one place up ([up]) or down: screen readers' "Move up" and "Move down". */
    data class Move(
        val id: Long,
        val up: Boolean,
    ) : SnippetsEvent
}

sealed interface SnippetsEffect {
    data class ShowError(
        val error: DomainError,
    ) : SnippetsEffect
}

/**
 * Settings → Appearance → Chats → "Snippets" (`REQUIREMENTS.md` FR-X2): the account's snippets ([SnippetRepository]),
 * kept on this device, in the order the composer offers them, which a drag or a screen reader's move changes at once.
 */
@HiltViewModel
class SnippetsViewModel
    @Inject
    constructor(
        private val repository: SnippetRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<SnippetsEffect>(Channel.BUFFERED)
        val effects: Flow<SnippetsEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<SnippetsUiState> =
            repository.snippets
                .map { SnippetsUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SnippetsUiState.Loading)

        fun onEvent(event: SnippetsEvent) {
            when (event) {
                is SnippetsEvent.Reorder -> reorder(event.ids)
                is SnippetsEvent.Move -> move(event.id, event.up)
            }
        }

        private fun reorder(ids: List<Long>) {
            viewModelScope.launch {
                val outcome = repository.reorder(ids)
                if (outcome is Outcome.Failure) effectChannel.send(SnippetsEffect.ShowError(outcome.error))
            }
        }

        private fun move(
            id: Long,
            up: Boolean,
        ) {
            val ids = (uiState.value as? SnippetsUiState.Content)?.snippets?.map { it.id } ?: return
            val index = ids.indexOf(id)
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            reorder(ids.toMutableList().apply { add(target, removeAt(index)) })
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
