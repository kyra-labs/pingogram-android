package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardOptions
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.SharedFile
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioKind
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// Fakes of the domain repositories the conversation reads, holding their state in flows the tests set.

/**
 * The chats TDLib announced ([chats]); [getChat] answers with [onGetChat]; opening and closing are recorded, and so
 * are the messages viewed, which [onViewMessages] answers. The main chat list is [mainList]; [loadChats] counts its
 * calls and answers with [onLoadChats]; [privateChat] records the user and answers with the announced chat.
 */
class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<Map<Long, Chat>>(emptyMap())

    /** `open:<id>` and `close:<id>`, in order. */
    val openCalls = mutableListOf<String>()

    /** The message ids of every [viewMessages] call, in order. */
    val viewed = mutableListOf<List<Long>>()

    var onViewMessages: suspend (List<Long>) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Every [getChat] call. */
    val getChatRequests = mutableListOf<Long>()

    /** The answer to [getChat]: an announced chat, else [DomainError.NotFound]. */
    var onGetChat: suspend (Long) -> Outcome<Chat> = { id ->
        chats.value[id]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
    }

    fun setChats(vararg chats: Chat) {
        this.chats.value = chats.associateBy { it.id }
    }

    /** The main chat list, in its order. */
    val mainList = MutableStateFlow<List<Chat>>(emptyList())

    /** How often [loadChats] was called. */
    var loadChatsCalls = 0

    var onLoadChats: suspend () -> Outcome<Boolean> = { Outcome.Success(false) }

    /** The users whose private chat [privateChat] was asked for. */
    val privateChatRequests = mutableListOf<Long>()

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

    override suspend fun viewMessages(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> {
        viewed += messageIds
        return onViewMessages(messageIds)
    }

    override fun chatList(list: ChatListType): Flow<ImmutableList<Chat>> =
        if (list == ChatListType.Main) mainList.map { it.toImmutableList() } else unused()

    override suspend fun loadChats(
        list: ChatListType,
        limit: Int,
    ): Outcome<Boolean> {
        loadChatsCalls++
        return onLoadChats()
    }

    override suspend fun privateChat(userId: Long): Outcome<Chat> {
        privateChatRequests += userId
        return chats.value[userId]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
    }

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
 * with [onLoadOlder], which by default succeeds without changing anything; [loadNewer], [loadAround] and [loadNewest]
 * likewise ([loads]); [repliedMessage] with [onRepliedMessage].
 */
class FakeMessageRepository : MessageRepository {
    val histories = MutableStateFlow<Map<Long, ChatHistory>>(emptyMap())

    /** The chat of every [loadOlder] call. */
    val loadRequests = mutableListOf<Long>()

    /** Every load but [loadOlder], in order: `newer`, `newest` and `around:<message id>`. */
    val loads = mutableListOf<String>()

    var onLoadOlder: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }

    var onLoadNewer: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Answers [loadAround] with the chat and the message. */
    var onLoadAround: suspend (Long, Long) -> Outcome<Unit> = { _, _ -> Outcome.Success(Unit) }

    var onLoadNewest: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Every [repliedMessage] call: the chat and the replying message. */
    val repliedRequests = mutableListOf<Pair<Long, Long>>()

    var onRepliedMessage: suspend (Long, Long) -> Outcome<Message> = { _, _ -> Outcome.Failure(DomainError.NotFound) }

    /**
     * Sets the history of [chatId], keeping its window id unless [history] has one: the messages changed, but they are
     * the same run ([replaceHistory] puts another run in place).
     */
    fun setHistory(
        chatId: Long,
        history: ChatHistory,
    ) {
        val windowId = history.windowId.takeIf { it != 0L } ?: histories.value[chatId]?.windowId ?: 0
        histories.value += chatId to history.copy(windowId = windowId)
    }

    /**
     * Answers the next loads in turn, [loadOlder] and [loadNewest] alike: each sets its history (or fails), then the
     * start is reached.
     */
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
        onLoadNewest = onLoadOlder
    }

    /**
     * Answers [loadAround] by putting [history] in place of the chat's (or failing with [failure]), with a new window
     * id, as a page that replaces loaded messages has.
     */
    fun answerAround(
        chatId: Long,
        history: ChatHistory,
        failure: Outcome.Failure? = null,
    ) {
        onLoadAround = { _, _ -> failure ?: Outcome.Success(Unit).also { replaceHistory(chatId, history) } }
    }

    /** Puts [history] in place of the chat's, with a new window id. */
    fun replaceHistory(
        chatId: Long,
        history: ChatHistory,
    ) {
        val windowId = (histories.value[chatId]?.windowId ?: 0) + 1
        setHistory(chatId, history.copy(windowId = windowId))
    }

    override fun history(chatId: Long): Flow<ChatHistory> = histories.map { it[chatId] ?: EMPTY }.distinctUntilChanged()

    override suspend fun loadOlder(chatId: Long): Outcome<Unit> {
        loadRequests += chatId
        return onLoadOlder(chatId)
    }

    override suspend fun loadNewer(chatId: Long): Outcome<Unit> {
        loads += "newer"
        return onLoadNewer(chatId)
    }

    override suspend fun loadAround(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> {
        loads += "around:$messageId"
        return onLoadAround(chatId, messageId)
    }

    override suspend fun loadNewest(chatId: Long): Outcome<Unit> {
        loads += "newest"
        return onLoadNewest(chatId)
    }

    override suspend fun repliedMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message> {
        repliedRequests += chatId to messageId
        return onRepliedMessage(chatId, messageId)
    }

    /** Every [message] call: the chat and the message. */
    val messageRequests = mutableListOf<Pair<Long, Long>>()

    var onMessage: suspend (Long, Long) -> Outcome<Message> = { _, _ -> Outcome.Failure(DomainError.NotFound) }

    override suspend fun message(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message> {
        messageRequests += chatId to messageId
        return onMessage(chatId, messageId)
    }

    override fun trimMemory() = unused()

    /** Every [sendText] call: the chat, the text and the options. */
    val sent = mutableListOf<Triple<Long, FormattedText, SendOptions>>()

    /** The message each [sendText] call replied to, in order; null for none. */
    val sentReplies = mutableListOf<Long?>()

    var onSendText: suspend (FormattedText) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Every [setDraft] call: the chat and the text. */
    val drafts = mutableListOf<Pair<Long, FormattedText>>()

    /** The message each [setDraft] call's draft replied to, in order; null for none. */
    val draftReplies = mutableListOf<Long?>()

    var onSetDraft: suspend (FormattedText) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun sendText(
        chatId: Long,
        text: FormattedText,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sent += Triple(chatId, text, options)
        sentReplies += replyToMessageId
        return onSendText(text)
    }

    override suspend fun setDraft(
        chatId: Long,
        text: FormattedText,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        drafts += chatId to text
        draftReplies += replyToMessageId
        return onSetDraft(text)
    }

    private companion object {
        val EMPTY = ChatHistory(persistentListOf(), reachedStart = false)
    }
}

/**
 * What the message actions ask TDLib: [properties] answers with [onProperties] (everything allowed by default),
 * [availableReactions] with [reactions], [customEmojiStickers] with those of [customEmoji] asked for; every action is
 * recorded in [calls] (`addReaction:<chat>:<message>:<type>`, `delete:<chat>:<ids>:<forEveryone>`, …) and answered with
 * [onAction].
 */
class FakeMessageActionRepository : MessageActionRepository {
    var onProperties: suspend (Long) -> Outcome<MessageProperties> = { Outcome.Success(ALL_ALLOWED) }

    var reactions: Outcome<AvailableReactions> = Outcome.Success(AvailableReactions(persistentListOf()))

    var onAction: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The custom emoji TDLib knows, by id; a failure fails every request. */
    var customEmoji: Outcome<Map<Long, Sticker>> = Outcome.Success(emptyMap())

    /** The custom emoji whose stickers were asked for, by request. */
    val customEmojiRequests = mutableListOf<List<Long>>()

    /** The messages whose properties were asked for, in order. */
    val propertyRequests = mutableListOf<Long>()

    /** The messages whose available reactions were asked for, in order. */
    val reactionRequests = mutableListOf<Long>()

    /** Every action, described, in order. */
    val calls = mutableListOf<String>()

    /** The texts of every edit, in order. */
    val edits = mutableListOf<FormattedText>()

    override suspend fun properties(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageProperties> {
        propertyRequests += messageId
        return onProperties(messageId)
    }

    override suspend fun availableReactions(
        chatId: Long,
        messageId: Long,
    ): Outcome<AvailableReactions> {
        reactionRequests += messageId
        return reactions
    }

    override suspend fun customEmojiStickers(customEmojiIds: List<Long>): Outcome<ImmutableMap<Long, Sticker>> {
        customEmojiRequests += customEmojiIds
        return customEmoji.map { known -> known.filterKeys { it in customEmojiIds }.toImmutableMap() }
    }

    override suspend fun addReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ) = record("addReaction:$chatId:$messageId:$reaction")

    override suspend fun removeReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ) = record("removeReaction:$chatId:$messageId:$reaction")

    override suspend fun editText(
        chatId: Long,
        messageId: Long,
        text: FormattedText,
    ): Outcome<Unit> {
        edits += text
        return record("editText:$chatId:$messageId")
    }

    override suspend fun editCaption(
        chatId: Long,
        messageId: Long,
        caption: FormattedText,
        showCaptionAboveMedia: Boolean,
    ): Outcome<Unit> {
        edits += caption
        return record("editCaption:$chatId:$messageId:$showCaptionAboveMedia")
    }

    override suspend fun delete(
        chatId: Long,
        messageIds: List<Long>,
        forEveryone: Boolean,
    ) = record("delete:$chatId:$messageIds:$forEveryone")

    override suspend fun pin(
        chatId: Long,
        messageId: Long,
        onlyForSelf: Boolean,
    ) = record("pin:$chatId:$messageId:$onlyForSelf")

    override suspend fun unpin(
        chatId: Long,
        messageId: Long,
    ) = record("unpin:$chatId:$messageId")

    override suspend fun resend(
        chatId: Long,
        messageIds: List<Long>,
    ) = record("resend:$chatId:$messageIds")

    /** The comment of every forward, in order; null where there was none. */
    val forwardComments = mutableListOf<FormattedText?>()

    override suspend fun forward(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        comment: FormattedText?,
        options: ForwardOptions,
    ): Outcome<Unit> {
        forwardComments += comment
        val flags = "${options.hideSenderName}:${options.hideCaptions}"
        return record("forward:$fromChatId:$messageIds:$toChatId:$flags")
    }

    /** What [report] answers, by request; reported at once by default. */
    var onReport: suspend (ReportOptionId, String) -> Outcome<ChatReportResult> = { _, _ ->
        Outcome.Success(ChatReportResult.Reported)
    }

    /** `report:<chat>:<messages>:<option bytes>:<text>`, in order. */
    val reports = mutableListOf<String>()

    override suspend fun report(
        chatId: Long,
        messageIds: List<Long>,
        option: ReportOptionId,
        text: String,
    ): Outcome<ChatReportResult> {
        reports += "report:$chatId:$messageIds:${option.bytes.toList()}:$text"
        return onReport(option, text)
    }

    private suspend fun record(call: String): Outcome<Unit> {
        calls += call
        return onAction(call)
    }

    companion object {
        val ALL_ALLOWED =
            MessageProperties(
                canBeReplied = true,
                canBeEdited = true,
                canBePinned = true,
                canBeDeletedOnlyForSelf = true,
                canBeDeletedForAllUsers = true,
                canBeSaved = true,
                canBeForwarded = true,
                canBeCopied = true,
                canBeCopiedToSecretChat = true,
            )
    }
}

