package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatSearchQuery
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatSearchEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * The search in the conversation of chat [chatId] (of its forum topic or message thread [topic], if not null) for
 * `ChatViewModel` (`REQUIREMENTS.md` §5.9, in-chat search): the bar's state ([state], kept out of the screen's state,
 * as only the bars show it), the text the field edits ([query]) and the members' sheet ([senderSheet], [senderQuery]).
 *
 * - **Searching**: while search is open, the query is searched once it has not changed for [DEBOUNCE_MILLIS]
 *   ([ChatSearchRepository.search], with the picked sender and the topic); a new query, or a new sender, cancels the
 *   search still running, so the newest wins. The results are message ids, newest first; the newest shows in the list
 *   at once ([onShow], the jump a tapped quote makes, which loads the messages around one far back and highlights it).
 * - **Up and down** ([ChatSearchEvent.Older], [ChatSearchEvent.Newer]) show the next older or newer result. The next
 *   page loads a few results before the last loaded one, and when the user goes past it before it came.
 * - **The match** ([highlight]): the bubble of the result shown marks the query's words while search is open.
 * - **A sender** ([ChatSearchEvent.PickSender], in groups): the sheet lists the group's members as its field finds
 *   them (`GroupInfoRepository.members`, after a pause); the search then finds that member's messages only, every one
 *   of them without words.
 * - **A day** ([ChatSearchEvent.JumpToDate]): the list shows the first message sent that day or later, in the user's
 *   time zone ([zone]), through [onShow] like a result.
 * - **Process death**: whether search is open, the query, the sender and the result shown are kept in [savedState];
 *   search runs again after it, at the result it showed, without moving the list (which keeps its own place).
 * - **Closing** ([ChatSearchEvent.Close]) forgets the query, the sender and the results.
 *
 * Runs in [scope], the view model's, on the main thread; [onEffect] gets the snackbars of failures.
 */
