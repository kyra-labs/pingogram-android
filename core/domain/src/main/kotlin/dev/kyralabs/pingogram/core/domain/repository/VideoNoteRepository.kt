package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.VideoNoteFiles

/**
 * The round video messages the composer records (`docs/adr/0032-round-video-messages.md`), as [VoiceNoteRepository]
 * the voice messages: the files to record into ([newVideoNoteFiles]), which belong to the caller until the video note
 * is sent ([sendVideoNote]), which hands them to TDLib, or [discardVideoNote]ed. Files TDLib has uploaded are deleted
 * by themselves, like the attach menu's copies.
 *
 * Sending fails with `DomainError.FloodWait`, `DomainError.Network` for a request that gets no answer in time,
 * `DomainError.SendingNotAllowed`, or another `DomainError` when TDLib refuses it.
 */
interface VideoNoteRepository {
    /**
     * A new directory in the app's cache for a video message to be recorded into: an empty MP4 file and the path for
     * its thumbnail. Fails with `DomainError.FileUnavailable` when it cannot be made.
     */
    suspend fun newVideoNoteFiles(): Outcome<VideoNoteFiles>

    /** Deletes [path], the video of [newVideoNoteFiles] that will not be sent, with its thumbnail. Returns at once. */
    fun discardVideoNote(path: String)

    /**
     * Sends [videoNote] to chat [chatId] as [options] say, replying as [replyTo] says if it is not null. Succeeds
     * once TDLib accepted the message, which it then shows as being sent while it uploads; its files are then TDLib's
     * to upload and deleted once uploaded. A refused video note's files are deleted.
     */
    suspend fun sendVideoNote(
        chatId: Long,
        videoNote: RecordedVideoNote,
        options: SendOptions = SendOptions(),
        replyTo: ReplyTarget? = null,
    ): Outcome<Unit>
}