/**
 * Shared storage: [needsPermission] as tests set it; every file saved to Downloads is recorded ([savedToDownloads])
 * and answered with [onSave].
 */
class FakeSharedStorageRepository : SharedStorageRepository {
    var needsPermission = false
    var onSave: suspend (SharedFile) -> Boolean = { true }
    val savedToDownloads = mutableListOf<SharedFile>()

    override fun needsStoragePermission(): Boolean = needsPermission

    override suspend fun saveToGallery(file: SharedFile): Boolean = throw UnsupportedOperationException("Not used")

    override suspend fun saveToDownloads(file: SharedFile): Boolean {
        savedToDownloads += file
        return onSave(file)
    }
}

/**
 * Chat search: [searchChats] records each query and emits what [onSearchChats] gives for it (nothing by default); the
 * rest of global search is not used by the conversation.
 */
class FakeSearchRepository : SearchRepository {
    /** Every query searched, in order. */
    val chatQueries = mutableListOf<String>()

    var onSearchChats: (String) -> Flow<Outcome<ImmutableList<Chat>>> = { flowOf(Outcome.Success(persistentListOf())) }

    override fun searchChats(query: String): Flow<Outcome<ImmutableList<Chat>>> {
        chatQueries += query
        return onSearchChats(query)
    }

