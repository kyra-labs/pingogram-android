package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository

/**
 * The voice notes' repository: files named after their extension ([voiceNoteFile]) unless tests set otherwise; it
 * records what is discarded and sent, and answers sends with what tests set.
 */
class FakeVoiceNoteRepository : VoiceNoteRepository {
    /** What [newVoiceNoteFile] answers for an extension. */
    var onNewVoiceNoteFile: suspend (String) -> Outcome<String> = { Outcome.Success(voiceNoteFile(it)) }
    val discardedVoiceNotes = mutableListOf<String>()

    /** The voice note and reply of every [sendVoiceNote] call. */
    val sentVoiceNotes = mutableListOf<Pair<RecordedVoiceNote, Long?>>()
    var onSendVoiceNote: suspend (RecordedVoiceNote) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun newVoiceNoteFile(extension: String): Outcome<String> = onNewVoiceNoteFile(extension)

    override fun discardVoiceNote(path: String) {
        discardedVoiceNotes += path
    }

    override suspend fun sendVoiceNote(
        chatId: Long,
        voiceNote: RecordedVoiceNote,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sentVoiceNotes += voiceNote to replyToMessageId
        return onSendVoiceNote(voiceNote)
    }

    companion object {
        /** The voice note file for [extension]. */
        fun voiceNoteFile(extension: String) = "/cache/uploads/voice/voice.$extension"
    }
}
