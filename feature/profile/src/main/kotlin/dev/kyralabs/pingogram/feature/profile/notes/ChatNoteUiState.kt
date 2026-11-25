package dev.kyralabs.pingogram.feature.profile.notes

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ChatNote

/**
 * The user's own name and private note for a chat (`REQUIREMENTS.md` FR-X20), as its info page or profile shows them:
 * [shown] while Settings → Chats → "Notes and names on this phone" is on, the [name] and [note] (each empty when not
 * set), and the [editor] while it is open.
 */
@Immutable
data class ChatNoteUiState(
    val shown: Boolean = false,
    val name: String = "",
    val note: String = "",
    val editor: ChatNoteEditor? = null,
) {
    override fun toString() = "ChatNoteUiState(shown=$shown, editor=$editor, …)"
}

/**
 * The editing dialog: the [name] and [note] as typed, each at most its limit ([ChatNote.MAX_NAME_LENGTH],
 * [ChatNote.MAX_NOTE_LENGTH]), what is kept now ([savedName], [savedNote]), whether the change is being written
 * ([isSaving]) and whether the last save failed ([failed]).
 */
@Immutable
data class ChatNoteEditor(
    val name: String,
    val note: String,
    val savedName: String,
    val savedNote: String,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
) {
    /** Whether "Save" may be tapped: something changed, and nothing is on its way. */
    val canSave: Boolean get() = !isSaving && (name.trim() != savedName || note.trimEnd() != savedNote)

    override fun toString() = "ChatNoteEditor(isSaving=$isSaving, failed=$failed, …)"
}

/** What the user does with the name and note. */
sealed interface ChatNoteEvent {
    /** Opens the editor on what is kept. */
    data object Edit : ChatNoteEvent

    /** The name field changed. */
    data class NameChanged(
        val text: String,
    ) : ChatNoteEvent

    /** The note field changed. */
    data class NoteChanged(
        val text: String,
    ) : ChatNoteEvent

    /** "Save": keeps both; both empty removes them. */
    data object Save : ChatNoteEvent

    /** "Cancel", back, or a tap outside: closes the editor, nothing kept. */
    data object Dismiss : ChatNoteEvent
}
