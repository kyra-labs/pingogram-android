package dev.kyralabs.pingogram.core.domain.model

/**
 * What Android's on-device text classifier found in a message's text that another app can act on (`REQUIREMENTS.md`
 * FR-X3): its first date or time ([event]) and its first address ([address]), each null where it found none.
 * `toString()` leaves both out, as they come from the user's messages.
 */
data class TextFindings(
    val event: FoundEvent? = null,
    val address: String? = null,
) {
    override fun toString() = "TextFindings(event=${event != null}, address=${address != null})"

    companion object {
        /** Nothing found. */
        val None = TextFindings()
    }
}

/**
 * A date or time written in a text: its start in milliseconds since the epoch where it reads as one ([startMillis],
 * null for words such as "tomorrow evening"), and whether it is a whole day, a date without a time ([isAllDay]).
 */
data class FoundEvent(
    val startMillis: Long?,
    val isAllDay: Boolean,
)
