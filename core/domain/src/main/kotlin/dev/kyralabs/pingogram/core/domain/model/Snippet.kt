package dev.kyralabs.pingogram.core.domain.model

/**
 * A text the user saved to insert in the composer by its [name] (`REQUIREMENTS.md` FR-X2): typing "/" and the name, or
 * picking it from the composer's menu, puts [text] in the field and never sends it. Kept on this device only.
 *
 * `toString()` leaves the text out, so a snippet can be logged.
 */
data class Snippet(
    val id: Long,
    val name: String,
    val text: String,
) {
    override fun toString() = "Snippet(id=$id)"

    companion object {
        /** The most snippets an account keeps (FR-X2). */
        const val MAX_SNIPPETS = 100

        /** The longest name: as long as a bot command's, which a snippet's name shares the "/" with. */
        const val MAX_NAME_LENGTH = 32

        /** The longest text: a message's (Telegram's `message_text_length_max`). */
        const val MAX_TEXT_LENGTH = 4_096

        /**
         * Whether [name] can name a snippet: 1 to [MAX_NAME_LENGTH] letters, digits or underscores, so "/" and the name
         * read as one word in the composer, as a bot command does.
         */
        fun isValidName(name: String): Boolean =
            name.length in 1..MAX_NAME_LENGTH && name.all { it.isLetterOrDigit() || it == '_' }
    }
}
