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
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 *   ends selection mode; delete ends it once confirmed. Outside selection mode a tap opens the chat
 *   ([ChatListEffect.OpenChat]).
 * - **Actions** (FR-L4) run one chat after another and stop at the first failure ([perform]), which a snackbar shows
 *   ([ChatListEffect.ShowError]). Their effect on the rows comes through TDLib's updates. Archiving offers to undo it
 *   ([ChatListEffect.ChatsArchived]). Deleting asks first ([ChatListUiState.deleteDialog]), with what TDLib allows for
 *   one chat; several chats are deleted for the current user only.
 * - **Times** follow the zone, the language and the 12- or 24-hour setting ([TimePatterns]), checked at every
 *   emission, so a change shows at the next minute at the latest.
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
    archiveRowRepository: ArchiveRowRepository,
    archiveSettingsRepository: ArchiveSettingsRepository,
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
        archiveRowRepository: ArchiveRowRepository,
        archiveSettingsRepository: ArchiveSettingsRepository,
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
        archiveRowRepository,
        archiveSettingsRepository,
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

    private val selectedList: Flow<ChatListType> =
        when (kind) {
            ChatListKind.Archive -> {
                flowOf(ChatListType.Archive)
            }

            ChatListKind.Chats -> {
                combine(savedStateHandle.getStateFlow<Int?>(KEY_FOLDER, null), chatRepository.folders) { id, folders ->
                    if (id != null && folders.any { it.id == id }) ChatListType.Folder(id) else ChatListType.Main
                }.distinctUntilChanged()
            }
        }

    /** What rows show besides their chats, in one snapshot. */
    private val rowSources: Flow<RowSnapshot> =
        combine(
            combine(userRepository.users, groupRepository.supergroups, secretChatRepository.secretChats, ::Triple),
            chatActionRepository.actionsByChat,
            userRepository.myUserId,
            clock.minutes,
        ) { (users, supergroups, secretChats), actions, myUserId, now ->
            RowSnapshot(users, supergroups, secretChats, actions, myUserId, now)
        }

    private val listState: Flow<ListState> =
        selectedList.flatMapLatest { list ->
            combine(
                chatRepository.chatList(list),
                rowSources,
                loads.map { it[list] ?: ListLoad() }.distinctUntilChanged(),
            ) { chats, snapshot, load -> ListState(list, content(list, chats, snapshot, load)) }
        }

    private val folderTabs: Flow<ImmutableList<FolderTab>> =
        if (kind == ChatListKind.Archive) {
            flowOf(persistentListOf())
        } else {
            chatRepository.folders.flatMapLatest { folders ->
                if (folders.isEmpty()) {
                    flowOf(persistentListOf())
                } else {
                    val lists = listOf(ChatListType.Main) + folders.map { ChatListType.Folder(it.id) }
                    val unread = combine(lists.map(chatRepository::unreadChatCount)) { it.toList() }
                    combine(unread, chatRepository.mainChatListPosition) { counts, mainPosition ->
                        folderTabs(folders, counts, mainPosition)
                    }
                }
            }
        }

    private val archive: Flow<ArchivePlace> =
        if (kind == ChatListKind.Archive) {
            flowOf(ArchivePlace(entry = null, style = ArchiveRowStyle.Full))
        } else {
            combine(
                chatRepository.unreadChatCount(ChatListType.Archive),
                chatRepository.chatList(ChatListType.Archive),
                archiveRowRepository.style,
            ) { count, chats, style -> ArchivePlace(archiveEntry(count, chats), style) }.distinctUntilChanged()
        }

    private val overlays: Flow<Overlays> = combine(selection.ids, deleteDialog, ::Overlays)

    val uiState: StateFlow<ChatListUiState> =
        combine(listState, connectionRepository.connectionStatus, folderTabs, archive, overlays) {
            list,
            connection,
            tabs,
            archive,
            overlays,
            ->
            ChatListUiState(
                kind = kind,
                content = list.content,
                connection = connection,
                folderTabs = tabs,
                selectedFolderId = (list.list as? ChatListType.Folder)?.id,
                archive = archive.row.takeIf { list.list == ChatListType.Main },
                hiddenArchive = archive.hidden.takeIf { list.list == ChatListType.Main },
                selection = selectionOf(list.content, overlays.selectedIds),
                deleteDialog = overlays.deleteDialog,
            )
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatListUiState(kind = kind))

    init {
        viewModelScope.launch {
            selectedList.collect { list ->
                currentList = list
                // The screen reports the new list's end afresh.
                isNearEnd = false
                val load = loads.value[list]
                if (load == null || (load.error != null && !load.isLoading)) loadPage(list)
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

            is ChatListEvent.NearEndChanged -> {
                onNearEndChanged(event.isNearEnd)
            }

            ChatListEvent.Retry -> {
                loadPage(currentList)
            }

            is ChatListEvent.ChatClicked -> {
                val shown = uiState.value.shownChatIds
                if (selection.isActive(shown)) {
                    selection.toggle(event.chatId, shown)
                } else {
                    effectChannel.trySend(ChatListEffect.OpenChat(event.chatId))
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

    /** Runs on [dispatcher]: maps the rows. */
    private fun content(
        list: ChatListType,
        chats: List<Chat>,
        snapshot: RowSnapshot,
        load: ListLoad,
    ): ChatListContent {
        val error = load.error
        return when {
            chats.isNotEmpty() -> ChatListContent.Content(mapper.map(chats, snapshot.sourcesFor(list)))
            error != null -> ChatListContent.Error(error)
            load.isComplete -> ChatListContent.Empty(isFolder = list is ChatListType.Folder)
            else -> ChatListContent.Loading
        }
    }

    private fun RowSnapshot.sourcesFor(list: ChatListType) = RowSources(list, this, timeFormatter())

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

    private class ListState(
        val list: ChatListType,
        val content: ChatListContent,
    )

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

    /** What shows over the rows: which of them are selected, and the delete dialog. */
    private class Overlays(
        val selectedIds: Set<Long>,
        val deleteDialog: DeleteDialog?,
    )

    internal companion object {
        /** The first page of a list: enough for a screen, and fast to load at startup (`docs/PERFORMANCE.md` §2). */
        const val FIRST_PAGE_SIZE = 20

        private const val KEY_FOLDER = "chatList.folderId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The rows shown; none while the list loads, is empty or failed. */
private val ChatListUiState.rows: List<ChatListItemModel>
    get() = (content as? ChatListContent.Content)?.items.orEmpty()

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
): ImmutableList<FolderTab> {
    val tabs: MutableList<FolderTab> =
        folders.mapIndexedTo(mutableListOf()) { index, folder ->
            val count = unread[index + 1]
            FolderTab.Folder(folder.id, folder.title.text, count.unreadUnmutedCount, count.unreadCount > 0)
        }
    val main = unread[0]
    tabs.add(mainPosition.coerceIn(0, folders.size), FolderTab.AllChats(main.unreadUnmutedCount, main.unreadCount > 0))
    return tabs.toImmutableList()
}
