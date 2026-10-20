package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TransferRepository] on TDLib's updates, followed from before TDLib starts (`UpdateCacheModule`):
 *
 * - **Uploads**: `updateNewMessage` of an outgoing message being sent whose file is not on Telegram yet (a photo,
 *   video, GIF, document, song, voice or video message) starts one; `updateFile` reports its progress; it ends when
 *   the message is sent, fails or is deleted (a cancelled upload). A forwarded file is on Telegram already and counts
 *   for nothing.
 * - **Downloads**: [download] (`DownloadPriority.Requested`, through `TdlibFileRepository`) starts one, followed before
 *   TDLib is asked so no `updateFile` in between is missed; it ends when the file is complete or its download stops
 *   (cancelled, failed), or when TDLib refuses the request.
 *
 * TDLib closing (logout) ends them all ([UpdateStore]).
 */
@Singleton
class TdlibTransferRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : TransferRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, TransferState(), TransferState::reduce)

        override val transfers: StateFlow<Transfers> =
            store.state.map { it.summary() }.stateIn(scope, SharingStarted.Eagerly, Transfers.None)

        /**
         * Downloads file [fileId] at TDLib [priority] for the user, as one of the [transfers] until it ends. Succeeds
         * once TDLib accepted the request.
         */
        internal suspend fun download(
            fileId: Int,
            priority: Int,
        ): Outcome<Unit> {
            store.update { it.requesting(fileId) }
            // The whole file (offset 0, no limit), answered at once rather than when the download completes.
            val answer = client.request(TdApi.DownloadFile(fileId, priority, 0, 0, false))
            store.update { state ->
                when (answer) {
                    is Outcome.Success -> state.answered(answer.value)
                    is Outcome.Failure -> state.copy(downloads = state.downloads.remove(fileId))
                }
            }
            return answer.map {}
        }

        override suspend fun cancelAll(): Outcome<Unit> {
            val state = store.state.value
            val requests =
                state.downloads.keys.map { fileId -> TdApi.CancelDownloadFile(fileId, false) } +
                    state.uploads.entries
                        .groupBy({ it.value.chatId }, { it.key })
                        .map { (chatId, messageIds) -> TdApi.DeleteMessages(chatId, messageIds.toLongArray(), true) }
            // All at once, every one tried, so one that fails leaves no other transfer running.
            val results = coroutineScope { requests.map { async { client.request(it) } }.awaitAll() }
            return results.firstOrNull { it is Outcome.Failure }?.map {} ?: Outcome.Success(Unit)
        }
    }

/** How far a transfer is: [transferred] of [size] bytes, 0 while TDLib does not know the size. */
private data class Progress(
    val transferred: Long,
    val size: Long,
)

/** A message being sent with a file to upload: its chat, its file and how far it is. */
private data class Upload(
    val chatId: Long,
    val fileId: Int,
    val progress: Progress,
)

/** The uploads by message id, the downloads by file id (their progress null until TDLib answered or reported). */
private data class TransferState(
    val uploads: PersistentMap<Long, Upload> = persistentMapOf(),
    val downloads: PersistentMap<Int, Progress?> = persistentMapOf(),
) {
    fun reduce(update: TdApi.Update): TransferState =
        when (update) {
            is TdApi.UpdateFile -> reported(update.file)
            is TdApi.UpdateNewMessage -> sending(update.message)
            is TdApi.UpdateMessageSendSucceeded -> withoutUpload(update.oldMessageId)
            is TdApi.UpdateMessageSendFailed -> withoutUpload(update.oldMessageId)
            is TdApi.UpdateDeleteMessages -> withoutUploads(update.messageIds)
            else -> this
        }

    fun requesting(fileId: Int): TransferState =
        if (fileId in downloads) this else copy(downloads = downloads.put(fileId, null))

    /** TDLib's answer to the download request, unless an `updateFile` came first: the answer can be older. */
    fun answered(file: TdApi.File): TransferState =
        if (file.id in downloads && downloads[file.id] == null) downloaded(file) else this

    fun summary(): Transfers {
        val progress = uploads.values.map { it.progress } + downloads.values.filterNotNull()
        val known = progress.filter { it.size > 0 }
        return Transfers(
            uploads = uploads.size,
            downloads = downloads.size,
            transferredSize = known.sumOf { it.transferred.coerceAtMost(it.size) },
            totalSize = known.sumOf { it.size },
        )
    }

    private fun reported(file: TdApi.File): TransferState {
        val withDownload = if (file.id in downloads) downloaded(file) else this
        return withDownload.uploaded(file)
    }

    private fun downloaded(file: TdApi.File): TransferState {
        val local = file.local
        val ended = local.isDownloadingCompleted || !local.isDownloadingActive
        return copy(
            downloads =
                if (ended) {
                    downloads.remove(file.id)
                } else {
                    downloads.put(file.id, Progress(local.downloadedSize, file.knownSize))
                },
        )
    }

    private fun uploaded(file: TdApi.File): TransferState {
        if (uploads.isEmpty()) return this
        val progress = Progress(file.remote.uploadedSize, file.knownSize)
        val changed =
            uploads
                .filterValues { it.fileId == file.id && it.progress != progress }
                .mapValues { (_, upload) -> upload.copy(progress = progress) }
        return if (changed.isEmpty()) this else copy(uploads = uploads.putAll(changed))
    }

    private fun sending(message: TdApi.Message): TransferState {
        if (!message.isOutgoing || message.sendingState !is TdApi.MessageSendingStatePending) return this
        val file = message.content.fileToUpload() ?: return this
        if (file.remote.isUploadingCompleted) return this
        val upload = Upload(message.chatId, file.id, Progress(file.remote.uploadedSize, file.knownSize))
        return copy(uploads = uploads.put(message.id, upload))
    }

    private fun withoutUpload(messageId: Long): TransferState =
        if (messageId in uploads) copy(uploads = uploads.remove(messageId)) else this

    private fun withoutUploads(messageIds: LongArray): TransferState =
        if (messageIds.none { it in uploads }) this else copy(uploads = uploads - messageIds.asList())
}

/** The file's size, or TDLib's estimate while it does not know it; 0 when it knows neither. */
private val TdApi.File.knownSize: Long
    get() = if (size > 0) size else expectedSize

/** The file a message being sent uploads: the largest size of a photo, the media or document itself; null otherwise. */
private fun TdApi.MessageContent.fileToUpload(): TdApi.File? =
    when (this) {
        is TdApi.MessagePhoto -> photo.sizes.maxByOrNull { it.width.toLong() * it.height }?.photo
        is TdApi.MessageVideo -> video.video
        is TdApi.MessageAnimation -> animation.animation
        is TdApi.MessageDocument -> document.document
        is TdApi.MessageAudio -> audio.audio
        is TdApi.MessageVoiceNote -> voiceNote.voice
        is TdApi.MessageVideoNote -> videoNote.video
        else -> null
    }
