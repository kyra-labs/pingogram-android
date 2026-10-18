package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The chat list (`REQUIREMENTS.md` FR-L1–L5): the chats of the selected list as [uiState], with the folder tabs and
 * the connection pill. Events come in through [onEvent].
 *
 * - **State**: one `combine` of the selected list's chats, the users, supergroups and actions that rows show, the
 *   current user, the minute ([WallClock.minutes], for times and online dots that expire), the connection, and the
 *   folders with their unread counts. Rows are mapped on [dispatcher] by a [ChatListItemMapper], which reuses rows
 *   whose chat did not change. It runs while the screen collects, and 5 s after, so a rotation does not restart it.
 * - **Folders**: the selected folder is kept in the [SavedStateHandle]. A folder that does not exist (deleted, or not
 *   reported by TDLib yet after a restart) shows the main list, without forgetting the selection.
 * - **Paging**: a list starts with a short first page ([FIRST_PAGE_SIZE]) once selected, then loads pages of
 *   [ChatRepository.PAGE_SIZE] when the list nears its end ([ChatListEvent.LoadMore]), one request per list at a
 *   time, until TDLib has no more. A failure shows [ChatListContent.Error] while there are no chats; otherwise the
 *   chats stay, and the next [ChatListEvent.LoadMore] tries again.
 *
 * It takes one repository per kind of data the list combines, plus the clock, dispatcher and language that tests
 * replace, hence `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatListViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    chatActionRepository: ChatActionRepository,
    connectionRepository: ConnectionRepository,
    private val clock: WallClock,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatActionRepository: ChatActionRepository,
        connectionRepository: ConnectionRepository,
        clock: WallClock,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle,
        chatRepository,
        userRepository,
        groupRepository,
        chatActionRepository,
        connectionRepository,
        clock,
        dispatcher,
        Locale::getDefault,
    )

    /** How far each list has loaded, by list; changed on the main thread only. */
    private val loads = MutableStateFlow<PersistentMap<ChatListType, ListLoad>>(persistentMapOf())

    /** The list shown; updated on the main thread, for events that act on it. */
    private var currentList: ChatListType = ChatListType.Main

    private val mapper = ChatListItemMapper()
    private var timeFormatter: ChatListTimeFormatter? = null

    private val selectedList: Flow<ChatListType> =
        combine(savedStateHandle.getStateFlow<Int?>(KEY_FOLDER, null), chatRepository.folders) { id, folders ->
            if (id != null && folders.any { it.id == id }) ChatListType.Folder(id) else ChatListType.Main
        }.distinctUntilChanged()

    /** What rows show besides their chats, in one snapshot. */
    private val rowSources: Flow<RowSnapshot> =
        combine(
            userRepository.users,
            groupRepository.supergroups,
            chatActionRepository.actionsByChat,
            userRepository.myUserId,
            clock.minutes,
            ::RowSnapshot,
        )

    private val listState: Flow<ListState> =
        selectedList.flatMapLatest { list ->
            combine(
                chatRepository.chatList(list),
                rowSources,
                loads.map { it[list] ?: ListLoad() }.distinctUntilChanged(),
            ) { chats, snapshot, load -> ListState(list, content(list, chats, snapshot, load)) }
        }

    private val folderTabs: Flow<ImmutableList<FolderTab>> =
        chatRepository.folders.flatMapLatest { folders ->
            if (folders.isEmpty()) {
                flowOf(persistentListOf())
            } else {
                val lists = listOf(ChatListType.Main) + folders.map { ChatListType.Folder(it.id) }
                val unread =
                    combine(lists.map(chatRepository::unreadChatCount)) { counts ->
                        counts.map { it.unreadUnmutedCount }
                    }
                combine(unread, chatRepository.mainChatListPosition) { counts, mainPosition ->
                    folderTabs(folders, counts, mainPosition)
                }
            }
        }

    val uiState: StateFlow<ChatListUiState> =
        combine(listState, connectionRepository.connectionStatus, folderTabs) { list, connection, tabs ->
            ChatListUiState(
                content = list.content,
                connection = connection,
                folderTabs = tabs,
                selectedFolderId = (list.list as? ChatListType.Folder)?.id,
            )
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatListUiState())

    init {
        viewModelScope.launch {
            selectedList.collect { list ->
                currentList = list
                val load = loads.value[list]
                if (load == null || (load.error != null && !load.isLoading)) loadPage(list)
            }
        }
    }

    fun onEvent(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.SelectFolder -> savedStateHandle[KEY_FOLDER] = event.folderId
            ChatListEvent.LoadMore -> loadPage(currentList)
            ChatListEvent.Retry -> loadPage(currentList)
        }
    }

    /**
     * Loads the next page of [list]: [FIRST_PAGE_SIZE] chats until a page succeeded, then [ChatRepository.PAGE_SIZE].
     * Nothing while a page of the list loads or once it is complete.
     */
    private fun loadPage(list: ChatListType) {
        val load = loads.value[list] ?: ListLoad()
        if (load.isLoading || load.isComplete) return
        loads.update { it.put(list, load.copy(isLoading = true, error = null)) }
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
                loads.put(list, loaded.copy(isLoading = false))
            }
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

    /** The formatter for the current time zone and language, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val current = timeFormatter
        if (current != null && current.zone == zone && current.locale == locale) return current
        return ChatListTimeFormatter(zone, locale).also { timeFormatter = it }
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

    internal companion object {
        /** The first page of a list: enough for a screen, and fast to load at startup (`docs/PERFORMANCE.md` §2). */
        const val FIRST_PAGE_SIZE = 20

        private const val KEY_FOLDER = "chatList.folderId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The tabs: the [folders] with their [unread] counts (the main list's first), "All chats" at [mainPosition]. */
private fun folderTabs(
    folders: List<ChatFolder>,
    unread: List<Int>,
    mainPosition: Int,
): ImmutableList<FolderTab> {
    val tabs: MutableList<FolderTab> =
        folders.mapIndexedTo(mutableListOf()) { index, folder ->
            FolderTab.Folder(folder.id, folder.title.text, unread[index + 1])
        }
    tabs.add(mainPosition.coerceIn(0, folders.size), FolderTab.AllChats(unread[0]))
    return tabs.toImmutableList()
}
