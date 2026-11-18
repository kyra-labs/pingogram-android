package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.TextFindings
import dev.kyralabs.pingogram.core.domain.repository.DeviceTextFinder

/**
 * [DeviceTextFinder] without Android: each text finds what [findings] gives it (by the whole text), else nothing.
 * [asked] records every text asked about, in order.
 */
class FakeDeviceTextFinder : DeviceTextFinder {
    /** What each text finds, by the whole text. */
    val findings = mutableMapOf<String, TextFindings>()

    /** Every text [find] was asked about, in order. */
    val asked = mutableListOf<String>()

    override suspend fun find(
        text: String,
        writtenAtMillis: Long,
    ): TextFindings {
        asked += text
        return findings[text] ?: TextFindings.None
    }
}
