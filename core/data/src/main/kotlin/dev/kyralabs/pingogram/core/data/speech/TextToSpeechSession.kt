package dev.kyralabs.pingogram.core.data.speech

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.kyralabs.pingogram.core.domain.repository.SpeechSession
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * A [SpeechSession] on a started [engine] with its voice set ([AndroidTextSpeaker]): each text is read as media
 * speech, in pieces no longer than the engine takes ([speechPieces]), one after the other; the engine reports each
 * piece's end on a thread of its own.
 */
internal class TextToSpeechSession(
    private val engine: TextToSpeech,
) : SpeechSession {
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()
    private val ids = AtomicLong()

    init {
        engine.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(Progress())
    }

    override suspend fun speak(
        text: String,
        rate: Float,
    ): Boolean =
        withContext(Dispatchers.Main) {
            engine.setSpeechRate(rate)
            speechPieces(text, TextToSpeech.getMaxSpeechInputLength()).all { speakPiece(it) }
        }

    private suspend fun speakPiece(piece: String): Boolean {
        val id = ids.incrementAndGet().toString()
        return try {
            suspendCancellableCoroutine { continuation ->
                pending[id] = continuation
                if (engine.speak(piece, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                    pending.remove(id)?.resume(false)
                }
            }
        } catch (cancelled: CancellationException) {
            pending.remove(id)
            engine.stop()
            throw cancelled
        }
    }

    override fun close() {
        pending.keys.toList().forEach { finish(it, read = false) }
        engine.stop()
        engine.shutdown()
    }

    private fun finish(
        id: String,
        read: Boolean,
    ) {
        pending.remove(id)?.let { if (it.isActive) it.resume(read) }
    }

    private inner class Progress : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) = finish(utteranceId, read = true)

        @Deprecated("Android's own; engines call the overload with an error code too.")
        override fun onError(utteranceId: String) = finish(utteranceId, read = false)

        override fun onError(
            utteranceId: String,
            errorCode: Int,
        ) = finish(utteranceId, read = false)

        override fun onStop(
            utteranceId: String,
            interrupted: Boolean,
        ) = finish(utteranceId, read = false)
    }
}

/**
 * [text] in pieces of at most [max] characters, cut after the last end of a sentence or line that fits, else at the
 * last space, else at [max] (never inside a surrogate pair); blank text gives none.
 */
internal fun speechPieces(
    text: String,
    max: Int,
): List<String> {
    val pieces = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > max) {
        val window = rest.substring(0, max)
        val cut =
            window.lastIndexOfAny(SENTENCE_ENDS).takeIf { it > 0 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: if (window.last().isHighSurrogate()) max - 1 else max
        pieces += rest.substring(0, cut).trim()
        rest = rest.substring(cut).trim()
    }
    if (rest.isNotEmpty()) pieces += rest
    return pieces
}

private val SENTENCE_ENDS = charArrayOf('.', '!', '?', '\n', '。', '！', '？')
