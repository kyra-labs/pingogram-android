package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.TranscriptionLanguages
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult

/**
 * Turns a voice or round video message's audio into text on the device (`docs/DESIGN_SYSTEM.md` §6 "Voice message
 * transcription"): Android's own on-device speech recognizer, Android 13 and later. Pingogram bundles no model and
 * sends nothing anywhere; Android downloads and manages the speech languages.
 */
interface VoiceTranscriber {
    /** Whether this device can transcribe: Android 13 or later with an on-device recognizer. */
    val isAvailable: Boolean

    /**
     * The text of the audio file at [path] (Ogg Opus voice messages, MP4 round videos), about [durationSeconds] long.
     * Runs off the main thread and may take about as long as the audio; never throws. Cancelling stops it.
     */
    suspend fun transcribe(
        path: String,
        durationSeconds: Int,
    ): TranscriptionResult

    /** The recognizer's languages; null where it cannot tell (or [isAvailable] is false). */
    suspend fun languages(): TranscriptionLanguages?
}
