package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.SpeechStart
import dev.kyralabs.pingogram.core.domain.repository.SpeechSession
import dev.kyralabs.pingogram.core.domain.repository.TextSpeaker
import kotlinx.coroutines.CompletableDeferred

/**
 * [TextSpeaker] in memory. While [ready], [open] gives a session that records each text it reads in [spoken] and
 * returns once the test calls [finish]; otherwise it gives [unready] (no offline voice, at first unset).
 */
class FakeTextSpeaker(
    var ready: Boolean = true,
    var unready: SpeechStart = SpeechStart.NoOfflineVoice,
) : TextSpeaker {
    /** Each text read, with its rate, in order. */
    val spoken = mutableListOf<Pair<String, Float>>()

    /** How many sessions were opened, and how many closed. */
    var opened = 0
        private set
    var closed = 0
        private set

    private var reading: CompletableDeferred<Boolean>? = null

    /** Whether a text is being read now. */
    val isReading: Boolean get() = reading?.isActive == true

    /** Ends the text being read: [read] if it was read, false if the engine failed. */
    fun finish(read: Boolean = true) {
        reading?.complete(read)
    }

    override suspend fun open(): SpeechStart {
        if (!ready) return unready
        opened++
        return SpeechStart.Ready(Session())
    }

    private inner class Session : SpeechSession {
        override suspend fun speak(
            text: String,
            rate: Float,
        ): Boolean {
            spoken += text to rate
            val done = CompletableDeferred<Boolean>()
            reading = done
            // A cancelled reader stops the voice, as the engine does.
            return try {
                done.await()
            } finally {
                done.cancel()
            }
        }

        override fun close() {
            reading?.cancel()
            closed++
        }
    }
}
