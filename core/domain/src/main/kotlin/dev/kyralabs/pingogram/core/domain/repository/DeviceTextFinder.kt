package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.TextFindings

/**
 * Dates, times and addresses in a message's text, found on the device alone (`REQUIREMENTS.md` FR-X3, §6.2): for the
 * message actions' "Add to calendar" and "Open in maps". Nothing of the text is sent anywhere to find them.
 */
interface DeviceTextFinder {
    /**
     * The first date or time and the first address in [text], written at [writtenAtMillis] (which "17:30" or "Friday"
     * count from). Runs off the main thread; never fails: nothing found, or no classifier on the device (before
     * Android 10), is [TextFindings.None].
     */
    suspend fun find(
        text: String,
        writtenAtMillis: Long,
    ): TextFindings
}
