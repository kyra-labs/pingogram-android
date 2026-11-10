package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.repository.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The note dialog of a contact's profile (`docs/DESIGN_SYSTEM.md` §6 "Contact note"), while it is open.
 *
 * @property name the contact's first name, for "Note about Arjun".
 * @property text the note as typed, at most [ProfileLimits.NOTE_LENGTH_MAX] characters.
 * @property saved the note the contact has: "Save" stays disabled while [text] is the same.
 * @property isSaving Telegram has the new note and has not answered yet.
 * @property error why the last save failed, shown under the field; null without one.
 */
@Immutable
data class ProfileNoteEditor(
    val name: String,
    val text: String,
    val saved: String,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    /** Whether "Save" may be tapped: the note changed, and nothing is on its way. */
    val canSave: Boolean get() = !isSaving && text.trim() != saved.trim()

    /** How many characters the note has, as its counter shows them. */
    val length: Int get() = text.codePointCount(0, text.length)

    override fun toString() = "ProfileNoteEditor(isSaving=$isSaving, error=$error, …)"
}

/**
 * Editing the note the current user keeps about a contact (`setUserNote`, through [UserProfileRepository.setNote]) for
 * [ProfileViewModel]: the [editor] dialog while it is open.
 *
 * - **Opening** ([open]) starts from the note the contact has; **typing** ([edit]) keeps at most
 *   [ProfileLimits.NOTE_LENGTH_MAX] characters. Whether the dialog is open and what is typed are kept in the
 *   [savedStateHandle], so they survive process death ([restore] reopens it once the contact is known).
 * - **Saving** ([save]) sends the note once per tap, only where it changed; the dialog then closes, and TDLib's
 *   `updateUserFullInfo` shows the new note on the profile. A failure stays in the dialog as its error, the text kept.
 * - **Closing** ([dismiss]) forgets what was typed; a save already sent is not taken back.
 *
 * Runs in [scope], the view model's.
 */
internal class ProfileNote(
    private val repository: UserProfileRepository,
    private val userId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
) {
    private val mutableEditor = MutableStateFlow<ProfileNoteEditor?>(null)

    /** The open note dialog; null while none is. */
    val editor: StateFlow<ProfileNoteEditor?> = mutableEditor.asStateFlow()

    private var saveJob: Job? = null

    /** Opens the dialog on the contact's note [saved]; [name] is their first name. */
    fun open(
        name: String,
        saved: String,
    ) {
        if (mutableEditor.value != null) return
        savedStateHandle[KEY_TEXT] = saved
        mutableEditor.value = ProfileNoteEditor(name, saved, saved)
    }

    /** Whether a dialog was open before process death, waiting for [restore]. */
    fun hasSavedText(): Boolean = savedStateHandle.contains(KEY_TEXT)

    /** Reopens the dialog that was open before process death, with what was typed, once the contact is known. */
    fun restore(
        name: String,
        saved: String,
    ) {
        if (mutableEditor.value != null) return
        val typed = savedStateHandle.get<String>(KEY_TEXT) ?: return
        mutableEditor.value = ProfileNoteEditor(name, typed, saved)
    }

    fun edit(value: String) {
        val editor = mutableEditor.value ?: return
        if (editor.isSaving) return
        val text = value.limitedToCodePoints(ProfileLimits.NOTE_LENGTH_MAX)
        savedStateHandle[KEY_TEXT] = text
        mutableEditor.value = editor.copy(text = text, error = null)
    }

    fun save() {
        val editor = mutableEditor.value ?: return
        if (!editor.canSave) return
        mutableEditor.value = editor.copy(isSaving = true, error = null)
        saveJob =
            scope.launch {
                when (val saved = repository.setNote(userId, editor.text)) {
                    is Outcome.Success -> close()
                    is Outcome.Failure -> mutableEditor.update { it?.copy(isSaving = false, error = saved.error) }
                }
            }
    }

    fun dismiss() {
        saveJob?.cancel()
        close()
    }

    private fun close() {
        savedStateHandle.remove<String>(KEY_TEXT)
        mutableEditor.value = null
    }

    private companion object {
        const val KEY_TEXT = "profile.note"
    }
}

/** [this] cut to at most [max] characters (code points), never inside a surrogate pair. */
internal fun String.limitedToCodePoints(max: Int): String =
    if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max))
