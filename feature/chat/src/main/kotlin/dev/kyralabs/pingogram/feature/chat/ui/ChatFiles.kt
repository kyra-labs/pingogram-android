package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.content.uploadFileId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * The files of a conversation's voice messages, songs and documents, for [ChatViewModel]: their states through
 * [FileRepository.observe], and the downloads the user taps in [scope], as transfers that continue in the background
 * ([DownloadPriority.Requested]). A failed request goes to [onError]. Playing
 * a voice message or song downloads its file through [ChatPlayback]. The uploads of the messages being sent from this
 * device come through [FileRepository.observeUpload] ([uploadsOf]).
 */
internal class ChatFiles(
    private val repository: FileRepository,
    private val scope: CoroutineScope,
    private val onError: (DomainError) -> Unit,
) {
    /**
     * The state of every file of the messages in [history], by id: one [FileRepository.observe] per file, followed for
     * as long as its message is loaded. A file TDLib has not reported is left out until it is.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun statesOf(history: Flow<ChatHistory>): Flow<ImmutableMap<Int, FileState>> =
        history
            .map { it.messages.mapNotNullTo(LinkedHashSet()) { message -> message.content.downloadableFileId } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map(::observed)) { files ->
                        files.mapNotNull { (id, state) -> state?.let { id to it } }.toMap().toImmutableMap()
                    }
                }
            }.distinctUntilChanged()

    /**
     * The upload of every message of [history] being sent from this device with a photo, video, GIF or document
     * ([uploadFileId]), by message id: its progress once TDLib reports it, unknown until then. Followed for as long as
     * the message is being sent; a failed message has none.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun uploadsOf(history: Flow<ChatHistory>): Flow<ImmutableMap<Long, MessageUpload>> =
        history
            .map { history ->
                history.messages
                    .filter { it.isOutgoing && it.sendingState == SendingState.Pending }
                    .mapNotNull { message -> message.content.uploadFileId?.let { message.id to it } }
            }.distinctUntilChanged()
            .flatMapLatest { uploading ->
                if (uploading.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(uploading.map { (messageId, fileId) -> upload(messageId, fileId) }) { uploads ->
                        uploads.toMap().toImmutableMap()
                    }
                }
            }.distinctUntilChanged()

    fun download(fileId: Int) = request { download(fileId, DownloadPriority.Requested) }

    fun cancelDownload(fileId: Int) = request { cancelDownload(fileId) }

    private fun request(call: suspend FileRepository.() -> Outcome<Unit>) {
        scope.launch { repository.call().onFailure(onError) }
    }

    /** The upload of [fileId], message [messageId]'s, as soon as it is subscribed: unknown until TDLib reports it. */
    private fun upload(
        messageId: Long,
        fileId: Int,
    ): Flow<Pair<Long, MessageUpload>> =
        repository
            .observeUpload(fileId)
            .map { upload -> messageId to MessageUpload(upload.progressOrNull) }
            .onStart { emit(messageId to MessageUpload(progress = null)) }

    /** [fileId] with its state as soon as it is subscribed: null until TDLib reports it. */
    private fun observed(fileId: Int): Flow<Pair<Int, FileState?>> =
        repository
            .observe(fileId)
            .map<FileState, Pair<Int, FileState?>> { fileId to it }
            .onStart { emit(fileId to null) }
}

/**
 * The uploads of this bubble's messages among [uploads]; the same empty map when none of them is being sent, so the
 * bubbles that do not upload skip while one does.
 */
internal fun ChatItem.Bubble.uploadsIn(uploads: ImmutableMap<Long, MessageUpload>): ImmutableMap<Long, MessageUpload> {
    val ids = album?.messageIds ?: listOf(id)
    if (uploads.isEmpty() || ids.none(uploads::containsKey)) return NoUploads
    return ids.mapNotNull { messageId -> uploads[messageId]?.let { messageId to it } }.toMap().toImmutableMap()
}

private val NoUploads: ImmutableMap<Long, MessageUpload> = persistentMapOf()

/** The progress to show: none while TDLib has neither started the upload nor told the size. */
private val FileUpload.progressOrNull: Float?
    get() {
        val started = isActive || uploadedSize > 0
        return if (isCompleted || (size > 0 && started)) progress else null
    }
