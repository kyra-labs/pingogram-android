package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.error.toOutcome
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

// Uploading a photo of the user's own or of a chat from a local copy (`PickedPhotoRepository`), with its progress.

/**
 * Sends [request] for the photo at [path] (a local file, as `inputChatPhotoStatic`) and waits for its answer, handing
 * the upload's progress to [onProgress] meanwhile: the share uploaded from the `updateFile`s of that file, recognized
 * by its path. Each progress gives it another [REQUEST_TIMEOUT], so a large photo on a slow network is never cut off
 * while it moves; a whole timeout without either fails with [DomainError.Network]. Cancelling stops the wait only.
 */
internal suspend fun TdlibClient.uploadPhoto(
    path: String,
    onProgress: (progress: Float) -> Unit,
    request: (photo: TdApi.InputChatPhoto) -> TdApi.Function<TdApi.Ok>,
): Outcome<Unit> =
    coroutineScope {
        val progress = Channel<Float>(Channel.CONFLATED)
        // Subscribed before the request is sent, so its first updateFile is not missed.
        val watcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                updates.collect { update ->
                    if (update is TdApi.UpdateFile && update.file.local?.path == path) {
                        update.file.uploadProgress()?.let(progress::trySend)
                    }
                }
            }
        val photo = TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path))
        val answer = async { send(request(photo)).toOutcome().map {} }
        try {
            awaitUpload(answer, progress, onProgress)
        } finally {
            watcher.cancel()
            answer.cancel()
        }
    }

/**
 * Waits for the upload's [answer], handing its [progress] to [onProgress] meanwhile. Each progress gives it another
 * [REQUEST_TIMEOUT]; a whole timeout without either fails with [DomainError.Network].
 */
private suspend fun awaitUpload(
    answer: Deferred<Outcome<Unit>>,
    progress: ReceiveChannel<Float>,
    onProgress: (Float) -> Unit,
): Outcome<Unit> {
    while (true) {
        val step =
            withTimeoutOrNull(REQUEST_TIMEOUT) {
                select<UploadStep> {
                    answer.onAwait { UploadStep.Answered(it) }
                    progress.onReceive { UploadStep.Progressed(it) }
                }
            } ?: return Outcome.Failure(DomainError.Network)
        when (step) {
            is UploadStep.Answered -> return step.outcome
            is UploadStep.Progressed -> onProgress(step.progress)
        }
    }
}

private sealed interface UploadStep {
    data class Answered(
        val outcome: Outcome<Unit>,
    ) : UploadStep

    data class Progressed(
        val progress: Float,
    ) : UploadStep
}

/** The share of the file uploaded, from 0 to 1; null while its size is unknown. */
private fun TdApi.File.uploadProgress(): Float? {
    val uploaded = remote ?: return null
    val total = if (size > 0) size else expectedSize
    return when {
        uploaded.isUploadingCompleted -> 1f
        total > 0 -> (uploaded.uploadedSize.toFloat() / total).coerceIn(0f, 1f)
        else -> null
    }
}