    override suspend fun searchContacts(query: String): Outcome<ImmutableList<Chat>> = unused()

    override suspend fun contacts(): Outcome<ImmutableList<Chat>> = unused()

    override suspend fun searchPublicChats(query: String): Outcome<ImmutableList<Chat>> = unused()

    override suspend fun searchMessages(
        query: String,
        offset: String?,
        filter: MessageSearchFilter,
    ): Outcome<FoundMessages> = unused()

    override suspend fun recentChats(): Outcome<ImmutableList<Chat>> = unused()

    override suspend fun addRecentChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun removeRecentChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun clearRecentChats(): Outcome<Unit> = unused()

    override suspend fun topPeople(): Outcome<ImmutableList<Chat>> = unused()
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

    override val basicGroups: Flow<ImmutableMap<Long, BasicGroup>> get() = basicGroupMap.map { it.toImmutableMap() }

    override fun basicGroup(id: Long): Flow<BasicGroup?> = basicGroupMap.map { it[id] }.distinctUntilChanged()

    override val supergroups: Flow<ImmutableMap<Long, Supergroup>> get() = supergroupMap.map { it.toImmutableMap() }

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

/** [AutoDownloadPolicy] whose [rules] tests set: photos only, as on mobile data by default, unless [initial]. */
class FakeAutoDownloadPolicy(
    initial: AutoDownloadRules = AutoDownloadRules(photos = true),
) : AutoDownloadPolicy {
    override val rules = MutableStateFlow(initial)
}

/**
 * Files by id ([files]): [observe] follows a file's entry and emits nothing while it has none, as TDLib does for a file
 * it has not reported. Downloads and cancels are recorded and answered with [onDownload] and [onCancel].
 */
class FakeFileRepository : FileRepository {
    val files = MutableStateFlow<Map<Int, FileState>>(emptyMap())

