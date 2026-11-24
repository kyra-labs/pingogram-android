package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.TextFindings
import dev.kyralabs.pingogram.core.domain.repository.DeviceTextFinder
import kotlinx.coroutines.CompletableDeferred

/**
 * [DeviceTextFinder] without Android: each text finds what [findings] gives it (by the whole text), else nothing.
 * [asked] records every text asked about, in order; while [held] is not complete, [find] waits for it, as a slow
 * classifier does.
 */
class FakeDeviceTextFinder : DeviceTextFinder {
    /** What each text finds, by the whole text. */
    val findings = mutableMapOf<String, TextFindings>()

    /** Every text [find] was asked about, in order. */
    val asked = mutableListOf<String>()

    /** When set, what [find] waits for before it answers. */
    var held: CompletableDeferred<Unit>? = null

    override suspend fun find(
        text: String,
        writtenAtMillis: Long,
    ): TextFindings {
        asked += text
        held?.await()
        return findings[text] ?: TextFindings.None
    }
}
