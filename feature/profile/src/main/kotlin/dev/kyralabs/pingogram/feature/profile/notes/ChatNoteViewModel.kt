package dev.kyralabs.pingogram.feature.profile.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.domain.repository.ChatNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The name and note on this phone (`REQUIREMENTS.md` FR-X20) of the chat its page [open]s: what is kept, whether the
 * setting shows them, and the editor. Which chats get them (not contacts, not oneself) is the page's to decide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatNoteViewModel
    @Inject
    constructor(
        private val notes: ChatNoteRepository,
        notesAndNames: NotesAndNamesRepository,
    ) : ViewModel() {
        private val chatId = MutableStateFlow<Long?>(null)
        private val editor = MutableStateFlow<ChatNoteEditor?>(null)

        val state: StateFlow<ChatNoteUiState> =
            chatId
                .filterNotNull()
                .flatMapLatest { id -> notes.notes.map { it[id] } }
                .let { note ->
                    combine(note, notesAndNames.enabled, editor) { kept, enabled, editing ->
                        ChatNoteUiState(enabled, kept?.name.orEmpty(), kept?.note.orEmpty(), editing.takeIf { enabled })
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatNoteUiState())

        /** Shows the name and note of chat [id]; the page calls it once it knows the chat. */
        fun open(id: Long) {
            if (chatId.value != id) editor.value = null
            chatId.value = id
        }

        fun onEvent(event: ChatNoteEvent) {
            when (event) {
                ChatNoteEvent.Edit -> {
                    edit()
                }

                is ChatNoteEvent.NameChanged -> {
                    editor.update { it?.copy(name = event.text.take(ChatNote.MAX_NAME_LENGTH), failed = false) }
                }

                is ChatNoteEvent.NoteChanged -> {
                    editor.update { it?.copy(note = event.text.take(ChatNote.MAX_NOTE_LENGTH), failed = false) }
                }

                ChatNoteEvent.Save -> {
                    save()
                }

                ChatNoteEvent.Dismiss -> {
                    editor.value = null
                }
            }
        }

        private fun edit() {
            val id = chatId.value ?: return
            viewModelScope.launch {
                val kept = notes.notes.first()[id]
                val name = kept?.name.orEmpty()
                val note = kept?.note.orEmpty()
                editor.value = ChatNoteEditor(name, note, savedName = name, savedNote = note)
            }
        }

        private fun save() {
            val id = chatId.value ?: return
            val current = editor.value?.takeIf { it.canSave } ?: return
            editor.value = current.copy(isSaving = true, failed = false)
            viewModelScope.launch {
                editor.value =
                    when (notes.set(ChatNote(id, current.name, current.note))) {
                        is Outcome.Success -> null
                        is Outcome.Failure -> current.copy(isSaving = false, failed = true)
                    }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
