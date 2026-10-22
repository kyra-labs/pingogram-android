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
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoLimits
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PickedPhotoRepository] on the app's upload cache ([UploadFiles]). A picked image is prepared as picked photos are
 * for sending ([AttachmentPreparer], on [io]): copied, decoded and written anew as an upright JPEG without metadata
 * (the location included), here at most [PickedPhotoLimits.MAX_SIDE_PX] on its longer side. What Android cannot decode
 * as a still image (a video, a GIF, another file) is deleted at once and refused. Copies are deleted in the app's
 * [scope], so a screen that leaves meanwhile leaves nothing behind; those of earlier runs go with the upload cache's
 * own cleaning.
 *
 * Nothing about the images or their paths is logged.
 */
@Singleton
class AndroidPickedPhotoRepository internal constructor(
    private val files: UploadFiles,
    content: ContentSource,
    media: MediaProcessor,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : PickedPhotoRepository {
    @Inject
    constructor(
        files: UploadFiles,
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(files, AndroidContentSource(context.contentResolver), AndroidMediaProcessor(), io, scope)

    private val preparer = AttachmentPreparer(files, content, media)

    override suspend fun preparePhoto(uri: String): Outcome<String> =
        withContext(io) {
            when (val prepared = preparer.prepare(uri, AttachmentKind.Media, PickedPhotoLimits.MAX_SIDE_PX)) {
                is Outcome.Failure -> {
                    Outcome.Failure(DomainError.PhotoInvalid)
                }

                is Outcome.Success -> {
                    val attachment = prepared.value
                    if (attachment is Attachment.Photo) {
                        Outcome.Success(attachment.path)
                    } else {
                        // A video, a GIF or a file Android cannot decode as an image.
                        files.delete(listOf(attachment))
                        Outcome.Failure(DomainError.PhotoInvalid)
                    }
                }
            }
        }

    override fun discardPhoto(path: String) {
        scope.launch(io) { files.deleteUploads(listOf(path)) }
    }
}
