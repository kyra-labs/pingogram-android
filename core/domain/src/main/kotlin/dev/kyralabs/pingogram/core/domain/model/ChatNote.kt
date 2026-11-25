package dev.kyralabs.pingogram.core.domain.model

/**
 * What the user wrote on this phone about chat [chatId] (`REQUIREMENTS.md` FR-X20): their own [name] for it, shown
 * where its title shows, and a private [note]; each empty when not set. Telegram never sees either.
 */
data class ChatNote(
    val chatId: Long,
    val name: String = "",
    val note: String = "",
) {
    /** Neither a name nor a note: nothing to keep. */
    val isEmpty: Boolean get() = name.isBlank() && note.isBlank()

    /** This note with its name trimmed and both cut to their limits, as it is kept. */
    fun kept(): ChatNote = copy(name = name.trim().take(MAX_NAME_LENGTH), note = note.trimEnd().take(MAX_NOTE_LENGTH))

    companion object {
        /** The longest name kept, in characters. */
        const val MAX_NAME_LENGTH = 64

        /** The longest note kept, in characters. */
        const val MAX_NOTE_LENGTH = 2_000
    }
}
