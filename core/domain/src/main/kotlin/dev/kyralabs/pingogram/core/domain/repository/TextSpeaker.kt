package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.SpeechStart

/**
 * Reads text aloud on the device (Listen to posts, `REQUIREMENTS.md` FR-X26): Android's text-to-speech, only with a
 * voice that needs no network connection, so nothing read leaves the phone.
 */
interface TextSpeaker {
    /**
     * Starts the device's speech engine with an offline voice of the device's language: a [SpeechStart.Ready] session
     * reads until it is closed. Never throws; cancelling releases the engine.
     */
    suspend fun open(): SpeechStart
}
