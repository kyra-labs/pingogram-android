package dev.kyralabs.pingogram.feature.media.ui

import androidx.media3.common.Player
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.media.ui.files.GalleryItem
import dev.kyralabs.pingogram.feature.media.ui.files.MediaFileActions
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// Fakes of what the media viewer reads, holding their state in fields the tests set.

/**
 * A chat's media, served as TDLib pages them ([serve]), or answered by the tests ([onAround], [onOlder], [onNewer],
 * [onPosition]). Every request is recorded in [calls]; [deletions] emits deleted messages. Whether a message can be
 * saved is [onCanBeSaved]'s answer, yes unless a test says otherwise; the messages asked about go to [saveChecks].
 */
class FakeChatMediaRepository : ChatMediaRepository {
    /** `around:<id>`, `older:<filter>:<id>`, `newer:<filter>:<id>` and `position:<filter>:<id>`, in order. */
    val calls = mutableListOf<String>()

    var onAround: suspend (Long) -> Outcome<ChatMediaPage> = { Outcome.Failure(DomainError.NotFound) }
    var onOlder: suspend (Long) -> Outcome<ChatMediaPage> = { Outcome.Failure(DomainError.NotFound) }
    var onNewer: suspend (Long) -> Outcome<ChatMediaPage> = { Outcome.Failure(DomainError.NotFound) }
    var onPosition: suspend (Long) -> Outcome<Int> = { Outcome.Failure(DomainError.Unknown(400, "No position")) }
    var onCanBeSaved: suspend (Long) -> Outcome<Boolean> = { Outcome.Success(true) }
    val saveChecks = mutableListOf<Long>()

    val deletions = MutableSharedFlow<Set<Long>>()

    /**
     * Serves [messages] of [filter] (any order), as TDLib pages them: up to [radius] on each side of the opened one,
     * [pageSize] per older or newer page, each end reached only by a page with nothing on that side; [total] as the
     * count, and positions counted from the newest.
     */
    fun serve(
        messages: List<Message>,
        filter: ChatMediaFilter = ChatMediaFilter.PhotosAndVideos,
        radius: Int = 2,
        pageSize: Int = 3,
        total: Int? = messages.size,
    ) {
        val newestFirst = messages.sortedByDescending { it.id }

        fun page(
            found: List<Message>,
            reachedOldest: Boolean,
            reachedNewest: Boolean,
        ) = Outcome.Success(ChatMediaPage(found.toImmutableList(), filter, reachedOldest, reachedNewest, total))
        onAround = { id ->
            val index = newestFirst.indexOfFirst { it.id == id }
            if (index < 0) {
                Outcome.Failure(DomainError.NotFound)
            } else {
                val first = (index - radius).coerceAtLeast(0)
                val found = newestFirst.subList(first, minOf(newestFirst.size, index + radius + 1))
                page(found, found.none { it.id < id }, found.none { it.id > id })
            }
        }
        onOlder = { id ->
            val older = newestFirst.filter { it.id < id }.take(pageSize)
            page(older, reachedOldest = older.isEmpty(), reachedNewest = false)
        }
        onNewer = { id ->
            val newer = newestFirst.filter { it.id > id }.takeLast(pageSize)
            page(newer, reachedOldest = false, reachedNewest = newer.isEmpty())
        }
        onPosition = { id -> Outcome.Success(newestFirst.indexOfFirst { it.id == id } + 1) }
    }

    override suspend fun around(
        chatId: Long,
        messageId: Long,
    ): Outcome<ChatMediaPage> {
        calls += "around:$messageId"
        return onAround(messageId)
    }

