package dev.kyralabs.pingogram.core.domain.repository

/** The device's speech engine with its voice chosen ([TextSpeaker.open]), until [close]. */
interface SpeechSession : AutoCloseable {
    /**
     * Reads [text] aloud at [rate] (1 is the voice's own pace) and returns once it is read: true, or false if the
     * engine failed. Cancelling stops it at once.
     */
    suspend fun speak(
        text: String,
        rate: Float,
    ): Boolean

    /** Stops reading and releases the engine, on the main thread; the session reads nothing after. */
    override fun close()
}
