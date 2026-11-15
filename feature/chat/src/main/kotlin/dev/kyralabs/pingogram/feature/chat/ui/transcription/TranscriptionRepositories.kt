package dev.kyralabs.pingogram.feature.chat.ui.transcription

import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.TranscriptRepository
import dev.kyralabs.pingogram.core.domain.repository.TranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceTranscriber
import javax.inject.Inject

/**
 * What the conversation's voice message transcription ([ChatTranscripts]) works with: the device's recognizer
 * ([transcriber]), the transcripts kept on the device ([transcripts]), its settings ([settings]) and the files it
 * downloads to transcribe ([files]). One parameter of `ChatViewModel` for them.
 */
class TranscriptionRepositories
    @Inject
    constructor(
        val transcriber: VoiceTranscriber,
        val transcripts: TranscriptRepository,
        val settings: TranscriptionSettingsRepository,
        val files: FileRepository,
    )
