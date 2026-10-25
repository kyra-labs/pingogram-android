package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettingsAvailability
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// Fakes of the domain repositories the chat list reads, holding their state in flows the tests set.

/**
 * The chats of each list, the folders and their counts; [loadChats] answers with [onLoadChats] and is recorded, and so
 * is every action on a chat ([calls]), answered with [onCall].
 */
class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<Map<ChatListType, List<Chat>>>(emptyMap())
    val folderList = MutableStateFlow<ImmutableList<ChatFolder>>(persistentListOf())
    val mainPosition = MutableStateFlow(0)
    val unreadCounts = MutableStateFlow<Map<ChatListType, UnreadChatCount>>(emptyMap())

    /** Every [loadChats] call: the list and the limit. */
    val loadRequests = mutableListOf<Pair<ChatListType, Int>>()

    /** Answers to the next [loadChats] calls, in order ([answerLoads]); the default [onLoadChats] takes them. */
    private val loadAnswers = ArrayDeque<Outcome<Boolean>>()

    /** The answer to [loadChats]: the next of [answerLoads], then that the list is complete. */
    var onLoadChats: suspend (ChatListType, Int) -> Outcome<Boolean> = { _, _ ->
        loadAnswers.removeFirstOrNull() ?: Outcome.Success(false)
    }

    /** Answers the next [loadChats] calls with [answers], in order; after them the list is complete. */
    fun answerLoads(vararg answers: Outcome<Boolean>) {
        loadAnswers += answers
    }

    fun setChats(
        list: ChatListType,
        vararg chats: Chat,
    ) {
        this.chats.value += list to chats.toList()
    }

    override fun chatList(list: ChatListType): Flow<ImmutableList<Chat>> =
        chats.map { it[list].orEmpty().toImmutableList() }.distinctUntilChanged()

    override suspend fun loadChats(
        list: ChatListType,
        limit: Int,
    ): Outcome<Boolean> {
        loadRequests += list to limit
        return onLoadChats(list, limit)
    }

    override val folders: Flow<ImmutableList<ChatFolder>> = folderList

    override val mainChatListPosition: Flow<Int> = mainPosition

    override fun unreadChatCount(list: ChatListType): Flow<UnreadChatCount> =
        unreadCounts.map { it[list] ?: UnreadChatCount.None }.distinctUntilChanged()

    /** A chat of any list, as [setChats] put it. */
    override fun chat(id: Long): Flow<Chat?> =
        chats
            .map { lists ->
                lists.values
                    .asSequence()
                    .flatten()
                    .firstOrNull { it.id == id }
            }.distinctUntilChanged()

    /** A chat of any list, as the repository keeps them in memory; [DomainError.NotFound] otherwise. */
    override suspend fun privateChat(userId: Long): Outcome<Chat> = unused()

    override suspend fun getChat(id: Long): Outcome<Chat> =
        chats.value.values
            .flatten()
            .firstOrNull { it.id == id }
            ?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)

    /** Every action on a chat, in order. */
    val calls = mutableListOf<ChatCall>()

    /** The answer to each action; succeeds by default. */
    var onCall: suspend (ChatCall) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The answer to [deleteOptions]: deleting the conversation for the current user only, by default. */
    var onDeleteOptions: suspend (Long) -> Outcome<DeleteChatOptions> = {
        Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = false))
    }

    private suspend fun record(call: ChatCall): Outcome<Unit> {
        calls += call
        return onCall(call)
    }

    override suspend fun setPinned(
        chatId: Long,
        list: ChatListType,
        isPinned: Boolean,
    ): Outcome<Unit> = record(ChatCall.SetPinned(chatId, list, isPinned))

    override suspend fun setMuted(
        chatId: Long,
        isMuted: Boolean,
    ): Outcome<Unit> = record(ChatCall.SetMuted(chatId, isMuted))

    override suspend fun setArchived(
        chatId: Long,
        isArchived: Boolean,
    ): Outcome<Unit> = record(ChatCall.SetArchived(chatId, isArchived))

    override suspend fun markAsRead(chatId: Long): Outcome<Unit> = record(ChatCall.MarkAsRead(chatId))

    override suspend fun markAsUnread(chatId: Long): Outcome<Unit> = record(ChatCall.MarkAsUnread(chatId))

    override suspend fun deleteOptions(chatId: Long): Outcome<DeleteChatOptions> = onDeleteOptions(chatId)

    override suspend fun deleteChat(
        chatId: Long,
        forEveryone: Boolean,
    ): Outcome<Unit> = record(ChatCall.DeleteChat(chatId, forEveryone))

    override suspend fun openChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun closeChat(chatId: Long): Outcome<Unit> = unused()

    override suspend fun viewMessages(
        chatId: Long,
        messageIds: List<Long>,
        topic: MessageTopic?,
    ): Outcome<Unit> = unused()
}

/** An action [FakeChatRepository] was asked for. */
sealed interface ChatCall {
    val chatId: Long

