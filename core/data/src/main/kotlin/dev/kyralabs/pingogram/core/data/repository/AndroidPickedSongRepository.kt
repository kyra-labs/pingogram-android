package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.AndroidContentSource
import dev.kyralabs.pingogram.core.data.attachment.AndroidMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.AttachmentPreparer
import dev.kyralabs.pingogram.core.data.attachment.ContentSource
import dev.kyralabs.pingogram.core.data.attachment.MediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.attachment.durationSeconds
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack
import dev.kyralabs.pingogram.core.domain.repository.PickedSongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PickedSongRepository] on the app's upload cache ([UploadFiles]). A picked file is copied as files are for sending
 * ([AttachmentPreparer], as a document, on [io]), then read by Android's media retriever: a file with audio and no
 * video, with a length, is a song; anything else is deleted at once and refused. Copies are deleted in the app's
 * [scope]; those of earlier runs go with the upload cache's own cleaning.
 *
 * Nothing about the songs, their tags or their paths is logged.
 */
@Singleton
class AndroidPickedSongRepository internal constructor(
    private val files: UploadFiles,
    content: ContentSource,
    private val media: MediaProcessor,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : PickedSongRepository {
    @Inject
    constructor(
        files: UploadFiles,
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(files, AndroidContentSource(context.contentResolver), AndroidMediaProcessor(), io, scope)

    private val preparer = AttachmentPreparer(files, content, media)

    override suspend fun prepareSong(uri: String): Outcome<NewProfileTrack> =
        withContext(io) {
            when (val prepared = preparer.prepare(uri, AttachmentKind.Document)) {
                is Outcome.Failure -> {
                    prepared
                }

                is Outcome.Success -> {
                    val copy = File(prepared.value.path)
                    val info = media.audioInfo(copy)
                    if (info == null) {
                        // A video, a picture or a file Android cannot read as audio.
                        files.delete(listOf(prepared.value))
                        Outcome.Failure(DomainError.FileUnavailable)
                    } else {
                        Outcome.Success(
                            NewProfileTrack(
                                path = copy.path,
                                durationSeconds = durationSeconds(info.durationMillis),
                                title = info.title.ifEmpty { copy.nameWithoutExtension },
                                performer = info.artist,
                            ),
                        )
                    }
                }
            }
        }

    override fun discardSong(path: String) {
        scope.launch(io) { files.deleteUploads(listOf(path)) }
    }
}
