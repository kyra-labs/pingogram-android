package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.VideoNoteFiles
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository

/**
 * The video notes' repository: the same files every time ([FILES]) unless tests set otherwise; it records what is
 * discarded and sent, with the options and reply, and answers sends with what tests set.
 */
class FakeVideoNoteRepository : VideoNoteRepository {
    var onNewVideoNoteFiles: suspend () -> Outcome<VideoNoteFiles> = { Outcome.Success(FILES) }
    val discardedVideoNotes = mutableListOf<String>()

    /** The video note, options and reply of every [sendVideoNote] call. */
    val sentVideoNotes = mutableListOf<Triple<RecordedVideoNote, SendOptions, Long?>>()
    var onSendVideoNote: suspend (RecordedVideoNote) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun newVideoNoteFiles(): Outcome<VideoNoteFiles> = onNewVideoNoteFiles()

    override fun discardVideoNote(path: String) {
        discardedVideoNotes += path
    }

    override suspend fun sendVideoNote(
        chatId: Long,
        videoNote: RecordedVideoNote,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> {
        sentVideoNotes += Triple(videoNote, options, replyTo?.messageId)
        return onSendVideoNote(videoNote)
    }

    companion object {
        val FILES = VideoNoteFiles("/cache/uploads/video/video_note.mp4", "/cache/uploads/video/video_note.jpg")
    }
}
