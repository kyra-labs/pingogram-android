package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// Fakes of the domain repositories the conversation reads, holding their state in flows the tests set.

/** The chats TDLib announced ([chats]); [getChat] answers with [onGetChat]; opening and closing are recorded. */
class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<Map<Long, Chat>>(emptyMap())

    /** `open:<id>` and `close:<id>`, in order. */
    val openCalls = mutableListOf<String>()

    /** Every [getChat] call. */
    val getChatRequests = mutableListOf<Long>()

    /** The answer to [getChat]: an announced chat, else [DomainError.NotFound]. */
    var onGetChat: suspend (Long) -> Outcome<Chat> = { id ->
        chats.value[id]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
    }

    fun setChats(vararg chats: Chat) {
        this.chats.value = chats.associateBy { it.id }
    }

    override fun chat(id: Long): Flow<Chat?> = chats.map { it[id] }.distinctUntilChanged()

    override suspend fun getChat(id: Long): Outcome<Chat> {
        getChatRequests += id
        return onGetChat(id)
    }

    override suspend fun openChat(chatId: Long): Outcome<Unit> {
        openCalls += "open:$chatId"
        return Outcome.Success(Unit)
    }

    override suspend fun closeChat(chatId: Long): Outcome<Unit> {
        openCalls += "close:$chatId"
        return Outcome.Success(Unit)
    }

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
}

/**
 * The history of each chat ([histories]), empty and not at the start until set. [loadOlder] is recorded and answers
 * with [onLoadOlder], which by default succeeds without changing anything; [repliedMessage] with [onRepliedMessage].
 */
class FakeMessageRepository : MessageRepository {
    val histories = MutableStateFlow<Map<Long, ChatHistory>>(emptyMap())

    /** The chat of every [loadOlder] call. */
    val loadRequests = mutableListOf<Long>()

    var onLoadOlder: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Every [repliedMessage] call: the chat and the replying message. */
    val repliedRequests = mutableListOf<Pair<Long, Long>>()

    var onRepliedMessage: suspend (Long, Long) -> Outcome<Message> = { _, _ -> Outcome.Failure(DomainError.NotFound) }

    fun setHistory(
        chatId: Long,
        history: ChatHistory,
    ) {
        histories.value += chatId to history
    }

    /** Answers the next [loadOlder] calls in turn: each sets its history (or fails), then the start is reached. */
    fun answerLoads(
        chatId: Long,
        vararg pages: Outcome<ChatHistory>,
    ) {
        val answers = ArrayDeque(pages.toList())
        onLoadOlder = {
            when (val page = answers.removeFirstOrNull()) {
                null -> {
                    setHistory(
                        chatId,
                        histories.value[chatId]?.copy(reachedStart = true) ?: EMPTY.copy(reachedStart = true),
                    )
                    Outcome.Success(Unit)
                }

                is Outcome.Success -> {
                    setHistory(chatId, page.value)
                    Outcome.Success(Unit)
                }

                is Outcome.Failure -> {
                    page
                }
            }
        }
    }

    override fun history(chatId: Long): Flow<ChatHistory> = histories.map { it[chatId] ?: EMPTY }.distinctUntilChanged()

    override suspend fun loadOlder(chatId: Long): Outcome<Unit> {
        loadRequests += chatId
        return onLoadOlder(chatId)
    }

    override suspend fun repliedMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message> {
        repliedRequests += chatId to messageId
        return onRepliedMessage(chatId, messageId)
    }

    override fun trimMemory() = unused()

    private companion object {
        val EMPTY = ChatHistory(persistentListOf(), reachedStart = false)
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

class FakeGroupRepository : GroupRepository {
    val basicGroupMap = MutableStateFlow<Map<Long, BasicGroup>>(emptyMap())
    val supergroupMap = MutableStateFlow<Map<Long, Supergroup>>(emptyMap())

    override val basicGroups: Flow<ImmutableMap<Long, BasicGroup>> get() = unused()

    override fun basicGroup(id: Long): Flow<BasicGroup?> = basicGroupMap.map { it[id] }.distinctUntilChanged()

    override val supergroups: Flow<ImmutableMap<Long, Supergroup>> get() = unused()

    override fun supergroup(id: Long): Flow<Supergroup?> = supergroupMap.map { it[id] }.distinctUntilChanged()
}

class FakeChatActionRepository : ChatActionRepository {
    val actionMap = MutableStateFlow<Map<Long, ImmutableList<SenderAction>>>(emptyMap())

    fun setActions(
        chatId: Long,
        vararg actions: SenderAction,
    ) {
        actionMap.value += chatId to actions.toList().toImmutableList()
    }

    override val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>> get() = unused()

    override fun actions(chatId: Long): Flow<ImmutableList<SenderAction>> =
        actionMap.map { it[chatId] ?: persistentListOf() }.distinctUntilChanged()
}

/** A wall clock at [now] in UTC; [setTime] moves it and emits the new time as the minute. */
class FakeWallClock(
    now: Instant,
) : WallClock {
    private var current = now

    override val minutes = MutableStateFlow(now)

    override fun now(): Instant = current

    override fun zone(): ZoneId = ZoneOffset.UTC

    fun setTime(time: Instant) {
        current = time
        minutes.value = time
    }
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
                "Hm" to "HH:mm",
                "hm" to "h:mm a",
                "MMMMEEEEd" to "EEEE, MMMM d",
                "yMMMMd" to "MMMM d, y",
                "MMMd" to "MMM d",
                "yMd" to "M/d/y",
            )
    }
}

/**
 * Files by id ([files]): [observe] follows a file's entry and emits nothing while it has none, as TDLib does for a file
 * it has not reported. Downloads and cancels are recorded and answered with [onDownload] and [onCancel].
 */
class FakeFileRepository : FileRepository {
    val files = MutableStateFlow<Map<Int, FileState>>(emptyMap())

    /** `download:<id>:<priority>` and `cancel:<id>`, in order. */
    val calls = mutableListOf<String>()

    /** The ids every [observe] call asked for, in order. */
    val observed = mutableListOf<Int>()

    var onDownload: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onCancel: suspend (Int) -> Outcome<Unit> = { Outcome.Success(Unit) }

    fun setFile(
        fileId: Int,
        state: FileState,
    ) {
        files.value += fileId to state
    }

    override fun observe(fileId: Int): Flow<FileState> {
        observed += fileId
        return files.mapNotNull { it[fileId] }.distinctUntilChanged()
    }

    override suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit> {
        calls += "download:$fileId:$priority"
        return onDownload(fileId)
    }

    override suspend fun cancelDownload(fileId: Int): Outcome<Unit> {
        calls += "cancel:$fileId"
        return onCancel(fileId)
    }
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the conversation")
