package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageReplyTo
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageVoiceNote
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [VoiceNoteRepository] on TDLib and the app's upload cache ([UploadFiles]): each recording in a directory of its own
 * in `uploads/`, as `voice.<extension>`, on [io]; deleted with its directory once TDLib uploaded it (`UploadCleaner`),
 * when it is discarded, or when TDLib refuses it.
 */
@AccountScoped
class TdlibVoiceNoteRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val files: UploadFiles,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : VoiceNoteRepository {
        override suspend fun newVoiceNoteFile(extension: String): Outcome<String> =
            withContext(io) {
                try {
                    val file = File(files.newUploadDirectory(), "$VOICE_NOTE.$extension")
                    if (!file.createNewFile()) throw IOException("Cannot create a voice note file")
                    Outcome.Success(file.path)
                } catch (_: IOException) {
                    Outcome.Failure(DomainError.FileUnavailable)
                }
            }

        override fun discardVoiceNote(path: String) {
            scope.launch(io) { files.deleteUploads(listOf(path)) }
        }

        /** `sendMessage` of the voice note; a refused one's file is deleted. */
        override suspend fun sendVoiceNote(
            chatId: Long,
            voiceNote: RecordedVoiceNote,
            options: SendOptions,
            replyTo: ReplyTarget?,
        ): Outcome<Unit> {
            val content = voiceNote.toInputMessageVoiceNote()
            val result = client.sendMessage(chatId, content, options, replyTo?.toInputMessageReplyTo())
            if (result is Outcome.Failure) discardVoiceNote(voiceNote.path)
            return result
        }

        private companion object {
            /** The name of a voice note's file, before its format's extension. */
            const val VOICE_NOTE = "voice"
        }
    }
