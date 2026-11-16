package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.TranscriptionLanguages
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult
import dev.kyralabs.pingogram.core.domain.repository.VoiceTranscriber
import kotlinx.coroutines.CompletableDeferred

/**
 * [VoiceTranscriber] answering [result] for every file ([transcribed] lists the paths asked for); [gate], when set,
 * holds each answer until it completes, so a test sees the transcription under way.
 */
class FakeVoiceTranscriber(
    override var isAvailable: Boolean = true,
    var result: TranscriptionResult = TranscriptionResult.Recognized("Hello from the voice message"),
    var languages: TranscriptionLanguages? = TranscriptionLanguages(ready = listOf("en-US")),
) : VoiceTranscriber {
    val transcribed = mutableListOf<String>()

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun transcribe(
        path: String,
        durationSeconds: Int,
    ): TranscriptionResult {
        transcribed += path
        gate?.await()
        return result
    }

    override suspend fun languages(): TranscriptionLanguages? = languages
}
