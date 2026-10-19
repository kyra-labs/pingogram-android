package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerTargets
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.composerState
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.content.opensInViewer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The conversation of chat [chatId] (`REQUIREMENTS.md` FR-C1–C3, FR-C5, FR-C8) as [uiState], and the text of its
 * composer as [composerText]. Events come in through [onEvent].
 *
 * - **State**: one `combine` of the chat, its history, the users and the chats the messages name, the replied messages
 *   fetched for it, the basic group or supergroup of a group or channel, what people are doing in it, the minute
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
 *   shows that it loads, or that the message was deleted. Tapping a quote ([ChatEvent.OpenReply]) scrolls to the
 *   replied message ([ChatEffect.ScrollToMessage]), loading older pages for it first, up to [MAX_REPLY_PAGES], as the
 *   older pages' indicator shows; [ChatEffect.MessageUnavailable] when it is not found. A new tap, or leaving the
 *   screen, cancels the search.
 * - **Opened at a message** ([initialMessageId], a message search found): the same search runs once the view model is
 *   created, up to [MAX_OPEN_PAGES], and scrolls to the message and highlights it; not found, the conversation stays
 *   at the newest messages with [ChatEffect.MessageNotLoaded]. Done once, also across process death (the
 *   [SavedStateHandle] remembers it). Loading pages around a message far back, rather than every page up to it, is
 *   left for later.
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
 * - **Composer** ([ChatComposer]): shown where the user can write ([composerState], from the chat's permissions and the
 *   user's membership), it sends the text ([ChatEvent.Send]) and keeps the chat's draft in step with it: filled from
 *   the draft, saved a second after typing stops and when the screen stops, kept across process death. It replies and
 *   edits too, with a bar for the message ([composerBar]), fetched by [ComposerTargets] when it is not loaded.
 * - **Message actions** ([ChatMessageActions]): the actions sheet of a long-pressed message, with what TDLib allows for
 *   it ([MessageActionRepository.properties]) and its quick reactions; replying, copying, editing, pinning, deleting
 *   (after a confirmation), sending again and toggling reactions; and the [ChatUiState.shortcuts] screen readers get.
 * - **Effects** ([effects]): errors, opening a photo, video or GIF in the media viewer ([ChatEvent.OpenMedia]),
 *   opening a downloaded document in another app ([ChatEvent.OpenFile]), a location in a maps app, a scheduled
 *   message's time, a copied text, and the composer's focus when a reply or an edit starts.
 *
 * It takes one repository per kind of data the screen combines or changes, plus the player, clock, dispatcher,
 * language, time patterns and log that tests replace, hence `LongParameterList` is suppressed. The conversation
 * destination and the detail pane of the Chats tab create it with their [chatId] (and the destination with its route's
 * [initialMessageId]) through [Factory].
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    messageActionRepository: MessageActionRepository,
    private val userRepository: UserRepository,
    groupRepository: GroupRepository,
    chatActionRepository: ChatActionRepository,
    fileRepository: FileRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    audioPlayer: AudioPlayer,
    private val log: ChatLog,
    private val initialMessageId: Long? = null,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted("chatId") chatId: Long,
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        messageRepository: MessageRepository,
        messageActionRepository: MessageActionRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatActionRepository: ChatActionRepository,
        fileRepository: FileRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        audioPlayer: AudioPlayer,
        @Assisted("messageId") messageId: Long? = null,
    ) : this(
        chatId,
        savedStateHandle,
        chatRepository,
        messageRepository,
        messageActionRepository,
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
        messageId,
    )

    /** Creates the view model of chat `chatId`'s conversation, opened at message `messageId` if not null. */
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("chatId") chatId: Long,
            @Assisted("messageId") messageId: Long?,
        ): ChatViewModel
    }

    /** Each retry of finding the chat starts it again. */
    private val chatAttempts = MutableStateFlow(0)

    /** How far older pages have loaded; changed on the main thread only. */
    private val pageLoad = MutableStateFlow(PageLoad())

    /** The replied messages fetched for messages whose replied message is not loaded, by replying message id. */
    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())

    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The screen shows the oldest message, or close to it ([ChatEvent.NearOldestChanged]); main thread only. */
    private var isNearOldest = false

    /** The search for a tapped quote's message ([ChatEvent.OpenReply]); main thread only. */
    private var replySearch: Job? = null

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

    private val composer: ChatComposer =
        ChatComposer(
            chatId = chatId,
            repository = messageRepository,
            actions = messageActionRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            drafts =
                chatRepository
                    .chat(chatId)
                    .filterNotNull()
                    .map { it.draft }
                    .distinctUntilChanged(),
            messageOf = { id ->
                history.replayCache
                    .firstOrNull()
                    ?.messages
                    ?.findById(id) ?: composerTargets[id]
            },
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    /** The messages the composer's bars show that are not loaded. */
    private val composerTargets = ComposerTargets(chatId, messageRepository, viewModelScope, composer::onMessageGone)

    /** The latest chat, history, user and composer, for the message actions; null while the chat is not known. */
    private val actionContext: suspend () -> ActionContext? = {
        (chat.first() as? ChatLookup.Found)?.chat?.let { chat ->
            ActionContext(
                chat = chat,
                history = history.first(),
                myUserId = userRepository.myUserId.first(),
                canWrite = uiState.value.composer is ComposerUiState.Available,
            )
        }
    }

    private val messageActions =
        ChatMessageActions(
            chatId = chatId,
            repository = messageActionRepository,
            composer = composer,
            scope = viewModelScope,
            current = actionContext,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The text the composer edits, for its text field; the view model keeps it across rotations. */
    val composerText: ComposerText get() = composer.text

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

    private val history: SharedFlow<ChatHistory> =
        messageRepository
            .history(chatId)
            .onEach(::fetchMissingReplies)
            .flowOn(dispatcher)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The basic group or supergroup of a group or channel: its member count, and the user's membership. */
    private val group: Flow<ChatGroup> =
        chat
            .map { (it as? ChatLookup.Found)?.chat?.type }
            .distinctUntilChanged()
            .flatMapLatest { type ->
                when (type) {
                    is ChatType.BasicGroup -> {
                        groupRepository.basicGroup(type.basicGroupId).map { ChatGroup(basicGroup = it) }
                    }

                    is ChatType.Supergroup -> {
                        groupRepository.supergroup(type.supergroupId).map { ChatGroup(supergroup = it) }
                    }

                    else -> {
                        flowOf(ChatGroup())
                    }
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
            group,
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

    /**
     * What shows over the conversation: the composer's mode, with the message its bar shows fetched when it is not
     * loaded, the actions sheet and the delete confirmation.
     */
    private val overlays: Flow<Overlays> =
        combine(
            combine(composer.mode, history, composerTargets.messages) { mode, history, targets ->
                composerTargets.require(mode.messageId, history)
                mode to targets
            },
            messageActions.sheet,
            messageActions.deleteDialog,
        ) { (mode, targets), sheet, deleteDialog -> Overlays(mode, targets, sheet, deleteDialog) }

    val uiState: StateFlow<ChatUiState> =
        combine(chatSources, messageSources, people, clock.minutes, overlays, ::state)
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
        if (initialMessageId != null && savedStateHandle.get<Boolean>(KEY_OPENED_AT_MESSAGE) != true) {
            showMessage(initialMessageId, MAX_OPEN_PAGES, notFound = ChatEffect.MessageNotLoaded) {
                savedStateHandle[KEY_OPENED_AT_MESSAGE] = true
            }
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Started -> setOpen(true)
            ChatEvent.Stopped -> setOpen(false)
            is ChatEvent.NearOldestChanged -> onNearOldestChanged(event.isNearOldest)
            is ChatEvent.MessagesVisible -> views.setVisible(event.messageIds)
            is ChatEvent.OpenReply -> showMessage(event.messageId, MAX_REPLY_PAGES, ChatEffect.MessageUnavailable)
            ChatEvent.Retry -> retry()
            is ChatEvent.RevealSpoilers -> revealedSpoilers.update { it.adding(event.messageId) }
            is ChatEvent.OpenMedia -> uiState.value.mediaToOpen(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.DownloadFile -> files.download(event.fileId)
            is ChatEvent.CancelDownload -> files.cancelDownload(event.fileId)
            is ChatEvent.OpenFile -> uiState.value.fileToOpen(event)?.let(effectChannel::trySend)
            is ChatEvent.OpenLocation -> uiState.value.locationOf(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.PlayPause -> playback.playPause(event.messageId)
            is ChatEvent.Seek -> playback.seek(event.messageId, event.fraction)
            is ChatEvent.Send -> composer.send(event.options)
            is ChatEvent.ComposerFocusChanged -> composer.setFocused(event.isFocused)
            is ChatEvent.ShowMessageActions -> messageActions.show(event.messageId)
            ChatEvent.DismissMessageActions -> messageActions.dismiss()
            is ChatEvent.Reply -> messageActions.reply(event.messageId)
            ChatEvent.CancelReply -> composer.cancelReply()
            is ChatEvent.Copy -> messageActions.copy(event.messageId)
            is ChatEvent.Edit -> messageActions.edit(event.messageId)
            ChatEvent.CancelEdit -> composer.cancelEdit()
            is ChatEvent.Pin -> messageActions.pin(event.messageId, event.onlyForSelf)
            is ChatEvent.Unpin -> messageActions.unpin(event.messageId)
            is ChatEvent.Delete -> messageActions.delete(event.messageId)
            is ChatEvent.ConfirmDelete -> messageActions.confirmDelete(event.forEveryone)
            ChatEvent.DismissDelete -> messageActions.dismissDelete()
            is ChatEvent.Resend -> messageActions.resend(event.messageId)
            is ChatEvent.ToggleReaction -> messageActions.toggleReaction(event.messageId, event.label)
        }
    }

    /**
     * Opens or closes the chat in TDLib when that changes. Undispatched, so the request is sent before this returns:
     * a close as the screen leaves, right before the view model's scope is cancelled, still reaches TDLib. A failure
     * changes nothing: TDLib then just does not prioritize the chat. Stopping also pauses what plays: nothing plays in
     * the background; and it saves the composer's draft at once, also before the scope is cancelled.
     */
    private fun setOpen(open: Boolean) {
        if (open == isOpen) return
        isOpen = open
        if (!open) {
            composer.flush()
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

    /**
     * Shows message [messageId] once the items hold it ([ChatEffect.ScrollToMessage]), after loading up to [maxPages]
     * older pages for it ([findMessage]) with the older pages' indicator; [notFound] if it is not found, the error if a
     * page fails; then [onDone]. For a tapped quote and for the message the conversation was opened at. Replaces a
     * search still running. Started lazily, so [replySearch] names it before it runs: a replaced search, ending as it
     * is cancelled, then leaves the indicator to this one.
     */
    private fun showMessage(
        messageId: Long,
        maxPages: Int,
        notFound: ChatEffect,
        onDone: () -> Unit = {},
    ) {
        replySearch?.cancel()
        val search =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                pageLoad.update { it.copy(isFindingMessage = true) }
                val found =
                    try {
                        messageRepository.findMessage(chatId, messageId, maxPages)
                    } finally {
                        if (replySearch === coroutineContext.job) pageLoad.update { it.copy(isFindingMessage = false) }
                    }
                when {
                    found is Outcome.Failure -> {
                        effectChannel.trySend(ChatEffect.ShowError(found.error))
                    }

                    found == Outcome.Success(true) -> {
                        uiState.first { state -> state.items.any { it.showsMessage(messageId) } }
                        effectChannel.trySend(ChatEffect.ScrollToMessage(messageId))
                    }

                    else -> {
                        effectChannel.trySend(notFound)
                    }
                }
                onDone()
            }
        replySearch = search
        search.start()
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
            pageLoad.update { it.copy(isLoading = false, error = (result as? Outcome.Failure)?.error) }
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

    /** Runs on [dispatcher]: maps the header, the items, the composer and what shows over them. */
    private fun state(
        chatSources: ChatSources,
        messages: MessageSources,
        people: ChatPeople,
        now: Instant,
        overlays: Overlays,
    ): ChatUiState {
        val lookup = chatSources.chat
        val chat = (lookup as? ChatLookup.Found)?.chat
        val unreadAfterId = chatSources.unreadAfterId
        if (chat == null || unreadAfterId == null) {
            return ChatUiState(content = (lookup as? ChatLookup.Failed)?.error.toContent())
        }
        val formatter = timeFormatter()
        val group = chatSources.group
        val header =
            HeaderSources(
                chat = chat,
                memberCount = group.memberCount,
                actions = chatSources.actions,
                people = people,
                now = now,
                timeFormatter = formatter,
            ).toHeader()
        val history = messages.state.history
        val composer = composerState(chat, chat.userId?.let(people.users::get), group.basicGroup, group.supergroup)
        val canWrite = composer is ComposerUiState.Available
        return ChatUiState(
            header = header,
            content = content(chat, messages, people, unreadAfterId, now, formatter),
            unreadCount = chat.unreadCount,
            composer = if (canWrite) composer.copy(bar = overlays.bar(chat, history, people)) else composer,
            // A message deleted meanwhile takes its sheet with it.
            messageActions = overlays.sheet?.takeIf { history.messages.findById(it.messageId) != null },
            deleteDialog = overlays.deleteDialog,
            shortcuts = MessageShortcuts(canReply = canWrite, canCopy = !chat.hasProtectedContent),
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
                ChatContent.Content(items, history.reachedStart, load.isLoading || load.isFindingMessage, error)
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

    /** How far older pages have loaded, and whether older pages load to find a tapped quote's message. */
    private data class PageLoad(
        val isLoading: Boolean = false,
        val error: DomainError? = null,
        val isFindingMessage: Boolean = false,
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
        val group: ChatGroup,
        val actions: List<SenderAction>,
        /** Null until the chat was first shown. */
        val unreadAfterId: Long?,
    )

    /** The basic group or supergroup of a group or channel chat; neither for other chats, nor while unknown. */
    private data class ChatGroup(
        val basicGroup: BasicGroup? = null,
        val supergroup: Supergroup? = null,
    ) {
        /** Null while unknown, and for other chats. */
        val memberCount: Int? get() = basicGroup?.memberCount ?: supergroup?.memberCount
    }

    private class MessageSources(
        val state: MessageState,
        val pageLoad: PageLoad,
    )

    /** The composer's mode with the bar messages fetched for it, the actions sheet and the delete confirmation. */
    private class Overlays(
        val mode: ComposerMode,
        val targets: Map<Long, Message?>,
        val sheet: MessageActionsSheet?,
        val deleteDialog: DeleteMessagesDialog?,
    ) {
        fun bar(
            chat: Chat,
            history: ChatHistory,
            people: ChatPeople,
        ): ComposerBar? = composerBar(mode, chat, history, targets, people)
    }

    internal companion object {
        /** The last message read when the chat was first shown ([NO_UNREAD_DIVIDER] if nothing was unread). */
        const val KEY_UNREAD_AFTER = "chat.unreadAfterId"

        /** Whether the conversation was shown at the message it was opened at, so that happens once. */
        const val KEY_OPENED_AT_MESSAGE = "chat.openedAtMessage"

        /** How many older pages a tapped quote's message is looked for in, about 500 messages. */
        const val MAX_REPLY_PAGES = 10

        /**
         * How many older pages the message the conversation was opened at is looked for in, about 1500 messages: search
         * finds messages anywhere in the history, so further back than quotes usually lead.
         */
        const val MAX_OPEN_PAGES = 30
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

/**
 * The document of the loaded message that [event] names, its file downloaded, as the effect that opens it in another
 * app; null for other messages and for files not on the device. Documents of chats that protect their content open
 * too: Telegram lets them be viewed, only not saved (`has_protected_content`), and Pingogram offers no saving here.
 */
private fun ChatUiState.fileToOpen(event: ChatEvent.OpenFile): ChatEffect.OpenFile? {
    val bubble = items.firstOrNull { it is ChatItem.Bubble && it.id == event.messageId } as? ChatItem.Bubble
    val document = (bubble?.content as? MessageContent.Document)?.document?.takeIf { it.file.id == event.fileId }
    val path = (fileStates[event.fileId] as? FileState.Downloaded)?.path ?: return null
    return document?.let { ChatEffect.OpenFile(path, it.mimeType.ifBlank { null }) }
}

/**
 * The photo, video or GIF of loaded message [messageId], an album's item too, as the effect that opens it in the media
 * viewer; null for other messages. Video messages stay where they are, and so does self-destructing media, which needs
 * a viewer of its own (a timer once opened, screenshots blocked).
 */
private fun ChatUiState.mediaToOpen(messageId: Long): ChatEffect.OpenMedia? {
    val content =
        items.firstNotNullOfOrNull { item ->
            val bubble = item as? ChatItem.Bubble
            val album = bubble?.album
            when {
                album != null -> album.items.getOrNull(album.messageIds.indexOf(messageId))
                bubble != null && bubble.id == messageId -> bubble.content
                else -> null
            }
        }
    return if (content?.opensInViewer == true) ChatEffect.OpenMedia(messageId) else null
}

/** The items shown, none while there is no content. */
private val ChatUiState.items: List<ChatItem>
    get() = (content as? ChatContent.Content)?.items.orEmpty()

/**
 * Whether message [messageId] is in the history of chat [chatId], loading older pages until it is, the start is
 * reached, or [maxPages] pages were loaded; a page that fails ends the search with its error. A message newer than the
 * oldest loaded one that is not there was deleted, as ids grow with time.
 */
private suspend fun MessageRepository.findMessage(
    chatId: Long,
    messageId: Long,
    maxPages: Int,
): Outcome<Boolean> {
    var loaded = history(chatId).first()
    var pages = 0
    while (loaded.messages.findById(messageId) == null && loaded.olderPagesMayHold(messageId) && pages < maxPages) {
        val page = loadOlder(chatId)
        if (page is Outcome.Failure) return page
        pages++
        loaded = history(chatId).first()
    }
    return Outcome.Success(loaded.messages.findById(messageId) != null)
}

/** Message [messageId] is older than every loaded message, and older ones are left to load. */
private fun ChatHistory.olderPagesMayHold(messageId: Long): Boolean =
    !reachedStart && messages.lastOrNull().let { oldest -> oldest == null || messageId < oldest.id }

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
