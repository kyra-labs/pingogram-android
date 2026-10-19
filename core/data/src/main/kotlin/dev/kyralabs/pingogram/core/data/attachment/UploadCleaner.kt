package dev.kyralabs.pingogram.core.data.attachment

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Deletes the copies of sent files (`UploadFiles`) once TDLib no longer needs them, so the cache does not fill up
 * (`REQUIREMENTS.md` FR-C5). It follows TDLib's updates from before TDLib starts (`UpdateCacheModule`), and remembers
 * which message being sent uploads which copies:
 *
 * - `updateNewMessage` of an outgoing message being sent whose files are copies starts following it: a new message,
 *   or one sent again (`resendMessages` deletes the failed message, then adds a new one with the same files).
 * - `updateMessageSendSucceeded`: Telegram has the files, so the copies go, the message's own and those the update
 *   names (after a restart nothing is remembered, but TDLib keeps the local paths of the files it uploaded).
 * - `updateMessageSendFailed`: the copies go, unless the message can be sent again; then its new id is followed.
 * - `updateDeleteMessages` (an upload cancelled, a message deleted): the copies go after [DELETE_GRACE], unless a
 *   message sent again took them over meanwhile, which TDLib announces right after deleting the failed one.
 * - TDLib closing (logout): the copies of every message followed go; their messages are gone with the database.
 * - At start, copies and camera files older than [maxAge] go: what a crash or a closed preview left behind, or a
 *   message TDLib still had to send after a restart and that nothing else reported.
 *
 * Updates are queued as they come and handled one at a time, in TDLib's order, on [io], where all file work runs.
 */
@Singleton
class UploadCleaner internal constructor(
    client: TdlibClient,
    private val files: UploadFiles,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
    maxAge: Duration?,
) : TdlibUpdateCache {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, files, io, scope, MAX_AGE)

    /** The copies of each message being sent (or failed, to be sent again), by id; touched on the queue only. */
    private val following = HashMap<Long, Set<String>>()

    private val queue = Channel<Event>(Channel.UNLIMITED)

    init {
        // Subscribed before the constructor returns, as UpdateStore does, so no update after it is missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update -> if (update.concernsUploads()) queue.trySend(Event.Update(update)) }
        }
        scope.launch(io) {
            maxAge?.let { files.deleteOlderThan(it.inWholeMilliseconds) }
            for (event in queue) handle(event)
        }
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.GraceOver -> delete(event.paths)
            is Event.Update -> handle(event.update)
        }
    }

    private fun handle(update: TdApi.Update) {
        when (update) {
            is TdApi.UpdateNewMessage -> follow(update.message.id, update.message.content.localPaths())
            is TdApi.UpdateMessageSendSucceeded -> delete(stopFollowing(update.oldMessageId) + update.localPaths())
            is TdApi.UpdateMessageSendFailed -> failed(update)
            is TdApi.UpdateDeleteMessages -> deleted(update.messageIds)
            is TdApi.UpdateAuthorizationState -> closed()
            else -> Unit
        }
    }

    private fun follow(
        messageId: Long,
        paths: Collection<String>,
    ) {
        val copies = paths.filterTo(HashSet(), files::isUpload)
        if (copies.isNotEmpty()) following[messageId] = copies
    }

    private fun stopFollowing(messageId: Long): Set<String> = following.remove(messageId).orEmpty()

    private fun failed(update: TdApi.UpdateMessageSendFailed) {
        val paths = stopFollowing(update.oldMessageId) + update.localPaths()
        val canRetry = (update.message.sendingState as? TdApi.MessageSendingStateFailed)?.canRetry == true
        if (canRetry) follow(update.message.id, paths) else delete(paths)
    }

    private fun deleted(messageIds: LongArray) {
        val orphans = messageIds.flatMapTo(HashSet()) { stopFollowing(it) }
        if (orphans.isEmpty()) return
        scope.launch(io) {
            delay(DELETE_GRACE)
            queue.trySend(Event.GraceOver(orphans))
        }
    }

    private fun closed() {
        val all = following.values.flatten()
        following.clear()
        delete(all)
    }

    /** Deletes the copies among [paths] that no message being followed uploads. */
    private fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val inUse = following.values.flatten().toSet()
        files.deleteUploads(paths.filter { it !in inUse && files.isUpload(it) })
    }

    /** What the queue holds: an update, or the end of a deleted message's grace. */
    private sealed interface Event {
        class Update(
            val update: TdApi.Update,
        ) : Event

        class GraceOver(
            val paths: Set<String>,
        ) : Event
    }

    internal companion object {
        /** How long a deleted message's copies wait for a message sent again that takes them over. */
        val DELETE_GRACE: Duration = 5.seconds

        /** How old leftover copies and camera files are deleted at start. */
        val MAX_AGE: Duration = 7.days
    }
}

/**
 * Whether [this] can concern a copy, checked quickly on TDLib's update stream: a file message being sent, the end of
 * a send, a deletion for good, TDLib closing.
 */
private fun TdApi.Update.concernsUploads(): Boolean =
    when (this) {
        is TdApi.UpdateNewMessage -> {
            message.isOutgoing &&
                message.sendingState is TdApi.MessageSendingStatePending &&
                message.content.localPaths().isNotEmpty()
        }

        is TdApi.UpdateMessageSendSucceeded, is TdApi.UpdateMessageSendFailed -> {
            true
        }

        is TdApi.UpdateDeleteMessages -> {
            isPermanent
        }

        is TdApi.UpdateAuthorizationState -> {
            authorizationState is TdApi.AuthorizationStateClosed
        }

        else -> {
            false
        }
    }

private fun TdApi.UpdateMessageSendSucceeded.localPaths() = message.content.localPaths()

private fun TdApi.UpdateMessageSendFailed.localPaths() = message.content.localPaths()

/**
 * The local paths of the files of a photo, video, GIF or document message (with their thumbnails): those of a message
 * being sent are the files it uploads. Empty for other content.
 */
internal fun TdApi.MessageContent.localPaths(): List<String> {
    val files =
        when (this) {
            is TdApi.MessagePhoto -> photo.sizes.map { it.photo }
            is TdApi.MessageVideo -> listOfNotNull(video.video, video.thumbnail?.file)
            is TdApi.MessageDocument -> listOfNotNull(document.document, document.thumbnail?.file)
            is TdApi.MessageAnimation -> listOfNotNull(animation.animation, animation.thumbnail?.file)
            else -> emptyList()
        }
    return files.mapNotNull { file -> file.local?.path?.takeIf { it.isNotEmpty() } }
}
