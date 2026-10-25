package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageReplyTo
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.VideoNoteFiles
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VideoNoteRepository] on TDLib and the app's upload cache ([UploadFiles]), as [TdlibVoiceNoteRepository] does voice
 * notes: each recording in a directory of its own in `uploads/`, as `video_note.mp4` with `video_note.jpg` beside it,
 * on [io]; deleted with its directory once TDLib uploaded it (`UploadCleaner`), when it is discarded, or when TDLib
 * refuses it.
 */
@Singleton
class TdlibVideoNoteRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val files: UploadFiles,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : VideoNoteRepository {
        override suspend fun newVideoNoteFiles(): Outcome<VideoNoteFiles> =
            withContext(io) {
                try {
                    val directory = files.newUploadDirectory()
                    val video = File(directory, VIDEO)
                    if (!video.createNewFile()) throw IOException("Cannot create a video note file")
                    Outcome.Success(VideoNoteFiles(video.path, File(directory, THUMBNAIL).path))
                } catch (_: IOException) {
                    Outcome.Failure(DomainError.FileUnavailable)
                }
            }

        override fun discardVideoNote(path: String) {
            scope.launch(io) { files.deleteUploads(listOf(path)) }
        }

        /** `sendMessage` of the video note; a refused one's files are deleted. */
        override suspend fun sendVideoNote(
            chatId: Long,
            videoNote: RecordedVideoNote,
            options: SendOptions,
            replyTo: ReplyTarget?,
        ): Outcome<Unit> {
            val content = videoNote.toInputMessageVideoNote()
            val result = client.sendMessage(chatId, content, options, replyTo?.toInputMessageReplyTo())
            if (result is Outcome.Failure) discardVideoNote(videoNote.path)
            return result
        }

        private companion object {
            /** The names of a video note's files in its directory; TDLib reads the video's type from its extension. */
            const val VIDEO = "video_note.mp4"
            const val THUMBNAIL = "video_note.jpg"
        }
    }

/**
 * `inputMessageVideoNote` of an `inputVideoNote` from the recorded square MP4, with its first frame's still as the
 * thumbnail where there is one, its length in seconds and the side of its square, not self-destructing (secret chats
 * apply their own timer).
 */
private fun RecordedVideoNote.toInputMessageVideoNote(): TdApi.InputMessageVideoNote {
    val still = thumbnail?.let { TdApi.InputThumbnail(TdApi.InputFileLocal(it.path), it.width, it.height) }
    return TdApi.InputMessageVideoNote(TdApi.InputVideoNote(TdApi.InputFileLocal(path), still, duration, length), null)
}
