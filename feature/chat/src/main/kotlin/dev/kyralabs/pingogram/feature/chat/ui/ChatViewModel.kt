package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.TextFieldState
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
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreview
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChatAttachments
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.attach.PreviewDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.attachOptions
import dev.kyralabs.pingogram.feature.chat.ui.attach.isChannel
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerTargets
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.composerState
import dev.kyralabs.pingogram.feature.chat.ui.content.opensInViewer
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatForwarding
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheet
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
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
 * - **Opening, paging and jumps** ([ChatWindow]): the conversation opens around the message search found
 *   ([initialMessageId]), else around the first unread message, else at the newest page, and shows nothing until those
 *   are loaded, so it never shows the newest messages first and then jumps; the list goes where the content's
 *   [ListAnchor] says. Older and newer pages load near the ends ([ChatEvent.NearOldestChanged],
 *   [ChatEvent.NearNewestChanged]); a tapped quote ([ChatEvent.OpenReply]) and the jump to the newest message
 *   ([ChatEvent.ShowNewest]) load the messages around their message when it is not loaded. The newest message the list
 *   shows is kept in the [SavedStateHandle], which the conversation opens around after process death.
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
 *   A group of messages shows its time on its last bubble; a bubble tapped shows its own ([ChatEvent.ToggleTime]).
 * - **Files** ([ChatFiles]): the files of the loaded voice messages, songs and documents are followed through
 *   [FileRepository.observe] into [ChatUiState.fileStates], on [dispatcher], for as long as they are loaded. Documents
 *   download and cancel on request. Failures go out as [ChatEffect.ShowError] snackbars.
 * - **Automatic download** ([ChatAutoDownloads], `docs/PERFORMANCE.md` §7): under the rules of the current network
 *   ([AutoDownloadPolicy]), the files of the messages on screen download by themselves, and photos download in their
 *   bubbles or wait for a tap on their download button ([ChatUiState.mediaDownloads]).
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
 * - **Forwarding** ([ChatForwarding]): the forward sheet ([forwardSheet], kept out of [uiState] so the chats it lists
 *   do not recompose the conversation), with its comment ([forwardComment]), and forwarding to the picked chats.
 * - **Selection mode** ([ChatSelection], [ChatUiState.selection]): the selected messages, kept across process death,
 *   and the selection toolbar's actions (copying them with their senders and times, forwarding, reporting and deleting
 *   them all); it ends once they are copied, forwarded, reported or deleted.
 * - **Reporting** ([ChatReporting], [ChatUiState.report]) a message or the selected ones, with the reasons Telegram
 *   asks for and a comment ([reportComment]); **saving** a message's files to Downloads ([ChatSaving]).
 * - **Attach menu** ([ChatAttachments]): what the composer's attach button offers in this chat
 *   ([ComposerUiState.Available.attachOptions]); picked photos, videos and files in a preview
 *   ([ChatUiState.attachPreview]) with its caption ([attachCaption]), a photo from the camera, a contact and a poll
 *   ([ChatUiState.poll], [pollEditor]), each taking the composer's reply along. The uploads of the messages being sent
 *   ([ChatUiState.uploads]) come from [FileRepository.observeUpload], and can be cancelled.
 * - **Sponsored messages** ([ChatSponsoredMessages]) of a channel or a chat with a bot (`REQUIREMENTS.md` §2): loaded
 *   once ([SponsoredMessageRepository]), placed among the items where they reach the chat's newest message, viewed once
 *   wholly on screen, opened, and "About this ad" ([ChatUiState.sponsoredSheet]) with reporting.
 * - **Starting**: the view model is created in the frame that first composes the screen; what it starts (loading the
 *   messages it opens at, following the composer's text and drafts, reporting viewed messages) runs once that frame is
 *   done, so the frame does not wait for it.
 * - **Effects** ([effects]): errors, opening a photo, video or GIF in the media viewer ([ChatEvent.OpenMedia]),
 *   opening a downloaded document in another app ([ChatEvent.OpenFile]), a location in a maps app, a scheduled
 *   message's time, a copied text, the composer's focus when a reply or an edit starts, and a haptic when a message
 *   being sent fails while the screen is shown ([SendFailures]).
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
    sponsoredMessageRepository: SponsoredMessageRepository,
    attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    groupRepository: GroupRepository,
    chatActionRepository: ChatActionRepository,
    fileRepository: FileRepository,
    searchRepository: SearchRepository,
    sharedStorageRepository: SharedStorageRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    audioPlayer: AudioPlayer,
    autoDownloadPolicy: AutoDownloadPolicy,
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
        sponsoredMessageRepository: SponsoredMessageRepository,
        attachmentRepository: AttachmentRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatActionRepository: ChatActionRepository,
        fileRepository: FileRepository,
        searchRepository: SearchRepository,
        sharedStorageRepository: SharedStorageRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        audioPlayer: AudioPlayer,
        autoDownloadPolicy: AutoDownloadPolicy,
        @Assisted("messageId") messageId: Long? = null,
    ) : this(
        chatId,
        savedStateHandle,
        chatRepository,
        messageRepository,
        messageActionRepository,
        sponsoredMessageRepository,
        attachmentRepository,
        userRepository,
        groupRepository,
        chatActionRepository,
        fileRepository,
        searchRepository,
        sharedStorageRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
        audioPlayer,
        autoDownloadPolicy,
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

    /** The replied messages fetched for messages whose replied message is not loaded, by replying message id. */
    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())

    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The messages whose time was tapped to show ([ChatEvent.ToggleTime]). */
    private val shownTimes = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /**
     * Whether the chat is open in TDLib ([ChatEvent.Started]): set on the main thread, read where the history is
     * watched for failed sends too ([SendFailures]).
     */
    @Volatile
    private var isOpen = false

    private val sendFailures = SendFailures()

    private val mapper = ChatItemMapper()
    private var timeFormatter: ChatTimeFormatter? = null

    private val effectChannel = Channel<ChatEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: snackbars and opening a location. */
    val effects: Flow<ChatEffect> = effectChannel.receiveAsFlow()

    private val files = ChatFiles(fileRepository, viewModelScope) { effectChannel.trySend(ChatEffect.ShowError(it)) }

    private val autoDownloads = ChatAutoDownloads(fileRepository, autoDownloadPolicy.rules, viewModelScope)

    private val views = MessageViews(chatId, chatRepository, viewModelScope, log)

    private val sponsored =
        ChatSponsoredMessages(
            chatId = chatId,
            repository = sponsoredMessageRepository,
            views = views,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

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

    private val attachments =
        ChatAttachments(
            chatId = chatId,
            repository = attachmentRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            replies = composer,
            onEffect = { effectChannel.trySend(it) },
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
                users = userRepository.users.first(),
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
            onForward = { forwarding.open(it) },
            onSelect = { selection.select(it) },
            onReport = { reporting.start(it) },
            onSave = { saving.save(it) },
            onSelectionDeleted = { selection.clear() },
        )

    private val selection =
        ChatSelection(
            chatId = chatId,
            repository = messageActionRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            current = actionContext,
            onEffect = { effectChannel.trySend(it) },
        )

    private val reporting =
        ChatReporting(
            chatId = chatId,
            repository = messageActionRepository,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            onReported = { selection.clear() },
        )

    private val saving =
        ChatSaving(
            files = fileRepository,
            storage = sharedStorageRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    private val forwarding =
        ChatForwarding(
            fromChatId = chatId,
            chats = chatRepository,
            search = searchRepository,
            users = userRepository,
            groups = groupRepository,
            actions = messageActionRepository,
            scope = viewModelScope,
            dispatcher = dispatcher,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The events of selection mode, reporting and saving. */
    private val selectionEvents =
        ChatSelectionEvents(selection, reporting, saving, messageActions, forwarding) {
            messageRepository.history(chatId).first()
        }

    /** The forward sheet; null while none is open. */
    val forwardSheet: StateFlow<ForwardSheet?> get() = forwarding.sheet

    /** The comment of the forward sheet, for its text field; kept like [composerText]. */
    val forwardComment: ComposerText get() = forwarding.comment

    /** The comment of a report that asks for one, for its text field; kept like [composerText]. */
    val reportComment: TextFieldState get() = reporting.comment

    /** The text the composer edits, for its text field; the view model keeps it across rotations. */
    val composerText: ComposerText get() = composer.text

    /** The caption of the attach preview, for its text field; kept like [composerText]. */
    val attachCaption: ComposerText get() = attachments.caption

    /** What the form of a new poll holds, for its fields; kept like [composerText]. */
    val pollEditor: PollEditor get() = attachments.poll

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
            // A `Reject` haptic when a message being sent fails while the screen is shown (SendFailures).
            .onEach { if (sendFailures.failedSince(it) && isOpen) effectChannel.trySend(ChatEffect.SendFailed) }
            .flowOn(dispatcher)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** Which messages the conversation shows, where its list shows them, and how far pages have loaded. */
    private val window =
        ChatWindow(
            chatId = chatId,
            repository = messageRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            history = history,
            content = { uiState.value.content },
            initialMessageId = initialMessageId,
            onEffect = { effectChannel.trySend(it) },
        )

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

    /** What was tapped in the bubbles: the messages whose spoilers show, and those whose time shows. */
    private val tapped: Flow<Pair<Set<Long>, Set<Long>>> = combine(revealedSpoilers, shownTimes, ::Pair)

    private val messageSources: Flow<MessageSources> =
        combine(history, replies, tapped, window.state) { history, replies, (spoilers, times), load ->
            MessageSources(MessageState(load.historyFor(history), replies, spoilers, times), load)
        }

    private val people: Flow<ChatPeople> =
        combine(userRepository.users, userRepository.myUserId, referencedChats, ::ChatPeople)

    /**
     * What shows over the conversation: the composer's mode, with the message its bar shows fetched when it is not
     * loaded, the actions sheet, the delete confirmation, the attach preview and the poll form.
     */
    private val overlays: Flow<Overlays> =
        combine(
            combine(composer.mode, history, composerTargets.messages) { mode, history, targets ->
                composerTargets.require(mode.messageId, history)
                mode to targets
            },
            messageActions.sheet,
            messageActions.deleteDialog,
            attachments.preview,
            attachments.isPollOpen,
        ) { (mode, targets), sheet, deleteDialog, preview, pollOpen ->
            Overlays(mode, targets, sheet, deleteDialog, preview, pollOpen)
        }

    val uiState: StateFlow<ChatUiState> =
        combine(chatSources, messageSources, people, clock.minutes, overlays, ::state)
            .combine(files.statesOf(history)) { state, fileStates -> state.copy(fileStates = fileStates) }
            .combine(files.uploadsOf(history)) { state, uploads -> state.copy(uploads = uploads) }
            .combine(playback.state) { state, playback -> state.copy(playback = playback) }
            .combine(sponsored.state(history)) { state, ads -> state.withSponsored(ads) }
            .combine(selection.state(history)) { state, selection -> state.copy(selection = selection) }
            .combine(reporting.sheet) { state, report -> state.copy(report = report) }
            .combine(autoDownloads.state) { state, downloads -> state.copy(mediaDownloads = downloads) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatUiState())

    init {
        // Leaving the screen frees the player, and deletes picked files that were never sent.
        addCloseable { playback.release() }
        addCloseable { attachments.release() }
        // The screen creates the view model in the frame that first composes it. What it starts runs once that frame is
        // done (dispatched, not at once as the main thread's immediate dispatcher would), so the frame does not wait.
        viewModelScope.launch(Dispatchers.Main) {
            composer.start()
            attachments.start()
            views.start()
            autoDownloads.start()
            window.open()
            sponsored.start(chat.mapNotNull { (it as? ChatLookup.Found)?.chat }, userRepository.users)
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Started -> setOpen(true)
            ChatEvent.Stopped -> setOpen(false)
            is ChatEvent.NearOldestChanged -> window.onNearOldestChanged(event.isNearOldest)
            is ChatEvent.NearNewestChanged -> window.onNearNewestChanged(event.isNearNewest)
            ChatEvent.ShowNewest -> window.showNewest()
            is ChatEvent.MessagesVisible -> onMessagesVisible(event.messageIds)
            is ChatEvent.OpenReply -> window.showMessage(event.messageId)
            ChatEvent.Retry -> retry()
            is ChatEvent.RevealSpoilers -> revealedSpoilers.update { it.adding(event.messageId) }
            is ChatEvent.ToggleTime -> shownTimes.update { it.toggling(event.messageId) }
            is ChatEvent.OpenMedia -> uiState.value.mediaToOpen(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.DownloadFile -> download(event.fileId)
            is ChatEvent.CancelDownload -> cancelDownload(event.fileId)
            is ChatEvent.OpenFile -> uiState.value.fileToOpen(event)?.let(effectChannel::trySend)
            is ChatEvent.OpenLocation -> uiState.value.locationOf(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.PlayPause -> playback.playPause(event.messageId)
            is ChatEvent.Seek -> playback.seek(event.messageId, event.fraction)
            is ChatEvent.Send -> composer.send(event.options)
            is ChatEvent.ComposerFocusChanged -> composer.setFocused(event.isFocused)
            ChatEvent.CancelReply -> composer.cancelReply()
            ChatEvent.CancelEdit -> composer.cancelEdit()
            is SponsoredEvent -> sponsored.onEvent(event)
            is SelectionEvent -> selectionEvents.onEvent(event)
            else -> onMessageEvent(event)
        }
    }

    /** The events of the message actions, the reactions and the forward sheet, which [onEvent] hands on. */
    private fun onMessageEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.ShowMessageActions -> messageActions.show(event.messageId)
            is ChatEvent.ShowAllReactions -> messageActions.show(event.messageId, showsPicker = true)
            ChatEvent.ExpandReactions -> messageActions.showPicker(true)
            ChatEvent.CollapseReactions -> messageActions.showPicker(false)
            ChatEvent.DismissMessageActions -> messageActions.dismiss()
            is ChatEvent.Reply -> messageActions.reply(event.messageId)
            is ChatEvent.Copy -> messageActions.copy(event.messageId)
            is ChatEvent.Edit -> messageActions.edit(event.messageId)
            is ChatEvent.Pin -> messageActions.pin(event.messageId, event.onlyForSelf)
            is ChatEvent.Unpin -> messageActions.unpin(event.messageId)
            is ChatEvent.Delete -> messageActions.delete(event.messageId)
            is ChatEvent.ConfirmDelete -> messageActions.confirmDelete(event.forEveryone)
            ChatEvent.DismissDelete -> messageActions.dismissDelete()
            is ChatEvent.Resend -> messageActions.resend(event.messageId)
            is ChatEvent.ToggleReaction -> messageActions.toggleReaction(event.messageId, event.label)
            is ChatEvent.Forward -> messageActions.forward(event.messageId)
            is ForwardEvent -> forwarding.onEvent(event)
            else -> onAttachEvent(event)
        }
    }

    /** The events of the attach menu, its previews and the poll form, which [onMessageEvent] hands on. */
    private fun onAttachEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.AttachMedia -> attachments.pickMedia(event.uris)

            is ChatEvent.AttachFiles -> attachments.pickFiles(event.uris)

            ChatEvent.TakePhoto -> attachments.takePhoto()

            is ChatEvent.PhotoTaken -> attachments.photoTaken(event.taken)

            is ChatEvent.AttachContact -> attachments.sendContact(event.uri)

            is ChatEvent.RemoveAttachment -> attachments.remove(event.key)

            ChatEvent.DismissAttachments -> attachments.dismissPreview()

            is ChatEvent.SendAttachments -> attachments.sendPreview(event.options)

            ChatEvent.OpenPoll -> attachments.openPoll()

            ChatEvent.DismissPoll -> attachments.dismissPoll()

            // A poll goes out anonymous in a channel.
            is ChatEvent.SendPoll -> attachments.sendPoll(event.options, uiState.value.poll?.isChannel == true)

            is ChatEvent.CancelUpload -> attachments.cancelUpload(event.messageIds)

            // The other events are handled by onEvent and onMessageEvent.
            else -> Unit
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
        if (open) {
            views.setOpen(true)
            sponsored.onStarted()
        }
    }

    /**
     * The messages the list shows go to TDLib as viewed; the newest of them is kept in the [SavedStateHandle], which
     * the conversation opens around after process death ([ChatWindow]).
     */
    private fun onMessagesVisible(messageIds: Set<Long>) {
        views.setVisible(messageIds)
        messageIds.maxOrNull()?.let { savedStateHandle[ChatWindow.KEY_SHOWN_MESSAGE] = it }
        val messages =
            history.replayCache
                .firstOrNull()
                ?.messages
                .orEmpty()
        autoDownloads.setVisible(messages.filter { it.id in messageIds }.map { it.content })
    }

    /** Downloads [fileId] on request: a document's, or a photo's that the rules leave for a tap. */
    private fun download(fileId: Int) {
        autoDownloads.requested(fileId)
        files.download(fileId)
    }

    private fun cancelDownload(fileId: Int) {
        autoDownloads.cancelled(fileId)
        files.cancelDownload(fileId)
    }

    /** Finds the chat again if that failed, and opens again or loads the pages that failed ([ChatWindow.retry]). */
    private fun retry() {
        if (!window.retry() && uiState.value.content is ChatContent.Error) chatAttempts.update { it + 1 }
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
        val user = chat.userId?.let(people.users::get)
        val composer = composerState(chat, user, group.basicGroup, group.supergroup)
        val canWrite = composer is ComposerUiState.Available
        val options = attachOptions(chat, user, people.myUserId)
        val canSchedule = (composer as? ComposerUiState.Available)?.canSchedule == true
        return ChatUiState(
            header = header,
            content = content(chat, messages, people, unreadAfterId, now, formatter),
            unreadCount = chat.unreadCount,
            composer = if (canWrite) composer.withBar(overlays.bar(chat, history, people), options) else composer,
            // A message deleted meanwhile takes its sheet with it.
            messageActions = overlays.sheet?.takeIf { history.messages.findById(it.messageId) != null },
            deleteDialog = overlays.deleteDialog,
            shortcuts = MessageShortcuts(canReply = canWrite, canCopy = !chat.hasProtectedContent),
            // What was picked or written stays while the user may not write; it shows again when they may.
            attachPreview =
                overlays.preview?.takeIf { canWrite }?.let { AttachPreview(it.kind, it.items, canSchedule) },
            poll =
                PollSheet(chat.isChannel, canSchedule).takeIf {
                    canWrite && overlays.pollOpen && AttachOption.Poll in options
                },
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
        val shown = load.shown
        return when {
            shown == null -> {
                load.openError?.let(ChatContent::Error) ?: ChatContent.Loading
            }

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
                ChatContent.Content(
                    items = items,
                    reachedStart = history.reachedStart,
                    isLoadingOlder = load.isLoading || load.isFindingMessage,
                    olderError = error,
                    reachedEnd = history.reachedEnd,
                    isLoadingNewer = load.isLoadingNewer || load.isFindingNewest,
                    newerError = load.newerError,
                    anchor = shown.anchor,
                )
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
        val pageLoad: WindowState,
    )

    /**
     * The composer's mode with the bar messages fetched for it, the actions sheet, the delete confirmation, the attach
     * preview and whether the poll form is open.
     */
    private class Overlays(
        val mode: ComposerMode,
        val targets: Map<Long, Message?>,
        val sheet: MessageActionsSheet?,
        val deleteDialog: DeleteMessagesDialog?,
        val preview: PreviewDraft?,
        val pollOpen: Boolean,
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

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The composer with [bar], and the [attachOptions] of the chat unless it edits: an edit sends no attachments. */
private fun ComposerUiState.Available.withBar(
    bar: ComposerBar?,
    attachOptions: ImmutableList<AttachOption>,
): ComposerUiState.Available =
    copy(bar = bar, attachOptions = if (bar is ComposerBar.Edit) persistentListOf() else attachOptions)

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

/** This set without [id] if it holds it, else with it. */
private fun PersistentSet<Long>.toggling(id: Long): PersistentSet<Long> = if (id in this) removing(id) else adding(id)