    /** The uploads TDLib reported, by file id; a file not in it has reported none yet. */
    val uploads = MutableStateFlow<Map<Int, FileUpload>>(emptyMap())

    /** The ids every [observeUpload] call asked for, in order. */
    val observedUploads = mutableListOf<Int>()

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

    override fun observeUpload(fileId: Int): Flow<FileUpload> {
        observedUploads += fileId
        return uploads.mapNotNull { it[fileId] }.distinctUntilChanged()
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

/**
 * A player that plays nothing: it records what it is told ([calls]), plays at [positionMillis] of [durationMillis],
 * which tests set, and tells its listener what tests make it do: [end], [pauseBySystem], [fail].
 */
internal class FakeAudioPlayer : AudioPlayer {
    override var listener: AudioPlayer.Listener? = null

    override var positionMillis = 0L

    override var durationMillis: Long? = null

    /** `play:<path>:<kind>:<position>`, `pause`, `resume`, `seek:<position>`, `stop` and `release`, in order. */
    val calls = mutableListOf<String>()

    override fun play(
        path: String,
        kind: AudioKind,
        positionMillis: Long,
    ) {
        calls += "play:$path:$kind:$positionMillis"
        this.positionMillis = positionMillis
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

    /** The file played to its end. */
    fun end() {
        positionMillis = durationMillis ?: positionMillis
        listener?.onEnded()
    }

    /** The system paused it: audio focus lost, headphones unplugged. */
    fun pauseBySystem() = listener?.onPlayingChanged(false)

    fun fail() = listener?.onError()
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the conversation")
