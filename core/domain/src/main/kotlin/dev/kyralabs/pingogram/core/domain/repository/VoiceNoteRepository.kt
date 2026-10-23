package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.SendOptions

/**
 * The voice messages the composer records (`REQUIREMENTS.md` FR-C5): a file to record into ([newVoiceNoteFile]), which
 * belongs to the caller until it is sent ([sendVoiceNote]), which hands it to TDLib, or [discardVoiceNote]ed. Files
 * TDLib has uploaded are deleted by themselves, like the attach menu's copies.
 *
 * Sending fails with `DomainError.FloodWait`, `DomainError.Network` for a request that gets no answer in time,
 * `DomainError.SendingNotAllowed`, or another `DomainError` when TDLib refuses it.
 */
interface VoiceNoteRepository {
    /**
     * A new, empty file in the app's cache for a voice message to be recorded into, by its path, ending in
     * `.`[extension] (the audio format's, which TDLib reads the file's type from). Fails with
     * `DomainError.FileUnavailable` when it cannot be made.
     */
    suspend fun newVoiceNoteFile(extension: String): Outcome<String>

    /** Deletes [path], a file from [newVoiceNoteFile] that will not be sent. Returns at once. */
    fun discardVoiceNote(path: String)

    /**
     * Sends [voiceNote] to chat [chatId] as [options] say, replying to [replyToMessageId] if it is not null. Succeeds
     * once TDLib accepted the message, which it then shows as being sent while it uploads; its file is then TDLib's to
     * upload and deleted once uploaded. A refused voice note's file is deleted.
     */
    suspend fun sendVoiceNote(
        chatId: Long,
        voiceNote: RecordedVoiceNote,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>
}