@Suppress(
    "LongParameterList", // The chat, where its results and members come from, and what shows them.
    "TooManyFunctions", // One function per event of the search's bars and sheet, and the searches they start.
)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ChatSearch(
    private val chatId: Long,
    private val topic: MessageTopic?,
    repositories: ChatSearchRepositories,
    private val users: UserRepository,
    private val chats: ChatRepository,
    chat: Flow<Chat>,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId,
    private val onShow: (messageId: Long) -> Unit,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val repository = repositories.search
    private val members = repositories.members

    /** The text of the search field; kept in [savedState] on every change. */
    val query = TextFieldState(savedState[KEY_QUERY] ?: "")

    /** The text of the members' sheet's search field; not kept: the sheet starts anew. */
    val senderQuery = TextFieldState()

    private val isOpen = MutableStateFlow(savedState[KEY_OPEN] ?: false)
    private val text = MutableStateFlow(query.text.toString())
    private val sender = MutableStateFlow(savedState.get<String>(KEY_SENDER)?.let(::senderOf))
    private val found = MutableStateFlow(Found())
    private val attempts = MutableStateFlow(0)
    private val sheetOpen = MutableStateFlow(false)
    private val senderAttempts = MutableStateFlow(0)

    /** The result shown before process death, until the first search after it found it; then null. */
    private var restoredId: Long? = savedState[KEY_CURRENT]

    /** The next page loading, and the day being found. */
    private var paging: Job? = null
    private var dayJump: Job? = null

    /** Whether the chat is a group, whose members' messages search can be limited to, and whether it is secret. */
    private val kind: Flow<ChatKind> =
        chat
            .map { ChatKind.of(it.type) }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The picked sender as the chip shows them, following their name and photo. */
    private val senderShown: Flow<SearchSender?> =
        sender.flatMapLatest { picked ->
            when (picked) {
                null -> flowOf(null)
                is MessageSender.User -> users.users.map { it[picked.userId]?.let { user -> user.searchSender() } }
                is MessageSender.Chat -> chats.chat(picked.chatId).map { it?.searchSender() }
            }
        }

    /** The bar's state while search is open; null while it is closed. */
    val state: StateFlow<ChatSearchUiState?> =
        combine(isOpen, found, senderShown, kind) { open, found, sender, kind ->
            if (open) ChatSearchUiState(found.results(), sender.takeIf { kind.isGroup }, kind.isGroup) else null
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The result shown with the query's words to mark in it; null while there is none, or no words. */
    val highlight: Flow<SearchHighlight?> =
        combine(isOpen, found) { open, found ->
            val messageId = found.ids.getOrNull(found.index)
            val terms = MatchHighlight.of(found.text)
            if (open && messageId != null && terms.terms.isNotEmpty()) SearchHighlight(messageId, terms) else null
        }.distinctUntilChanged()

    /** The members' sheet; null while it is closed. */
    val senderSheet: StateFlow<SenderSheet?> =
        sheetOpen
            .flatMapLatest { open -> if (open) senderOptions() else flowOf(null) }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Starts following the field and searching, once. */
    fun start() {
        scope.launch {
            snapshotFlow { query.text.toString() }.collect { typed ->
                savedState[KEY_QUERY] = typed
                text.value = typed
            }
        }
        scope.launch {
            val typed = text.debounce { if (it.isBlank()) 0L else DEBOUNCE_MILLIS }
            combine(isOpen, typed, sender, kind, attempts) { open, typed, sender, kind, attempt ->
                SearchRequest(queryOf(typed, sender, kind).takeIf { open && !it.isEmpty }, attempt)
            }.distinctUntilChanged()
                .collectLatest { request -> search(request.query) }
        }
    }

    fun onEvent(event: ChatSearchEvent) {
        when (event) {
            ChatSearchEvent.Open -> setOpen(true)
            ChatSearchEvent.Close -> close()
            ChatSearchEvent.Older -> older()
            ChatSearchEvent.Newer -> newer()
            ChatSearchEvent.Retry -> attempts.update { it + 1 }
            ChatSearchEvent.OpenSenders -> sheetOpen.value = true
            ChatSearchEvent.DismissSenders -> closeSheet()
            is ChatSearchEvent.PickSender -> pickSender(event.sender)
            ChatSearchEvent.ClearSender -> setSender(null)
            ChatSearchEvent.RetrySenders -> senderAttempts.update { it + 1 }
            is ChatSearchEvent.JumpToDate -> jumpToDate(LocalDate.ofEpochDay(event.epochDay))
        }
    }

    private fun setOpen(open: Boolean) {
        isOpen.value = open
        savedState[KEY_OPEN] = open
    }

    private fun close() {
        setOpen(false)
        closeSheet()
        paging?.cancel()
        query.clearText()
        // At once, not when the snapshot's change reaches the field's collector: opening again starts empty.
        text.value = ""
        setSender(null)
        found.value = Found()
        savedState.remove<Long>(KEY_CURRENT)
        restoredId = null
    }

    /**
     * Searches for [query] (null: nothing to search), and shows the newest result; after process death, the result
     * shown before, paging until it is loaded, without moving the list.
     */
    private suspend fun search(query: ChatSearchQuery?) {
        paging?.cancel()
        if (query == null) {
            found.value = Found()
            return
        }
        found.value = Found(query = query, text = query.text, status = Status.Searching)
        val restored = restoredId
        val loaded = load(query, restored)
        restoredId = null
        when (loaded) {
            is Outcome.Failure -> {
                found.value = Found(query = query, text = query.text, status = Status.Failed(loaded.error))
                onEffect(ChatEffect.ShowError(loaded.error))
            }

            is Outcome.Success -> {
                val page = loaded.value
                val ids = page.messageIds.toPersistentList()
                val index = restored?.let(ids::indexOf)?.takeIf { it >= 0 } ?: 0
                found.value = Found(query, query.text, Status.Done, ids, page.totalCount, page.next, index = index)
                ids.getOrNull(index)?.let { id ->
                    savedState[KEY_CURRENT] = id
                    if (restored == null) onShow(id)
                }
            }
        }
    }

    /**
     * The first page of what [query] finds; after process death, with the pages after it up to the result shown before
     * ([restored]), at most [MAX_RESTORED_PAGES] in all, as one page. A later page that fails ends there.
     */
    private suspend fun load(
        query: ChatSearchQuery,
        restored: Long?,
    ): Outcome<ChatSearchPage> {
        val first = repository.search(chatId, query)
        var page = (first as? Outcome.Success)?.value ?: return first
        var pages = 1
        while (pages < MAX_RESTORED_PAGES && page.leadsTo(restored)) {
            val more = (repository.search(chatId, query, checkNotNull(page.next)) as? Outcome.Success)?.value ?: break
            page =
                ChatSearchPage(
                    messageIds = page.messageIds.toPersistentList().addAllNew(more.messageIds),
                    totalCount = more.totalCount ?: page.totalCount,
                    next = more.next,
                )
            pages++
        }
        return Outcome.Success(page)
    }

    private fun older() {
        val current = found.value
        if (current.status != Status.Done || current.ids.isEmpty()) return
        val next = current.index + 1
        if (next < current.ids.size) {
            moveTo(next)
            if (current.ids.size - next <= PREFETCH_DISTANCE) loadMore(advance = false)
        } else {
            loadMore(advance = true)
        }
    }

    private fun newer() {
        val current = found.value
        if (current.status == Status.Done && current.index > 0) moveTo(current.index - 1)
    }

    private fun moveTo(index: Int) {
        val id = found.value.ids.getOrNull(index) ?: return
        found.update { it.copy(index = index) }
        savedState[KEY_CURRENT] = id
        onShow(id)
    }

    /**
     * Loads the page after the loaded results, once at a time; [advance] then shows the first of it, as the user went
     * past the last one. A failure is a snackbar; the results stay.
     */
    private fun loadMore(advance: Boolean) {
        val current = found.value
        val cursor = current.next
        val query = current.query
        if (cursor == null || query == null || paging?.isActive == true) return
        found.update { it.copy(isLoadingMore = true) }
        paging =
            scope.launch {
                val result = repository.search(chatId, query, cursor)
                when (result) {
                    is Outcome.Failure -> {
                        found.update { it.copy(isLoadingMore = false) }
                        onEffect(ChatEffect.ShowError(result.error))
                    }

                    is Outcome.Success -> {
                        val page = result.value
                        val before = found.value.ids.size
                        found.update {
                            it.copy(
                                ids = it.ids.addAllNew(page.messageIds),
                                total = page.totalCount ?: it.total,
                                next = page.next,
                                isLoadingMore = false,
                            )
                        }
                        if (advance && found.value.ids.size > before) moveTo(found.value.index + 1)
                    }
                }
            }
    }

    private fun pickSender(picked: MessageSender) {
        closeSheet()
        setSender(picked)
    }

    private fun setSender(picked: MessageSender?) {
        sender.value = picked
        savedState[KEY_SENDER] = picked?.let(::keyOf)
    }

    private fun closeSheet() {
        sheetOpen.value = false
        senderQuery.clearText()
    }

    /** The members the sheet's field finds once it has not changed for [DEBOUNCE_MILLIS], with the picked one. */
    private fun senderOptions(): Flow<SenderSheet> {
        val typed =
            snapshotFlow { senderQuery.text.toString().trim() }
                .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }
                .distinctUntilChanged()
        val loaded =
            combine(typed, senderAttempts, ::Pair).flatMapLatest { (typed, _) ->
                flow {
                    emit(null)
                    emit(members.members(chatId, MemberFilter.Recent, typed))
                }
            }
        return combine(loaded, users.users, sender) { result, users, picked ->
            when (result) {
                null -> {
                    SenderSheet(options = null)
                }

                is Outcome.Failure -> {
                    SenderSheet(options = persistentListOf(), error = result.error)
                }

                is Outcome.Success -> {
                    val options =
                        result.value.members
                            .filter { it.role.isMember }
                            .mapNotNull { member -> optionOf(member.sender, member.role, users, picked) }
                    SenderSheet(options.toImmutableList())
                }
            }
        }
    }

    /** The member as the sheet lists them; null for a chat TDLib has not announced. */
    private suspend fun optionOf(
        member: MessageSender,
        role: MemberRole,
        users: Map<Long, User>,
        picked: MessageSender?,
    ): SenderOption? {
        val subtitle = role.label()?.let { UiText.resource(it) }
        return when (member) {
            is MessageSender.User -> {
                users[member.userId]?.let { user ->
                    SenderOption(
                        sender = member,
                        id = user.id,
                        name = user.fullName(),
                        photo = user.profilePhoto,
                        subtitle = subtitle,
                        selected = member == picked,
                    )
                }
            }

            is MessageSender.Chat -> {
                chats.chat(member.chatId).first()?.let { chat ->
                    SenderOption(
                        sender = member,
                        id = chat.id,
                        name = UiText.Plain(chat.title),
                        photo = chat.photo,
                        subtitle = subtitle,
                        selected = member == picked,
                    )
                }
            }
        }
    }

    private fun jumpToDate(day: LocalDate) {
        dayJump?.cancel()
        dayJump =
            scope.launch {
                val zone = zone()
                val start = day.atStartOfDay(zone).toInstant()
                val end = day.plusDays(1).atStartOfDay(zone).toInstant()
                when (val message = repository.messageForDay(chatId, start, end)) {
                    is Outcome.Success -> onShow(message.value)
                    is Outcome.Failure -> onEffect(message.error.dayEffect())
                }
            }
    }

    private fun queryOf(
        typed: String,
        sender: MessageSender?,
        kind: ChatKind,
    ) = ChatSearchQuery(
        text = typed.trim(),
        sender = sender.takeIf { kind.isGroup },
        topic = topic,
        isSecretChat = kind.isSecret,
    )

    /** A query to search, null for none, and the attempt, so that "Try again" searches it again. */
    private data class SearchRequest(
        val query: ChatSearchQuery?,
        val attempt: Int,
    )

    /** Whether a search runs, found something, or failed. */
    private sealed interface Status {
        data object Idle : Status

        data object Searching : Status

        data object Done : Status

        data class Failed(
            val error: DomainError,
        ) : Status
    }

    /**
     * What [query] (its words: [text]) found: [ids] newest first, about [total] in all, the next page from [next], and
     * the result shown at [index].
     */
    private data class Found(
        val query: ChatSearchQuery? = null,
        val text: String = "",
        val status: Status = Status.Idle,
        val ids: PersistentList<Long> = persistentListOf(),
        val total: Int? = null,
        val next: ChatSearchCursor? = null,
        val isLoadingMore: Boolean = false,
        val index: Int = 0,
    ) {
        fun results(): SearchResults =
            when (status) {
                Status.Idle -> {
                    SearchResults.Idle
                }

                Status.Searching -> {
                    SearchResults.Searching
                }

                is Status.Failed -> {
                    SearchResults.Failed(status.error)
                }

                Status.Done -> {
                    if (ids.isEmpty()) {
                        SearchResults.NoResults
                    } else {
                        SearchResults.Found(
                            position = index + 1,
                            total = maxOf(total ?: ids.size, ids.size),
                            isTotalApproximate = total == null && next != null,
                            hasOlder = index + 1 < ids.size || next != null,
                            hasNewer = index > 0,
                            isLoadingOlder = isLoadingMore,
                        )
                    }
                }
            }
    }

    /** What search needs to know of the chat. */
    private data class ChatKind(
        val isGroup: Boolean,
        val isSecret: Boolean,
    ) {
        companion object {
            fun of(type: ChatType) =
                ChatKind(
                    isGroup = type is ChatType.BasicGroup || (type is ChatType.Supergroup && !type.isChannel),
                    isSecret = type is ChatType.Secret,
                )
        }
    }

    internal companion object {
        /** How long the query waits for the next key before it is searched. */
        const val DEBOUNCE_MILLIS = 300L

        /** Results from the last loaded one at which the next page loads. */
        const val PREFETCH_DISTANCE = 5

        /** How many pages the search after process death goes through to find the result it showed. */
        const val MAX_RESTORED_PAGES = 5

        const val KEY_OPEN = "chat.search.open"
        const val KEY_QUERY = "chat.search.query"
        const val KEY_SENDER = "chat.search.sender"
        const val KEY_CURRENT = "chat.search.current"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Message [messageId] is the result shown, and marks [terms] in its text. */
internal data class SearchHighlight(
    val messageId: Long,
    val terms: MatchHighlight,
)

/**
 * Whether the result shown before process death ([restored]) may be on a page after this one: not on it, and older
 * than its oldest result, with a page after it.
 */
private fun ChatSearchPage.leadsTo(restored: Long?): Boolean {
    if (restored == null || next == null || restored in messageIds) return false
    return messageIds.isEmpty() || restored < messageIds.last()
}

/** [this] with the ids of [more] it does not hold yet, after its own. */
private fun PersistentList<Long>.addAllNew(more: ImmutableList<Long>): PersistentList<Long> =
    addAll(more.filterNot { it in this })

/** "u42" or "c-100" for the saved state. */
private fun keyOf(sender: MessageSender): String =
    when (sender) {
        is MessageSender.User -> "u${sender.userId}"
        is MessageSender.Chat -> "c${sender.chatId}"
    }

private fun senderOf(key: String): MessageSender? {
    val id = key.drop(1).toLongOrNull() ?: return null
    return when (key.firstOrNull()) {
        'u' -> MessageSender.User(id)
        'c' -> MessageSender.Chat(id)
        else -> null
    }
}

private fun User.searchSender() = SearchSender(MessageSender.User(id), id, fullName(), profilePhoto)

private fun Chat.searchSender() = SearchSender(MessageSender.Chat(id), id, UiText.Plain(title), photo)

/** "Owner" or "Admin" under a member's name; nothing for others. */
private fun MemberRole.label(): Int? =
    when (this) {
        is MemberRole.Owner -> R.string.search_sender_owner
        is MemberRole.Administrator -> R.string.search_sender_admin
        else -> null
    }

/** The snackbar of a day that could not be shown: no messages up to it, or the failure. */
private fun DomainError.dayEffect(): ChatEffect =
    if (this == DomainError.NotFound) ChatEffect.NoMessagesForDay else ChatEffect.ShowError(this)
