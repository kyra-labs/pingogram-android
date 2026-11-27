package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.domain.repository.SpeechSession

/** What starting the device's speech engine gave (`TextSpeaker.open`). */
sealed interface SpeechStart {
    /** A [session] that reads with an offline voice of the device's language. */
    data class Ready(
        val session: SpeechSession,
    ) : SpeechStart

    /**
     * The engine has no installed voice for the device's language that works without a network connection; Android's
     * text-to-speech settings can download one.
     */
    data object NoOfflineVoice : SpeechStart

    /** The device has no speech engine, or it did not start. */
    data object Unavailable : SpeechStart
}
