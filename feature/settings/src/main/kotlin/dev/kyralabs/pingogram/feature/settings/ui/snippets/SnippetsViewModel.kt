package dev.kyralabs.pingogram.feature.settings.ui.snippets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.SnippetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Snippets page shows. */
@Immutable
sealed interface SnippetsUiState {
    data object Loading : SnippetsUiState

    /**
     * The snippets in the user's order; [canAdd] while there are fewer than [Snippet.MAX_SNIPPETS]; the [draft] being
     * written in the editor, and the snippet whose deletion waits for the user's yes ([deleting]).
     */
    data class Content(
        val snippets: ImmutableList<Snippet>,
        val canAdd: Boolean = snippets.size < Snippet.MAX_SNIPPETS,
        val draft: SnippetDraft? = null,
        val deleting: Snippet? = null,
    ) : SnippetsUiState
}

/** A snippet being written: a new one without an [id]; [nameError] says why its [name] cannot be saved. */
@Immutable
data class SnippetDraft(
    val id: Long?,
    val name: String,
    val text: String,
    val nameError: SnippetNameError? = null,
) {
    val canSave: Boolean get() = name.isNotEmpty() && nameError == null && text.isNotBlank()
}

/** Why a draft's name cannot be saved: not one word of letters, digits and "_", or another snippet's. */
enum class SnippetNameError { NotOneWord, Taken }

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

    /** "New snippet". */
    data object StartNew : SnippetsEvent

    /** A tap on snippet [id]: its editor. */
    data class StartEdit(
        val id: Long,
    ) : SnippetsEvent

    data class EditName(
        val name: String,
    ) : SnippetsEvent

    data class EditText(
        val text: String,
    ) : SnippetsEvent

    data object SaveDraft : SnippetsEvent

    data object CancelDraft : SnippetsEvent

    /** "Delete" in the editor of the snippet being edited: asks first. */
    data object AskDelete : SnippetsEvent

    data object ConfirmDelete : SnippetsEvent

    data object CancelDelete : SnippetsEvent
}

sealed interface SnippetsEffect {
    data class ShowError(
        val error: DomainError,
    ) : SnippetsEffect
}

/**
 * Settings → Appearance → Chats → "Snippets" (`REQUIREMENTS.md` FR-X2): the account's snippets ([SnippetRepository]),
 * kept on this device, in the order the composer offers them, which a drag or a screen reader's move changes at once.
 * The editor writes a new snippet or changes one, checking its name as it is typed, and deletes one after asking. The
 * draft and the question survive process death in the [SavedStateHandle]; a refused or failed save keeps the draft.
 */
@HiltViewModel
class SnippetsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: SnippetRepository,
    ) : ViewModel() {
        private val draftId = savedStateHandle.getStateFlow(KEY_DRAFT, NO_DRAFT)
        private val draftName = savedStateHandle.getStateFlow(KEY_NAME, "")
        private val draftText = savedStateHandle.getStateFlow(KEY_TEXT, "")
        private val deleting = savedStateHandle.getStateFlow(KEY_DELETING, false)

        private val effectChannel = Channel<SnippetsEffect>(Channel.BUFFERED)
        val effects: Flow<SnippetsEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<SnippetsUiState> =
            combine(repository.snippets, draftId, draftName, draftText, deleting) { snippets, id, name, text, asking ->
                val edited = snippets.firstOrNull { it.id == id }
                val draft =
                    when {
                        id == NEW_SNIPPET -> SnippetDraft(null, name, text, nameError(name, null, snippets))
                        edited != null -> SnippetDraft(id, name, text, nameError(name, id, snippets))
                        else -> null
                    }
                SnippetsUiState.Content(snippets, draft = draft, deleting = edited?.takeIf { asking })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SnippetsUiState.Loading)

        fun onEvent(event: SnippetsEvent) {
            when (event) {
                is SnippetsEvent.Reorder -> reorder(event.ids)
                is SnippetsEvent.Move -> move(event.id, event.up)
                SnippetsEvent.StartNew -> open(NEW_SNIPPET, "", "")
                is SnippetsEvent.StartEdit -> startEdit(event.id)
                is SnippetsEvent.EditName -> savedStateHandle[KEY_NAME] = event.name.take(Snippet.MAX_NAME_LENGTH)
                is SnippetsEvent.EditText -> savedStateHandle[KEY_TEXT] = event.text.take(Snippet.MAX_TEXT_LENGTH)
                SnippetsEvent.SaveDraft -> save()
                SnippetsEvent.CancelDraft -> close()
                SnippetsEvent.AskDelete -> savedStateHandle[KEY_DELETING] = true
                SnippetsEvent.ConfirmDelete -> delete()
                SnippetsEvent.CancelDelete -> savedStateHandle[KEY_DELETING] = false
            }
        }

        private fun content() = uiState.value as? SnippetsUiState.Content

        private fun reorder(ids: List<Long>) {
            viewModelScope.launch { repository.reorder(ids).reportFailure() }
        }

        private fun move(
            id: Long,
            up: Boolean,
        ) {
            val ids = content()?.snippets?.map { it.id } ?: return
            val index = ids.indexOf(id)
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            reorder(ids.toMutableList().apply { add(target, removeAt(index)) })
        }

        private fun startEdit(id: Long) {
            val snippet = content()?.snippets?.firstOrNull { it.id == id } ?: return
            open(id, snippet.name, snippet.text)
        }

        private fun open(
            id: Long,
            name: String,
            text: String,
        ) {
            savedStateHandle[KEY_NAME] = name
            savedStateHandle[KEY_TEXT] = text
            savedStateHandle[KEY_DELETING] = false
            savedStateHandle[KEY_DRAFT] = id
        }

        private fun close() {
            savedStateHandle[KEY_DRAFT] = NO_DRAFT
            savedStateHandle[KEY_DELETING] = false
        }

        private fun save() {
            val draft = content()?.draft?.takeIf { it.canSave } ?: return
            viewModelScope.launch {
                val outcome =
                    when (val id = draft.id) {
                        null -> repository.add(draft.name, draft.text)
                        else -> repository.update(Snippet(id, draft.name, draft.text))
                    }
                if (outcome is Outcome.Success) close() else outcome.reportFailure()
            }
        }

        private fun delete() {
            val snippet = content()?.deleting ?: return
            viewModelScope.launch {
                val outcome = repository.remove(snippet.id)
                if (outcome is Outcome.Success) close() else outcome.reportFailure()
            }
        }

        private suspend fun Outcome<*>.reportFailure() {
            if (this is Outcome.Failure) effectChannel.send(SnippetsEffect.ShowError(error))
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val KEY_DRAFT = "snippets.draft"
            const val KEY_NAME = "snippets.name"
            const val KEY_TEXT = "snippets.text"
            const val KEY_DELETING = "snippets.deleting"
            const val NO_DRAFT = -1L
            const val NEW_SNIPPET = 0L
        }
    }

/** Why [name] cannot name the draft of snippet [id] among [snippets], or null; an empty name says nothing yet. */
private fun nameError(
    name: String,
    id: Long?,
    snippets: List<Snippet>,
): SnippetNameError? =
    when {
        name.isEmpty() -> null
        !Snippet.isValidName(name) -> SnippetNameError.NotOneWord
        snippets.any { it.id != id && it.name.equals(name, ignoreCase = true) } -> SnippetNameError.Taken
        else -> null
    }
