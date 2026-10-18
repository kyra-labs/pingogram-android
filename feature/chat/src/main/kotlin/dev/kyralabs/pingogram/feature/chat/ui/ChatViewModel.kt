package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioPlayer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The conversation of chat [chatId] (`REQUIREMENTS.md` FR-C1–C3, FR-C8), read-only for now, as [uiState]. Events come
 * in through [onEvent].
 *
 * - **State**: one `combine` of the chat, its history, the users and the chats the messages name, the replied messages
 *   fetched for it, the member count of a group or channel, what people are doing in it, the minute
 *   ([WallClock.minutes], for "last seen" and "Today"), and the paging state. Items are mapped on [dispatcher] by a
 *   [ChatItemMapper], which reuses items whose message did not change. It runs while the screen collects, and 5 s
 *   after, so a rotation does not restart it.
 * - **The chat** comes from [ChatRepository.chat], or from [ChatRepository.getChat] while TDLib has not announced it:
 *   [ChatContent.NotFound] for a chat TDLib does not know, [ChatContent.Error] with a retry for other failures.
 * - **Paging**: the newest page loads at once when the history is empty, then older pages for as long as the screen
 *   reports that the oldest message is near ([ChatEvent.NearOldestChanged]), one at a time, until the start. A
 *   failure shows [ChatContent.Error] while there are no messages, [ChatContent.Content.olderError] otherwise; nothing
 *   is retried by itself: [ChatEvent.Retry], or the oldest message coming near again, tries again.
 * - **Replies** whose replied message is not loaded fetch it once ([MessageRepository.repliedMessage]); the quote
 *   shows that it loads, or that the message was deleted.
 * - **Unread divider**: before the first incoming message after the last one read when the chat was first shown
 *   (`lastReadInboxMessageId`, if anything was unread), kept in the [SavedStateHandle], so it stays where it was
 *   while the screen lives, also across process death, whatever arrives or is read meanwhile.
 * - **Open and close**: [ChatEvent.Started] opens the chat in TDLib and [ChatEvent.Stopped] closes it, from the
 *   screen's lifecycle (started and stopped, or left), each once per change. They are sent before [onEvent] returns,
 *   so a close sent as the screen leaves is not lost with the view model's scope.
 * - **Read** ([MessageViews]): the messages the screen shows ([ChatEvent.MessagesVisible]) go to TDLib as viewed while
 *   the chat is open, which reads them; [Chat.unreadCount], the jump button's badge, follows.
 * - **Times** follow the zone, the language and the 12- or 24-hour setting ([TimePatterns]), checked at every emission.
 * - **Files** ([ChatFiles]): the files of the loaded voice messages, songs and documents are followed through
 *   [FileRepository.observe] into [ChatUiState.fileStates], on [dispatcher], for as long as they are loaded. Documents
 *   download and cancel on request. Failures go out as [ChatEffect.ShowError] snackbars.
 * - **Playback** ([ChatPlayback]): voice messages and songs play one at a time on the [AudioPlayer], downloading their
 *   file first if needed, into [ChatUiState.playback]; the position moves in snapshot state, so it re-emits nothing.
 *   Stopping the screen pauses, and the player is released with the view model.
 * - **Effects** ([effects]): errors, opening a location in a maps app, and the note that opening files comes later.
 *
 * It takes one repository per kind of data the screen combines, plus the player, clock, dispatcher, language, time
 * patterns and log that tests replace, hence `LongParameterList` is suppressed. Hilt reads [chatId] from the route.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    chatActionRepository: ChatActionRepository,
    fileRepository: FileRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    audioPlayer: AudioPlayer,
    private val log: ChatLog,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        messageRepository: MessageRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatActionRepository: ChatActionRepository,
        fileRepository: FileRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        audioPlayer: AudioPlayer,
    ) : this(
        savedStateHandle.toRoute<ChatRoute>().chatId,
        savedStateHandle,
        chatRepository,
        messageRepository,
        userRepository,
        groupRepository,
        chatActionRepository,
        fileRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
        audioPlayer,
        AndroidChatLog,
    )

    /** Each retry of finding the chat starts it again. */
    private val chatAttempts = MutableStateFlow(0)

    /** How far older pages have loaded; changed on the main thread only. */
    private val pageLoad = MutableStateFlow(PageLoad())

    /** The replied messages fetched for messages whose replied message is not loaded, by replying message id. */
    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())

    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The screen shows the oldest message, or close to it ([ChatEvent.NearOldestChanged]); main thread only. */
    private var isNearOldest = false

    /** Whether the chat is open in TDLib ([ChatEvent.Started]); main thread only. */
    private var isOpen = false

    private val mapper = ChatItemMapper()
    private var timeFormatter: ChatTimeFormatter? = null

    private val effectChannel = Channel<ChatEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: snackbars and opening a location. */
    val effects: Flow<ChatEffect> = effectChannel.receiveAsFlow()

    private val files = ChatFiles(fileRepository, viewModelScope) { effectChannel.trySend(ChatEffect.ShowError(it)) }

    private val views = MessageViews(chatId, chatRepository, viewModelScope, log)

    private val playback =
        ChatPlayback(
            player = audioPlayer,
            files = fileRepository,
            scope = viewModelScope,
            history = { messageRepository.history(chatId).first() },
            onError = { effectChannel.trySend(ChatEffect.ShowError(it)) },
            onPlaybackFailed = { effectChannel.trySend(ChatEffect.PlaybackFailed) },
            log = log,
        )

    private val chat: Flow<ChatLookup> =
        chatAttempts
            .flatMapLatest {
                chatRepository.chat(chatId).transformLatest { chat ->
                    if (chat != null) {
                        emit(ChatLookup.Found(chat))
                    } else {
                        emit(ChatLookup.Pending)
                        // Found, TDLib announces it too, and chat() follows.
                        when (val found = chatRepository.getChat(chatId)) {
                            is Outcome.Success -> emit(ChatLookup.Found(found.value))
                            is Outcome.Failure -> emit(ChatLookup.Failed(found.error))
                        }
                    }
                }
            }.distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val history: Flow<ChatHistory> =
        messageRepository
            .history(chatId)
            .onEach(::fetchMissingReplies)
            .flowOn(dispatcher)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The member count of a group or channel, null for other chats and while unknown. */
    private val memberCount: Flow<Int?> =
        chat
            .map { (it as? ChatLookup.Found)?.chat?.type }
            .distinctUntilChanged()
            .flatMapLatest { type ->
                when (type) {
                    is ChatType.BasicGroup -> groupRepository.basicGroup(type.basicGroupId).map { it?.memberCount }
                    is ChatType.Supergroup -> groupRepository.supergroup(type.supergroupId).map { it?.memberCount }
                    else -> flowOf(null)
                }
            }.distinctUntilChanged()

    /** The chats the messages name, by id: their titles and photos, which change far less often than the chats. */
    private val referencedChats: Flow<ImmutableMap<Long, ChatRef>> =
        combine(history, replies) { history, replies -> referencedChatIds(chatId, history, replies) }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map(chatRepository::chatRef)) { refs ->
                        refs.filterNotNull().associateBy { it.id }.toImmutableMap()
                    }
                }
            }

    private val chatSources: Flow<ChatSources> =
        combine(
            chat,
            memberCount,
            chatActionRepository.actions(chatId),
            savedStateHandle.getStateFlow<Long?>(KEY_UNREAD_AFTER, null),
            ::ChatSources,
        )

    private val messageSources: Flow<MessageSources> =
        combine(history, replies, revealedSpoilers, pageLoad) { history, replies, revealed, load ->
            MessageSources(MessageState(history, replies, revealed), load)
        }

    private val people: Flow<ChatPeople> =
        combine(userRepository.users, userRepository.myUserId, referencedChats, ::ChatPeople)

    val uiState: StateFlow<ChatUiState> =
        combine(chatSources, messageSources, people, clock.minutes, ::state)
            .combine(files.statesOf(history)) { state, fileStates -> state.copy(fileStates = fileStates) }
            .combine(playback.state) { state, playback -> state.copy(playback = playback) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatUiState())

    init {
        // Leaving the screen frees the player.
        addCloseable { playback.release() }
        viewModelScope.launch {
            // The divider stays where it was when the chat was first shown, also after process death.
            val chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat }.first()
            if (savedStateHandle.get<Long>(KEY_UNREAD_AFTER) == null) {
                savedStateHandle[KEY_UNREAD_AFTER] =
                    if (chat.unreadCount > 0) chat.lastReadInboxMessageId else NO_UNREAD_DIVIDER
            }
        }
        viewModelScope.launch {
            val first = messageRepository.history(chatId).first()
            if (first.messages.isEmpty() && !first.reachedStart) loadPage()
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Started -> setOpen(true)
            ChatEvent.Stopped -> setOpen(false)
            is ChatEvent.NearOldestChanged -> onNearOldestChanged(event.isNearOldest)
            is ChatEvent.MessagesVisible -> views.setVisible(event.messageIds)
            ChatEvent.Retry -> retry()
            is ChatEvent.RevealSpoilers -> revealedSpoilers.update { it.adding(event.messageId) }
            is ChatEvent.OpenMedia -> Unit
            is ChatEvent.DownloadFile -> files.download(event.fileId)
            is ChatEvent.CancelDownload -> files.cancelDownload(event.fileId)
            is ChatEvent.OpenFile -> effectChannel.trySend(ChatEffect.FileOpeningUnavailable)
            is ChatEvent.OpenLocation -> uiState.value.locationOf(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.PlayPause -> playback.playPause(event.messageId)
            is ChatEvent.Seek -> playback.seek(event.messageId, event.fraction)
        }
    }

    /**
     * Opens or closes the chat in TDLib when that changes. Undispatched, so the request is sent before this returns:
     * a close as the screen leaves, right before the view model's scope is cancelled, still reaches TDLib. A failure
     * changes nothing: TDLib then just does not prioritize the chat. Stopping also pauses what plays: nothing plays in
     * the background.
     */
    private fun setOpen(open: Boolean) {
        if (open == isOpen) return
        isOpen = open
        if (!open) {
            playback.pause()
            views.setOpen(false)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (open) chatRepository.openChat(chatId) else chatRepository.closeChat(chatId)
        }
        // Once openChat is sent: TDLib reads the messages viewed in an open chat only.
        if (open) views.setOpen(true)
    }

    /** The oldest message coming near loads the next page, also after a failure; once at the start nothing. */
    private fun onNearOldestChanged(nearOldest: Boolean) {
        isNearOldest = nearOldest
        val reachedStart = (uiState.value.content as? ChatContent.Content)?.reachedStart == true
        if (nearOldest && !reachedStart) loadPage()
    }

    /** Finds the chat again if that failed, and loads the page that failed. */
    private fun retry() {
        val content = uiState.value.content
        if (content is ChatContent.Error && pageLoad.value.error == null) chatAttempts.update { it + 1 }
        if (pageLoad.value.error != null) loadPage()
    }

    /**
     * Loads the page before the oldest loaded message; nothing while one loads. A page that succeeds while the screen
     * still shows the oldest message loads the next one, until the start; a failed page waits for [ChatEvent.Retry] or
     * the oldest message coming near again.
     */
    private fun loadPage() {
        val load = pageLoad.value
        if (load.isLoading) return
        pageLoad.value = load.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = messageRepository.loadOlder(chatId)
            // The repository has applied the page by now; a fresh look tells whether it reached the start.
            val reachedStart = result is Outcome.Success && messageRepository.history(chatId).first().reachedStart
            pageLoad.value = PageLoad(isLoading = false, error = (result as? Outcome.Failure)?.error)
            if (result is Outcome.Success && !reachedStart && isNearOldest) loadPage()
        }
    }

    /**
     * Asks once for each replied message that is not in [history], for messages of this chat: the quote shows it
     * once it is there. A failure is kept, and shown; it is not asked for again.
     */
    private fun fetchMissingReplies(history: ChatHistory) {
        val missing =
            history.messages.filter { message ->
                val replyTo = message.replyTo as? ReplyTo.Message
                val loaded = replyTo?.chatId == chatId && history.messages.findById(replyTo.messageId) != null
                replyTo != null && replyTo.messageId != 0L && !loaded && message.id !in replies.value
            }
        for (message in missing) {
            var added = false
            replies.update { replies ->
                added = message.id !in replies
                if (added) replies.putting(message.id, ReplyState.Pending) else replies
            }
            if (added) {
                viewModelScope.launch {
                    val state =
                        when (val replied = messageRepository.repliedMessage(chatId, message.id)) {
                            is Outcome.Success -> ReplyState.Loaded(replied.value)
                            is Outcome.Failure -> ReplyState.Failed(replied.error)
                        }
                    replies.update { it.putting(message.id, state) }
                }
            }
        }
    }

    /** Runs on [dispatcher]: maps the header and the items. */
    private fun state(
        chatSources: ChatSources,
        messages: MessageSources,
        people: ChatPeople,
        now: Instant,
    ): ChatUiState {
        val lookup = chatSources.chat
        val chat = (lookup as? ChatLookup.Found)?.chat
        val unreadAfterId = chatSources.unreadAfterId
        if (chat == null || unreadAfterId == null) {
            return ChatUiState(content = (lookup as? ChatLookup.Failed)?.error.toContent())
        }
        val formatter = timeFormatter()
        val header =
            HeaderSources(
                chat = chat,
                memberCount = chatSources.memberCount,
                actions = chatSources.actions,
                people = people,
                now = now,
                timeFormatter = formatter,
            ).toHeader()
        return ChatUiState(
            header = header,
            content = content(chat, messages, people, unreadAfterId, now, formatter),
            unreadCount = chat.unreadCount,
        )
    }

    @Suppress("LongParameterList") // What the items are made from, passed on to the mapper.
    private fun content(
        chat: Chat,
        messages: MessageSources,
        people: ChatPeople,
        unreadAfterId: Long,
        now: Instant,
        formatter: ChatTimeFormatter,
    ): ChatContent {
        val history = messages.state.history
        val load = messages.pageLoad
        val error = load.error
        return when {
            history.messages.isNotEmpty() -> {
                val items =
                    mapper.map(
                        ItemSources(
                            chat = chat,
                            messages = messages.state,
                            people = people,
                            unreadAfterId = unreadAfterId,
                            today = now.atZone(formatter.zone).toLocalDate(),
                            timeFormatter = formatter,
                        ),
                    )
                ChatContent.Content(items, history.reachedStart, load.isLoading, error)
            }

            error != null -> {
                ChatContent.Error(error)
            }

            history.reachedStart -> {
                ChatContent.Empty
            }

            else -> {
                ChatContent.Loading
            }
        }
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** How far older pages have loaded. */
    private data class PageLoad(
        val isLoading: Boolean = false,
        val error: DomainError? = null,
    )

    /** The chat, or why it is not there (yet). */
    private sealed interface ChatLookup {
        data object Pending : ChatLookup

        data class Found(
            val chat: Chat,
        ) : ChatLookup

        data class Failed(
            val error: DomainError,
        ) : ChatLookup
    }

    private class ChatSources(
        val chat: ChatLookup,
        val memberCount: Int?,
        val actions: List<SenderAction>,
        /** Null until the chat was first shown. */
        val unreadAfterId: Long?,
    )

    private class MessageSources(
        val state: MessageState,
        val pageLoad: PageLoad,
    )

    internal companion object {
        /** The last message read when the chat was first shown ([NO_UNREAD_DIVIDER] if nothing was unread). */
        const val KEY_UNREAD_AFTER = "chat.unreadAfterId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The location or venue of loaded message [messageId], as the effect that shows it in a maps app, named after the
 * venue; null for other messages.
 */
private fun ChatUiState.locationOf(messageId: Long): ChatEffect.OpenLocation? {
    val bubble =
        (content as? ChatContent.Content)
            ?.items
            ?.firstOrNull { it is ChatItem.Bubble && it.id == messageId } as? ChatItem.Bubble
    return when (val message = bubble?.content) {
        is MessageContent.Location -> {
            ChatEffect.OpenLocation(message.location.latitude, message.location.longitude, label = null)
        }

        is MessageContent.Venue -> {
            val venue = message.venue
            ChatEffect.OpenLocation(venue.location.latitude, venue.location.longitude, venue.title.ifBlank { null })
        }

        else -> {
            null
        }
    }
}

/** The title and photo of chat [id], then every change of them; null while TDLib has not announced the chat. */
private fun ChatRepository.chatRef(id: Long): Flow<ChatRef?> =
    chat(id)
        .map { chat -> chat?.let { ChatRef(it.id, it.title, it.photo) } }
        .distinctUntilChanged()

/** Not found for a chat TDLib does not know, an error with a retry for other failures; loading without a failure. */
private fun DomainError?.toContent(): ChatContent =
    when (this) {
        null -> ChatContent.Loading
        DomainError.NotFound -> ChatContent.NotFound
        else -> ChatContent.Error(this)
    }