    override suspend fun older(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<ChatMediaPage> {
        calls += "older:$filter:$messageId"
        return onOlder(messageId)
    }

    override suspend fun newer(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<ChatMediaPage> {
        calls += "newer:$filter:$messageId"
        return onNewer(messageId)
    }

    override suspend fun position(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<Int> {
        calls += "position:$filter:$messageId"
        return onPosition(messageId)
    }

    override suspend fun canBeSaved(
        chatId: Long,
        messageId: Long,
    ): Outcome<Boolean> {
        saveChecks += messageId
        return onCanBeSaved(messageId)
    }

    override fun deletedMessages(chatId: Long): Flow<Set<Long>> = deletions
}

/**
 * Files by id ([files]): [observe] follows a file's entry and emits nothing while it has none, as TDLib does for a file
 * it has not reported. Downloads and cancels are recorded in [calls] and answered with [onDownload].
 */
class FakeFileRepository : FileRepository {
    val files = MutableStateFlow<Map<Int, FileState>>(emptyMap())

    /** `download:<id>:<priority>` and `cancel:<id>`, in order. */
    val calls = mutableListOf<String>()

    var onDownload: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }

    fun setFile(
        fileId: Int,
        state: FileState,
    ) {
        files.value += fileId to state
    }

    override fun observe(fileId: Int): Flow<FileState> = files.mapNotNull { it[fileId] }.distinctUntilChanged()

    // The media viewer shows received files, never uploads.
    override fun observeUpload(fileId: Int): Flow<FileUpload> = emptyFlow()

    override suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit> {
        calls += "download:$fileId:$priority"
        return onDownload(fileId)
    }

    override suspend fun cancelDownload(fileId: Int): Outcome<Unit> {
        calls += "cancel:$fileId"
        return Outcome.Success(Unit)
    }
}

class FakeUserRepository : UserRepository {
    override val myUserId = MutableStateFlow<Long?>(null)
    val userMap = MutableStateFlow<ImmutableMap<Long, User>>(persistentMapOf())

    fun setUsers(vararg users: User) {
        userMap.value = users.associateBy { it.id }.toImmutableMap()
    }

    override val users: Flow<ImmutableMap<Long, User>> = userMap

    override fun user(id: Long): Flow<User?> = unused()

    override suspend fun getUser(id: Long): Outcome<User> = unused()
}

/** The chats TDLib announced ([chats]); the viewer only reads their titles. */
class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<Map<Long, Chat>>(emptyMap())

    override fun chat(id: Long): Flow<Chat?> = chats.map { it[id] }.distinctUntilChanged()

    override suspend fun privateChat(userId: Long): Outcome<Chat> = unused()

    override suspend fun getChat(id: Long): Outcome<Chat> = unused()

    override fun chatList(list: ChatListType): Flow<ImmutableList<Chat>> = unused()

    override suspend fun loadChats(
        list: ChatListType,
        limit: Int,
    ): Outcome<Boolean> = unused()

    override val folders: Flow<ImmutableList<ChatFolder>> get() = unused()

    override val mainChatListPosition: Flow<Int> get() = unused()

    override fun unreadChatCount(list: ChatListType): Flow<UnreadChatCount> = unused()

    override suspend fun setPinned(
        chatId: Long,
        list: ChatListType,
        isPinned: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun setMuted(
        chatId: Long,
        isMuted: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun setArchived(
        chatId: Long,
        isArchived: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun markAsRead(chatId: Long): Outcome<Unit> = unused()

    override suspend fun markAsUnread(chatId: Long): Outcome<Unit> = unused()

    override suspend fun deleteOptions(chatId: Long): Outcome<DeleteChatOptions> = unused()

    override suspend fun deleteChat(
        chatId: Long,
        forEveryone: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun openChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun closeChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun viewMessages(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> = unused()
}

/** A wall clock stopped at [now] in UTC. */
class FakeWallClock(
    private val now: Instant,
) : WallClock {
    override val minutes = MutableStateFlow(now)

    override fun now(): Instant = now

    override fun zone(): ZoneId = ZoneOffset.UTC
}

/** Fixed English patterns, as the platform gives them for `Locale.US`, on the 12-hour clock. */
class FakeTimePatterns : TimePatterns {
    override fun is24HourClock(): Boolean = false

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = PATTERNS[skeleton] ?: error("No pattern for $skeleton")

    private companion object {
        val PATTERNS =
            mapOf(
                "MMMdhm" to "MMM d, h:mm a",
                "yMMMdhm" to "MMM d, y, h:mm a",
                "MMMdHm" to "MMM d, HH:mm",
                "yMMMdHm" to "MMM d, y, HH:mm",
            )
    }
}

/**
 * A player that plays nothing: it records what it is told ([calls]), plays at [positionMillis] of [durationMillis],
 * which tests set, and tells its listener what tests make it do: [renderFirstFrame], [end], [pauseBySystem], [fail].
 */
internal class FakeVideoPlayer : VideoPlayer {
    override var listener: VideoPlayer.Listener? = null

    override val surfacePlayer: Player? = null

    override var positionMillis = 0L

    override var durationMillis: Long? = null

    /** `play:<path>:<kind>`, `pause`, `resume`, `seek:<position>`, `stop` and `release`, in order. */
    val calls = mutableListOf<String>()

    override fun play(
        path: String,
        kind: VideoKind,
    ) {
        calls += "play:$path:$kind"
        positionMillis = 0
    }

    override fun pause() {
        calls += "pause"
    }

    override fun resume() {
        calls += "resume"
    }

    override fun seekTo(positionMillis: Long) {
        calls += "seek:$positionMillis"
        this.positionMillis = positionMillis
    }

    override fun stop() {
        calls += "stop"
    }

    override fun release() {
        calls += "release"
    }

    fun renderFirstFrame() = listener?.onFirstFrame()

    fun end() = listener?.onEnded()

    fun pauseBySystem() = listener?.onPlayingChanged(false)

    fun fail() = listener?.onError()
}

/**
 * Saving into a gallery that does what tests say: [needsPermission] as on Android 8–9 without the permission, and
 * [onSave] answering each copy, which goes to [saved] first.
 */
class FakeMediaFileActions : MediaFileActions {
    var needsPermission = false
    var onSave: suspend (GalleryItem) -> Boolean = { true }
    val saved = mutableListOf<GalleryItem>()

    override fun needsStoragePermission(): Boolean = needsPermission

    override suspend fun saveToGallery(item: GalleryItem): Boolean {
        saved += item
        return onSave(item)
    }
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the media viewer")