    data class SetPinned(
        override val chatId: Long,
        val list: ChatListType,
        val isPinned: Boolean,
    ) : ChatCall

    data class SetMuted(
        override val chatId: Long,
        val isMuted: Boolean,
    ) : ChatCall

    data class SetArchived(
        override val chatId: Long,
        val isArchived: Boolean,
    ) : ChatCall

    data class MarkAsRead(
        override val chatId: Long,
    ) : ChatCall

    data class MarkAsUnread(
        override val chatId: Long,
    ) : ChatCall

    data class DeleteChat(
        override val chatId: Long,
        val forEveryone: Boolean,
    ) : ChatCall
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
    val supergroupMap = MutableStateFlow<ImmutableMap<Long, Supergroup>>(persistentMapOf())

    override val basicGroups: Flow<ImmutableMap<Long, BasicGroup>> = MutableStateFlow(persistentMapOf())

    override fun basicGroup(id: Long): Flow<BasicGroup?> = unused()

    override val supergroups: Flow<ImmutableMap<Long, Supergroup>> = supergroupMap

    override fun supergroup(id: Long): Flow<Supergroup?> = unused()
}

class FakeSecretChatRepository : SecretChatRepository {
    val secretChatMap = MutableStateFlow<ImmutableMap<Int, SecretChat>>(persistentMapOf())

    fun setSecretChats(vararg secretChats: SecretChat) {
        secretChatMap.value = secretChats.associateBy { it.id }.toImmutableMap()
    }

    override val secretChats: Flow<ImmutableMap<Int, SecretChat>> = secretChatMap

    override fun secretChat(id: Int): Flow<SecretChat?> = unused()

    override suspend fun createSecretChat(userId: Long): Outcome<Long> = unused()

    override suspend fun closeSecretChat(secretChatId: Int): Outcome<Unit> = unused()

    override suspend fun setSelfDestructTime(
        chatId: Long,
        seconds: Int,
    ): Outcome<Unit> = unused()
}

class FakeChatActionRepository : ChatActionRepository {
    val actions = MutableStateFlow<ImmutableMap<Long, ImmutableList<SenderAction>>>(persistentMapOf())

    fun setActions(
        chatId: Long,
        vararg actions: SenderAction,
    ) {
        this.actions.value = (this.actions.value + (chatId to actions.toList().toImmutableList())).toImmutableMap()
    }

    override val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>> = actions

    override fun actions(chatId: Long): Flow<ImmutableList<SenderAction>> = unused()
}

class FakeConnectionRepository : ConnectionRepository {
    override val connectionStatus = MutableStateFlow(ConnectionStatus.Ready)
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

/** Fixed English patterns, as the platform gives them for `Locale.US`; [uses24HourClock] is the system setting. */
class FakeTimePatterns(
    var uses24HourClock: Boolean = false,
) : TimePatterns {
    override fun is24HourClock(): Boolean = uses24HourClock

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = PATTERNS[skeleton] ?: error("No pattern for $skeleton")

    private companion object {
        val PATTERNS = mapOf("Hm" to "HH:mm", "hm" to "h:mm a", "MMMd" to "MMM d", "yMd" to "M/d/y")
    }
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the chat list")

/** The archive row's style as [style] holds it; [setStyle] answers with [onSetStyle] and is recorded. */
class FakeArchiveRowRepository : ArchiveRowRepository {
    override val style = MutableStateFlow(ArchiveRowStyle.Full)

    /** Every style saved, in order. */
    val saved = mutableListOf<ArchiveRowStyle>()

    var onSetStyle: suspend (ArchiveRowStyle) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun setStyle(style: ArchiveRowStyle): Outcome<Unit> {
        saved += style
        return onSetStyle(style).also { if (it is Outcome.Success) this.style.value = style }
    }
}

/**
 * The archive's settings as [settings] holds them, answered by [onRead]; every change ([changes]) and every list read
 * ([readLists]) is recorded and answered by [onSet] and [onReadList].
 */
class FakeArchiveSettingsRepository : ArchiveSettingsRepository {
    var settings = ArchiveSettingsAvailability(ArchiveSettings(), canArchiveAndMuteNewChats = true)

    var onRead: suspend () -> Outcome<ArchiveSettingsAvailability> = { Outcome.Success(settings) }
    var onSet: suspend (ArchiveSettings) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onReadList: suspend (ChatListType) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Every [archiveSettings] call. */
    var reads = 0
    val changes = mutableListOf<ArchiveSettings>()
    val readLists = mutableListOf<ChatListType>()

    override suspend fun archiveSettings(): Outcome<ArchiveSettingsAvailability> {
        reads++
        return onRead()
    }

    override suspend fun setArchiveSettings(settings: ArchiveSettings): Outcome<Unit> {
        changes += settings
        return onSet(settings)
    }

    override suspend fun readChatList(list: ChatListType): Outcome<Unit> {
        readLists += list
        return onReadList(list)
    }
}
