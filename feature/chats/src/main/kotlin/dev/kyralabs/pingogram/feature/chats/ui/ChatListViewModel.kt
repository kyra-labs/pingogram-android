package dev.kyralabs.pingogram.feature.chats.ui

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
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.keyword.MutedWords
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ShownChatListRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpChats
import dev.kyralabs.pingogram.feature.chats.ui.keyword.KeywordAlertChats
import dev.kyralabs.pingogram.feature.chats.ui.unanswered.UnansweredChats
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * A chat list (`REQUIREMENTS.md` FR-L1–L5, FR-L8, FR-L9): the Chats tab or the archive ([kind]), as [uiState]. Events
 * come in through [onEvent]; snackbars and navigation go out through [effects].
 *
 * - **State**: one `combine` of the shown list's chats, the users, supergroups, secret chats and actions that rows
 *   show, the current user, the minute ([WallClock.minutes], for times and online dots that expire), the connection,
 *   the folders with their unread counts, the archive row, the selection and the delete dialog. Rows are mapped on
 *   [dispatcher] by a [ChatListItemMapper], which reuses rows whose chat did not change. It runs while the screen
 *   collects, and 5 s after, so a rotation does not restart it.
 * - **Folders** (Chats tab only): the selected folder is kept in the [SavedStateHandle]. A folder that does not exist
 *   (deleted, or not reported by TDLib yet after a restart) shows the main list, without forgetting the selection.
 *   The main list has the archive row while the archive has chats; the archive itself has neither.
 * - **Paging**: a list starts with a short first page ([FIRST_PAGE_SIZE]) once shown, then loads pages of
 *   [ChatRepository.PAGE_SIZE] for as long as the screen reports that its end is near
 *   ([ChatListEvent.NearEndChanged]), one request per list at a time, until TDLib has no more. A page that leaves the
 *   end near, even one that added no rows, loads the next. A failure shows [ChatListContent.Error] while there are no
 *   chats; otherwise the chats stay. Either way nothing is retried by itself: [ChatListEvent.Retry], or the end coming
 *   near again, tries again.
 * - **Selection** (FR-L8): the selected ids are kept in the [SavedStateHandle] ([SelectedChats]), for the list shown;
 *   selecting another folder clears them. A toolbar action applies to the selected chats it fits ([targetsOf]) and
 *   ends selection mode; delete ends it once confirmed. Outside selection mode a tap opens the chat, and a tap on a
 *   topic a forum's row names that topic ([ChatListEffect.OpenChat]).
 * - **Actions** (FR-L4) run one chat after another and stop at the first failure ([perform]), which a snackbar shows
 *   ([ChatListEffect.ShowError]). Their effect on the rows comes through TDLib's updates. Archiving offers to undo it
 *   ([ChatListEffect.ChatsArchived]). Deleting asks first ([ChatListUiState.deleteDialog]), with what TDLib allows for
 *   one chat; several chats are deleted for the current user only.
 * - **Times** follow the zone, the language and the 12- or 24-hour setting ([TimePatterns]), checked at every
 *   emission, so a change shows at the next minute at the latest.
 * - **Communities** (main list only): the chats of a community that collapses them show as one entry
 *   ([ChatListCommunities]).
 * - **Archive row** (Chats tab only): full, compact or hidden as [ArchiveRowRepository] keeps it on the device; a
 *   hidden row leaves the archive to the top bar ([ChatListUiState.hiddenArchive]). Its style and the tabs' "Mark all
 *   as read" go through [ChatListActions].
 *
 * It takes one repository per kind of data the list combines, plus the clock, dispatcher, language and time patterns
 * that tests replace, hence `LongParameterList` is suppressed. The Chats tab and the archive create it with their
 * [kind] through [Factory].
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatListViewModel.Factory::class)
class ChatListViewModel internal constructor(
    private val kind: ChatListKind,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    secretChatRepository: SecretChatRepository,
    chatActionRepository: ChatActionRepository,
    connectionRepository: ConnectionRepository,
    chatLock: ChatLockRepository,
    archiveRowRepository: ArchiveRowRepository,
    archiveSettingsRepository: ArchiveSettingsRepository,
    communityRepository: CommunityRepository,
    forumTopicRepository: ForumTopicRepository,
    catchUpChats: CatchUpChats,
    unansweredChats: UnansweredChats,
    private val keywordChats: KeywordAlertChats,
    private val shownLists: ShownChatListRepository,
    localChatNames: LocalChatNamesRepository,
    mutedWords: MutedWordsRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted kind: ChatListKind,
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        secretChatRepository: SecretChatRepository,
        chatActionRepository: ChatActionRepository,
        connectionRepository: ConnectionRepository,
        chatLock: ChatLockRepository,
        archiveRowRepository: ArchiveRowRepository,
        archiveSettingsRepository: ArchiveSettingsRepository,
        communityRepository: CommunityRepository,
        forumTopicRepository: ForumTopicRepository,
        catchUpChats: CatchUpChats,
        unansweredChats: UnansweredChats,
        keywordChats: KeywordAlertChats,
        shownLists: ShownChatListRepository,
        localChatNames: LocalChatNamesRepository,
        mutedWords: MutedWordsRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        kind,
        savedStateHandle,
        chatRepository,
        userRepository,
        groupRepository,
        secretChatRepository,
        chatActionRepository,
        connectionRepository,
        chatLock,
        archiveRowRepository,
        archiveSettingsRepository,
        communityRepository,
        forumTopicRepository,
        catchUpChats,
        unansweredChats,
        keywordChats,
        shownLists,
        localChatNames,
        mutedWords,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Creates the view model of the Chats tab or of the archive. */
    @AssistedFactory
    interface Factory {
        fun create(kind: ChatListKind): ChatListViewModel
    }

    /** How far each list has loaded, by list; changed on the main thread only. */
    private val loads = MutableStateFlow<PersistentMap<ChatListType, ListLoad>>(persistentMapOf())

    /** The list shown; updated on the main thread, for events that act on it. */
    private var currentList: ChatListType =
        if (kind ==
            ChatListKind.Archive
        ) {
            ChatListType.Archive
        } else {
            ChatListType.Main
        }

    /** The screen shows the end of [currentList], or close to it ([ChatListEvent.NearEndChanged]); main thread only. */
    private var isNearEnd = false

    private val mapper = ChatListItemMapper()
    private var timeFormatter: ChatListTimeFormatter? = null

    private val selection = SelectedChats(savedStateHandle)
    private val deleteDialog = MutableStateFlow<DeleteDialog?>(null)

    /** Reads what deleting a chat allows, before its dialog shows; one at a time. */
    private var deleteRequest: Job? = null

    private val effectChannel = Channel<ChatListEffect>(Channel.BUFFERED)
    val effects: Flow<ChatListEffect> = effectChannel.receiveAsFlow()

    private val listActions =
        ChatListActions(
            scope = viewModelScope,
            archiveRowRepository = archiveRowRepository,
            archiveSettingsRepository = archiveSettingsRepository,
            shownArchive = { uiState.value.archive },
            effects = effectChannel,
        )

    private val communities =
        ChatListCommunities(
            repository = communityRepository.takeIf { kind == ChatListKind.Chats },
            savedStateHandle = savedStateHandle,
            scope = viewModelScope,
            effects = effectChannel,
        )

    private val forumTopics =
        ForumRowTopics(forumTopicRepository.takeIf { kind == ChatListKind.Chats }, groupRepository)

    /** The keyword alert matches ([KeywordAlertChats.matches]), which sort every match: read once, for all below. */
    private val keywordHits: Flow<List<KeywordHit>> =
        if (kind == ChatListKind.Chats) {
            keywordChats.matches.sharedIn(viewModelScope, dispatcher)
        } else {
            flowOf(emptyList())
        }

    /** How many chats the "Keyword alerts" tab lists; 0 hides it (always in the archive). */
    private val keywordMatchCount: Flow<Int> =
        if (kind == ChatListKind.Chats) {
            // No tab until the matches are read: the chat list's first content never waits for them (they read the
            // settings file and a file of the account's), and the tab comes last, so it moves no other tab.
            keywordHits
                .map { it.size }
                .onStart { emit(0) }
                .distinctUntilChanged()
        } else {
            flowOf(0)
        }

    /**
     * The message each chat of the "Keyword alerts" tab opens at, its newest match, which a tap on its row opens at;
     * read on the main thread.
     */
    private val keywordMessages: StateFlow<Map<Long, Long>> =
        if (kind == ChatListKind.Chats) {
            keywordHits
                .map { hits -> hits.associate { it.chatId to it.messageId } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
        } else {
            MutableStateFlow(emptyMap())
        }

    /** [keywordMessages] while the "Keyword alerts" tab shows; none otherwise, so other rows open their chat. */
    private val shownKeywordMessages: Map<Long, Long>
        get() = if (currentList == KEYWORD_ALERTS_LIST) keywordMessages.value else emptyMap()

    private val selectedList: Flow<ChatListType> =
        when (kind) {
            ChatListKind.Archive -> {
                flowOf(ChatListType.Archive)
            }

            ChatListKind.Chats -> {
                combine(
                    savedStateHandle.getStateFlow<Int?>(KEY_FOLDER, null),
                    chatRepository.folders,
                    keywordMatchCount.map { it > 0 }.distinctUntilChanged(),
                ) { id, folders, hasKeywordMatches ->
                    when {
                        id == KEYWORD_ALERTS_FOLDER_ID && hasKeywordMatches -> KEYWORD_ALERTS_LIST
                        id != null && folders.any { it.id == id } -> ChatListType.Folder(id)
                        else -> ChatListType.Main
                    }
                }.distinctUntilChanged()
                    .sharedIn(viewModelScope, dispatcher)
            }
        }

    /** The minute, read once for the rows and the header. */
    private val minutes: Flow<Instant> = clock.minutes.sharedIn(viewModelScope, dispatcher)

    /** What rows show besides their chats, in one snapshot. */
    private val rowSources: Flow<RowSnapshot> =
        combine(
            combine(userRepository.users, groupRepository.supergroups, secretChatRepository.secretChats, ::Triple),
            chatActionRepository.actionsByChat,
            userRepository.myUserId,
            minutes,
            combine(
                localChatNames.names,
                // One function per settings: rows compare it by identity.
                mutedWords.settings.distinctUntilChanged().map { MutedWords(it)::mutes },
                ::Pair,
            ),
        ) { (users, supergroups, secretChats), actions, myUserId, now, (localNames, muted) ->
            RowSnapshot(users, supergroups, secretChats, actions, myUserId, now, localNames, muted)
        }.sharedIn(viewModelScope, dispatcher)

    /** Locked chats (docs/adr/0029-locked-chats.md) never show here, open or not: they have a list of their own. */
    private val lockedChatIds = chatLock.lockedChatIds.distinctUntilChanged()

    private val listState: Flow<ListState> =
        selectedList.flatMapLatest { list ->
            val chats = chatsOf(list, chatRepository, keywordChats).without(lockedChatIds)
            combine(
                chats,
                rowSources,
                loads.map { it[list] ?: ListLoad() }.distinctUntilChanged(),
                communities.of(list),
                forumTopics.of(chats),
                ::ListInputs,
            )
                // While the rows of one change map, the changes after it make one: only the latest is mapped.
                .conflate()
                .map { inputs ->
                    val content =
                        content(list, inputs.chats, inputs.snapshot, inputs.load, inputs.groups, inputs.topics)
                    ListState(list, content)
                }.combine(keywordWordsOf(list, keywordHits)) { state, words -> state.withKeywords(words) }
        }

    private val folderTabs: Flow<ImmutableList<FolderTab>> =
        if (kind == ChatListKind.Archive) {
            flowOf(persistentListOf())
        } else {
            combine(chatRepository.folders, keywordMatchCount.map { it > 0 }, ::Pair)
                .distinctUntilChanged()
                .flatMapLatest { (folders, hasKeywordMatches) ->
                    if (folders.isEmpty() && !hasKeywordMatches) {
                        flowOf(persistentListOf())
                    } else {
                        val lists = listOf(ChatListType.Main) + folders.map { ChatListType.Folder(it.id) }
                        val unread = combine(lists.map(chatRepository::unreadChatCount)) { it.toList() }
                        combine(unread, chatRepository.mainChatListPosition, keywordMatchCount) {
                            counts,
                            mainPosition,
                            keywordCount,
                            ->
                            folderTabs(folders, counts, mainPosition, keywordCount)
                        }
                    }
                }.sharedIn(viewModelScope, dispatcher)
        }

    /** The Feed tab, while the main list holds 2 or more channels; never in the archive. */
    private val feedTab: Flow<FeedTab?> =
        if (kind == ChatListKind.Archive) {
            flowOf(null)
        } else {
            chatRepository
                .chatList(ChatListType.Main)
                .map { chats -> feedTabOf(chats) }
                .distinctUntilChanged()
        }

    private val archive: Flow<ArchivePlace> =
        if (kind == ChatListKind.Archive) {
            flowOf(ArchivePlace(entry = null, style = ArchiveRowStyle.Full))
        } else {
            combine(
                chatRepository.unreadChatCount(ChatListType.Archive),
                chatRepository.chatList(ChatListType.Archive).without(lockedChatIds),
                archiveRowRepository.style,
            ) { count, chats, style -> ArchivePlace(archiveEntry(count, chats), style) }
                .distinctUntilChanged()
                .sharedIn(viewModelScope, dispatcher)
        }

    private val overlays: Flow<Overlays> =
        combine(selection.ids, deleteDialog, lockedChatIds) { ids, dialog, locked ->
            Overlays(ids, dialog, hasLockedChats = kind == ChatListKind.Chats && locked.isNotEmpty())
        }.sharedIn(viewModelScope, dispatcher)

    /** The lists of the folder tabs beside the selected one, if any; none in the archive. */
    private val neighbourTypes: Flow<NeighbourTypes> =
        if (kind == ChatListKind.Archive) {
            flowOf(NeighbourTypes())
        } else {
            combine(selectedList, folderTabs) { list, tabs -> NeighbourTypes.of(tabs, list) }
                .distinctUntilChanged()
                .sharedIn(viewModelScope, dispatcher)
        }

    val uiState: StateFlow<ChatListUiState> =
        combine(
            listState,
            connectionRepository.connectionStatus,
            // The tabs' counts at the counts' pace, the tabs themselves at once; the rows follow every change.
            combine(folderTabs, feedTab, ::Pair).distinctUntilChanged().atCountPace(shapeOf = ::tabsShape),
            archive,
            overlays,
        ) {
            list,
            connection,
            (tabs, feed),
            archive,
            overlays,
            ->
            ChatListUiState(
                kind = kind,
                content = list.content,
                connection = connection,
                folderTabs = tabs,
                feedTab = feed,
                selectedFolderId = (list.list as? ChatListType.Folder)?.id,
                archive = archive.row.takeIf { list.list == ChatListType.Main },
                hasLockedChats = overlays.hasLockedChats && list.list == ChatListType.Main,
                hiddenArchive = archive.hidden.takeIf { list.list == ChatListType.Main },
                selection = selectionOf(list.content, overlays.selectedIds),
                deleteDialog = overlays.deleteDialog,
            )
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatListUiState(kind = kind))

    /**
     * The lists of the folder tabs beside the selected one ([FolderNeighbourLists]), which a swipe of the list pulls in
     * under the finger (owner decision 2026-11-07): mapped as the list shown is, each by a mapper of its own, while the
     * screen collects them (it does only while "Swipe to change folders" is on), and dropped, with their rows, once
     * their tab is no longer beside the selected one. Their first page is loaded ahead either way ([neighbourTypes]).
     */
    val neighbourLists: StateFlow<FolderNeighbourLists> =
        neighbourTypes
            .flatMapLatest { types ->
                val neighbour: (ChatListType?) -> Flow<FolderNeighbourList?> = { type ->
                    if (type == null) {
                        flowOf(null)
                    } else {
                        val mapper = ChatListItemMapper()
                        combine(
                            chatsOf(type, chatRepository, keywordChats).without(lockedChatIds),
                            rowSources,
                            loads.map { it[type] ?: ListLoad() }.distinctUntilChanged(),
                            communities.of(type),
                        ) { chats, snapshot, load, groups ->
                            val content = content(type, chats, snapshot, load, groups, mapper = mapper)
                            FolderNeighbourList((type as? ChatListType.Folder)?.id, ChatListUiState(kind, content))
                        }
                    }
                }
                combine(
                    neighbour(types.previous),
                    neighbour(types.next),
                    archive,
                    overlays,
                ) { previous, next, archive, over ->
                    FolderNeighbourLists(
                        previous = previous?.onMainList(archive.row, over.hasLockedChats),
                        next = next?.onMainList(archive.row, over.hasLockedChats),
                    )
                }
            }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FolderNeighbourLists())

    /**
     * What the Chats tab's header says ([ChatListSummary]): the main list's unread chats and the chats among them that
     * mention the user, the chats Catch-up would go through ([CatchUpChats]), whether a chat has a draft (main or
     * archived) for the Drafts button, and the signed-in user for its avatar.
     * Apart from [uiState], so a count changing recomposes the header alone; at most one change every [COUNT_PACE]
     * ([atCountPace]). Nothing in the archive, whose header has no status line.
     */
    val summary: StateFlow<ChatListSummary> =
        if (kind == ChatListKind.Archive) {
            MutableStateFlow(ChatListSummary())
        } else {
            val mentions =
                chatRepository
                    .chatList(ChatListType.Main)
                    .without(lockedChatIds)
                    .map { chats -> chats.count { it.unreadMentionCount > 0 } }
                    .distinctUntilChanged()
            // The name and photo only: the user's own status changes nothing here.
            val account =
                combine(userRepository.myUserId, userRepository.profiles) { id, users ->
                    id?.let(users::get)?.let { HeaderAccount(it.id, it.fullName(), it.profilePhoto) }
                }.distinctUntilChanged()
            val catchUp = catchUpChats.count
            val hasDrafts =
                combine(chatRepository.chatList(ChatListType.Main), chatRepository.chatList(ChatListType.Archive)) {
                    main,
                    archive,
                    ->
                    main + archive
                }.without(lockedChatIds)
                    .map { chats -> chats.any { it.shownDraft != null } }
                    .distinctUntilChanged()
            combine(
                chatRepository.unreadChatCount(ChatListType.Main),
                mentions,
                account,
                catchUp,
                combine(
                    hasDrafts,
                    // Not offered until known: the header shows without waiting for the dismissals or the notes.
                    unansweredChats.offeredAt(minutes).onStart { emit(false) },
                    ::Pair,
                ),
            ) { unread, mentioned, me, catchUpCount, (drafts, unanswered) ->
                ChatListSummary(
                    unreadChats = unread.unreadUnmutedCount,
                    mentionChats = mentioned,
                    account = me,
                    catchUpChats = catchUpCount,
                    hasDrafts = drafts,
                    hasUnanswered = unanswered,
                )
            }.distinctUntilChanged()
                .atCountPace()
                .flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatListSummary())
        }

    init {
        viewModelScope.launch {
            selectedList.collect { list ->
                currentList = list
                // Conversations opened from the Chats tab go on in its folder; keyword alerts are the main list's.
                if (kind == ChatListKind.Chats) {
                    shownLists.show(if (list == KEYWORD_ALERTS_LIST) ChatListType.Main else list)
                }
                // The screen reports the new list's end afresh.
                isNearEnd = false
                val load = loads.value[list]
                if (load == null || (load.error != null && !load.isLoading)) loadPage(list)
            }
        }
        // The first page of the folders beside the one shown, so a swipe or a tap shows their chats at once.
        viewModelScope.launch {
            neighbourTypes.collect { types ->
                listOfNotNull(types.previous, types.next).forEach { if (loads.value[it] == null) loadPage(it) }
            }
        }
    }

    fun onEvent(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.SelectFolder -> {
                // The selection belongs to the list shown.
                if (event.folderId != (currentList as? ChatListType.Folder)?.id) selection.clear()
                savedStateHandle[KEY_FOLDER] = event.folderId
            }

            is ChatListEvent.NearEndChanged, is ChatListEvent.RowsShown -> {
                onListShown(event)
            }

            ChatListEvent.Retry -> {
                loadPage(currentList)
            }

            is ChatListEvent.ChatClicked -> {
                selection.tap(event.chatId, uiState.value.shownChatIds) {
                    val messageId = shownKeywordMessages[event.chatId].takeIf { event.topicId == null }
                    effectChannel.trySend(ChatListEffect.OpenChat(event.chatId, messageId, event.topicId))
                }
            }

            is ChatListEvent.ChatLongClicked -> {
                selection.toggle(event.chatId, uiState.value.shownChatIds)
            }

            ChatListEvent.ClearSelection -> {
                selection.clear()
            }

            is ChatListEvent.RowAction -> {
                runAction(event.action, uiState.value.rows.filter { it.id == event.chatId }, endsSelection = false)
            }

            is ChatListEvent.SelectionAction -> {
                val selected =
                    uiState.value.selection
                        ?.chatIds
                        .orEmpty()
                runAction(event.action, uiState.value.rows.filter { it.id in selected }, endsSelection = true)
            }

            is ChatListEvent.UndoArchive -> {
                launchAction(ChatRowAction.Unarchive, event.chatIds)
            }

            ChatListEvent.OpenArchive -> {
                effectChannel.trySend(ChatListEffect.OpenArchive)
            }

            ChatListEvent.NewChat -> {
                effectChannel.trySend(ChatListEffect.OpenNewChat)
            }

            ChatListEvent.OpenArchiveSettings, is ChatListEvent.SetArchiveRowStyle, is ChatListEvent.MarkAllAsRead -> {
                listActions.onEvent(event)
            }

            is ChatListEvent.ConfirmDelete -> {
                confirmDelete(event.forEveryone)
            }

            ChatListEvent.DismissDelete -> {
                deleteDialog.value = null
            }

            is ChatListEvent.ToggleCommunity, is ChatListEvent.ShowCommunityChatsSeparately -> {
                communities.onEvent(event)
            }
        }
    }

    /** What the list shows: whether it nears its end ([onNearEndChanged]), and which rows ([ForumRowTopics]). */
    private fun onListShown(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.NearEndChanged -> onNearEndChanged(event.isNearEnd)
            is ChatListEvent.RowsShown -> forumTopics.onShown(event.chatIds)
            else -> Unit
        }
    }

    /** The end coming near loads the next page, also after a failure; leaving it stops loading after this page. */
    private fun onNearEndChanged(nearEnd: Boolean) {
        isNearEnd = nearEnd
        if (nearEnd) loadPage(currentList)
    }

    /**
     * Loads the next page of [list]: [FIRST_PAGE_SIZE] chats until a page succeeded, then [ChatRepository.PAGE_SIZE].
     * Nothing while a page of the list loads or once it is complete. A page that succeeds while the screen still shows
     * the end of this list loads the next one, as the chats it added (if any) did not move the end away; a failed page
     * waits for [ChatListEvent.Retry] or the end coming near again.
     */

    private fun loadPage(list: ChatListType) {
        if (list == KEYWORD_ALERTS_LIST) {
            // Its chats are those with a match, which TDLib has announced: nothing to load.
            loads.update { it.putting(list, ListLoad(hasLoadedPage = true, isComplete = true)) }
            return
        }
        val load = loads.value[list] ?: ListLoad()
        if (load.isLoading || load.isComplete) return
        loads.update { it.putting(list, load.copy(isLoading = true, error = null)) }
        val limit = if (load.hasLoadedPage) ChatRepository.PAGE_SIZE else FIRST_PAGE_SIZE
        viewModelScope.launch {
            val result = chatRepository.loadChats(list, limit)
            loads.update { loads ->
                val current = loads[list] ?: ListLoad()
                val loaded =
                    when (result) {
                        is Outcome.Success -> current.copy(hasLoadedPage = true, isComplete = !result.value)
                        is Outcome.Failure -> current.copy(error = result.error)
                    }
                loads.putting(list, loaded.copy(isLoading = false))
            }
            val hasMore = (result as? Outcome.Success)?.value == true
            if (hasMore && isNearEnd && list == currentList) loadPage(list)
        }
    }

    /**
     * [action] on the chats of [rows] it applies to; deleting asks first. A toolbar action ([endsSelection]) ends
     * selection mode at once: the rows change through TDLib's updates.
     */
    private fun runAction(
        action: ChatRowAction,
        rows: List<ChatListItemModel>,
        endsSelection: Boolean,
    ) {
        if (action == ChatRowAction.Delete) {
            requestDelete(rows)
        } else {
            if (endsSelection) selection.clear()
            launchAction(action, targetsOf(action, rows))
        }
    }

    /** Runs [action] on [chatIds] in the background; archiving offers to undo it, a failure shows its error. */
    private fun launchAction(
        action: ChatRowAction,
        chatIds: List<Long>,
    ) {
        if (chatIds.isEmpty()) return
        val list = currentList
        viewModelScope.launch {
            val result = chatRepository.perform(action, chatIds, list)
            if (action == ChatRowAction.Archive && result.done.isNotEmpty()) {
                effectChannel.trySend(ChatListEffect.ChatsArchived(result.done.toImmutableList()))
            }
            result.error?.let { effectChannel.trySend(ChatListEffect.ShowError(it)) }
        }
    }

    /**
     * Shows the delete dialog for [rows]: for several chats at once; for one chat once TDLib said what deleting it
     * does and allows, which can fail like any request (a snackbar then, and no dialog).
     */
    private fun requestDelete(rows: List<ChatListItemModel>) {
        val row = rows.singleOrNull()
        when {
            row == null && rows.isNotEmpty() -> {
                deleteDialog.value = DeleteDialog.Bulk(rows.map { it.id }.toImmutableList())
            }

            row != null && deleteRequest?.isActive != true -> {
                deleteRequest =
                    viewModelScope.launch {
                        val dialog =
                            when (val chat = chatRepository.getChat(row.id)) {
                                is Outcome.Failure -> {
                                    chat
                                }

                                is Outcome.Success -> {
                                    chatRepository.deleteOptions(row.id).map {
                                        deleteDialog(chat.value, row.title, it)
                                    }
                                }
                            }
                        when (dialog) {
                            is Outcome.Success -> deleteDialog.value = dialog.value
                            is Outcome.Failure -> effectChannel.trySend(ChatListEffect.ShowError(dialog.error))
                        }
                    }
            }
        }
    }

    /**
     * Deletes the chats of the dialog shown and ends selection mode: one chat for everyone too if [forEveryone] and
     * allowed; several chats for the current user only.
     */
    private fun confirmDelete(forEveryone: Boolean) {
        val dialog = deleteDialog.value ?: return
        deleteDialog.value = null
        selection.clear()
        val list = currentList
        viewModelScope.launch {
            val error: DomainError? =
                when (dialog) {
                    is DeleteDialog.Single -> {
                        val outcome =
                            chatRepository.deleteChat(
                                dialog.chatId,
                                forEveryone && dialog.forEveryone != null,
                            )
                        (outcome as? Outcome.Failure)?.error
                    }

                    is DeleteDialog.Bulk -> {
                        chatRepository.perform(ChatRowAction.Delete, dialog.chatIds, list).error
                    }
                }
            error?.let { effectChannel.trySend(ChatListEffect.ShowError(it)) }
        }
    }

    /** Runs on [dispatcher]: maps the rows, and puts the chats of [groups] in their communities' entries. */
    @Suppress("LongParameterList") // The list, what its rows show and how far it loaded, and the mapper to use.
    private fun content(
        list: ChatListType,
        chats: List<Chat>,
        snapshot: RowSnapshot,
        load: ListLoad,
        groups: CommunityGroups,
        forumTopics: Map<Long, ForumRowSummary> = emptyMap(),
        mapper: ChatListItemMapper = this.mapper,
    ): ChatListContent {
        val error = load.error
        return when {
            chats.isNotEmpty() -> {
                val mapped = mapper.map(chats, RowSources(list, snapshot, timeFormatter(), forumTopics))
                val pinnedCount = mapped.pinnedCount()
                val (items, entries) = groupCommunities(mapped, pinnedCount, groups)
                ChatListContent.Content(items, pinnedCount = pinnedCount, communities = entries)
            }

            error != null -> {
                ChatListContent.Error(error)
            }

            load.isComplete -> {
                ChatListContent.Empty(isFolder = list is ChatListType.Folder)
            }

            else -> {
                ChatListContent.Loading
            }
        }
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** How far a list has loaded. */
    private data class ListLoad(
        val isLoading: Boolean = false,
        val hasLoadedPage: Boolean = false,
        val isComplete: Boolean = false,
        val error: DomainError? = null,
    )

    /** What the rows of the shown list are mapped from, as of one change. */
    private class ListInputs(
        val chats: List<Chat>,
        val snapshot: RowSnapshot,
        val load: ListLoad,
        val groups: CommunityGroups,
        val topics: Map<Long, ForumRowSummary>,
    )

    private class ListState(
        val list: ChatListType,
        val content: ChatListContent,
    ) {
        /** These rows with the word each chat matched ([ChatListItemModel.keyword]); as they are without words. */
        fun withKeywords(words: Map<Long, String>): ListState {
            val rows = content as? ChatListContent.Content
            if (words.isEmpty() || rows == null) return this
            val items = rows.items.map { row -> words[row.id]?.let { row.copy(keyword = it) } ?: row }
            return ListState(list, rows.copy(items = items.toImmutableList()))
        }
    }

    /** The archive row's [entry] (null while the archive is empty) and how the row shows ([style]). */
    private data class ArchivePlace(
        val entry: ArchiveEntry?,
        val style: ArchiveRowStyle,
    ) {
        /** The row of the main list: full or compact; null while hidden or empty. */
        val row: ArchiveEntry?
            get() =
                when (style) {
                    ArchiveRowStyle.Full -> entry
                    ArchiveRowStyle.Compact -> entry?.copy(isCompact = true)
                    ArchiveRowStyle.Hidden -> null
                }

        /** The archive while its row is hidden, for the top bar's button. */
        val hidden: ArchiveEntry? get() = entry.takeIf { style == ArchiveRowStyle.Hidden }
    }

    /** The list of the folder tab before the selected one ([previous]) and after it ([next]); null for none. */
    private data class NeighbourTypes(
        val previous: ChatListType? = null,
        val next: ChatListType? = null,
    ) {
        companion object {
            fun of(
                tabs: List<FolderTab>,
                list: ChatListType,
            ): NeighbourTypes {
                val neighbours = FolderNeighbours.of(tabs, (list as? ChatListType.Folder)?.id)
                return NeighbourTypes(neighbours.previous?.listType(), neighbours.next?.listType())
            }

            private fun FolderTarget.listType(): ChatListType = folderId?.let(ChatListType::Folder) ?: ChatListType.Main
        }
    }

    /** What shows over the rows: which of them are selected, and the delete dialog. */
    private class Overlays(
        val selectedIds: Set<Long>,
        val deleteDialog: DeleteDialog?,
        val hasLockedChats: Boolean,
    )

    internal companion object {
        /** The first page of a list: enough for a screen, and fast to load at startup (`docs/PERFORMANCE.md` §2). */
        const val FIRST_PAGE_SIZE = 20

        private const val KEY_FOLDER = "chatList.folderId"

        /** The list of the "Keyword alerts" tab: a folder no TDLib folder is, whose chats come from the matches. */
        val KEYWORD_ALERTS_LIST = ChatListType.Folder(KEYWORD_ALERTS_FOLDER_ID)
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The rows shown, those in communities' entries too; none while the list loads, is empty or failed. */
private val ChatListUiState.rows: List<ChatListItemModel>
    get() = (content as? ChatListContent.Content)?.allRows.orEmpty()

private val ChatListUiState.shownChatIds: Set<Long>
    get() = rows.mapTo(HashSet()) { it.id }

/**
 * The tabs: the [folders] with their [unread] counts (the main list's first), "All chats" at [mainPosition]. A tab's
 * badge counts its unmuted unread chats; its menu marks it as read while any chat, muted or not, is unread.
 */
private fun folderTabs(
    folders: List<ChatFolder>,
    unread: List<UnreadChatCount>,
    mainPosition: Int,
    keywordChatCount: Int,
): ImmutableList<FolderTab> {
    val tabs: MutableList<FolderTab> =
        folders.mapIndexedTo(mutableListOf()) { index, folder ->
            val count = unread[index + 1]
            FolderTab.Folder(
                folder.id,
                folder.title.text,
                count.unreadUnmutedCount,
                count.unreadCount > 0,
                folder.iconName,
            )
        }
    val main = unread[0]
    tabs.add(mainPosition.coerceIn(0, folders.size), FolderTab.AllChats(main.unreadUnmutedCount, main.unreadCount > 0))
    if (keywordChatCount > 0) tabs.add(FolderTab.KeywordAlerts(keywordChatCount))
    return tabs.toImmutableList()
}

/** The Feed tab for the main list's [chats]: their channels' unread posts, once there are 2 channels or more. */
private fun feedTabOf(chats: List<Chat>): FeedTab? {
    val channels = chats.filter { (it.type as? ChatType.Supergroup)?.isChannel == true }
    return if (channels.size <
        FEED_TAB_MIN_CHANNELS
    ) {
        null
    } else {
        FeedTab(channels.sumOf(Chat::unreadCount), channels.size)
    }
}

/** Which tabs there are, as they read without their counts: what [ChatListViewModel] shows at once. */
private fun tabsShape(tabs: Pair<List<FolderTab>, FeedTab?>): Any =
    tabs.first.map { tab -> if (tab is FolderTab.Folder) Triple(tab.id, tab.title, tab.iconName) else tab.folderId } to
        (tabs.second != null)

/** The Feed tab shows from this many channels (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"). */
private const val FEED_TAB_MIN_CHANNELS = 2

/** [list]'s chats: TDLib's, or for the "Keyword alerts" tab those with a match ([KeywordAlertChats]). */
private fun chatsOf(
    list: ChatListType,
    chatRepository: ChatRepository,
    keywordChats: KeywordAlertChats,
): Flow<List<Chat>> =
    if (list == ChatListViewModel.KEYWORD_ALERTS_LIST) keywordChats.chats else chatRepository.chatList(list)

/** The word each chat of [list] matched, for its chip: only on the "Keyword alerts" tab. */
private fun keywordWordsOf(
    list: ChatListType,
    keywordHits: Flow<List<KeywordHit>>,
): Flow<Map<Long, String>> =
    if (list == ChatListViewModel.KEYWORD_ALERTS_LIST) {
        keywordHits.map { hits -> hits.associate { it.chatId to it.word } }
    } else {
        flowOf(emptyMap())
    }

/**
 * This flow run once, on [dispatcher], for every state of [ChatListViewModel] that reads it (the list, the folders
 * beside it, the tabs, the view model's own collectors), while any does; forgotten when none does, so a state that
 * comes back never starts from a stale value.
 */
private fun <T> Flow<T>.sharedIn(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
): Flow<T> = flowOn(dispatcher).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

/** These chats without those of [locked]. */
private fun Flow<List<Chat>>.without(locked: Flow<Set<Long>>): Flow<List<Chat>> =
    combine(this, locked) { chats, ids -> if (ids.isEmpty()) chats else chats.filterNot { it.id in ids } }

/** How many rows the list pins, from its first: TDLib orders pinned chats first. */
private fun List<ChatListItemModel>.pinnedCount(): Int = indexOfFirst { !it.isPinned }.let { if (it < 0) size else it }

/** The main list's neighbour with its [archiveRow] and locked chats row, as the main list shows them. */
private fun FolderNeighbourList.onMainList(
    archiveRow: ArchiveEntry?,
    hasLockedChats: Boolean,
): FolderNeighbourList =
    if (folderId != null) this else copy(state = state.copy(archive = archiveRow, hasLockedChats = hasLockedChats))
