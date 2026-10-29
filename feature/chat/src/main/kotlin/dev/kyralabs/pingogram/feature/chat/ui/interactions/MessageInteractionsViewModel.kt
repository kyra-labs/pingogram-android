package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageInteractionRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.DayAndTime
import dev.kyralabs.pingogram.feature.chat.ui.label
import dev.kyralabs.pingogram.feature.chat.ui.reactionType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Who saw a group message and who reacted to it (`docs/DESIGN_SYSTEM.md` §6 "Seen by and reactions"), the sheet that
 * the message's "Seen by" and "Reactions" open ([open]) once TDLib allows them; keyed by the chat like the
 * conversation's view model, which hands the request over.
 *
 * - **Tabs**: "Seen" where TDLib lists the viewers, then "All" and one per kind of reaction where it lists who reacted;
 *   the one the sheet opened at is selected, and the user switches ([InteractionsEvent.SelectTab]).
 * - **Loading**: nothing is asked before the sheet opens, and then only for the tabs the user selects, each once:
 *   the viewers all at once (`MessageInteractionRepository.viewers`), the reactions a page at a time as the end of
 *   the list comes near ([InteractionsEvent.LoadMore]). A tab's first failure shows instead of its list, a later
 *   page's at its end, each with "Try again" ([InteractionsEvent.Retry]). What a tab loaded stays while the sheet is
 *   open; closing it drops everything and cancels what is on its way.
 * - **Rows** name a user as `UserRepository` knows them now, a chat (a channel someone reacted as) by its title; the
 *   users and chats of a page are looked up before it shows, so every row has a name. Times are worded as the
 *   conversation's ([ChatTimeFormatter.dayAndTime]), anew each minute.
 * - **Opening** a row closes the sheet and opens the user's profile or the chat's info ([InteractionsEffect]).
 * - **Process death**: the request and the selected tab are kept in [savedState]; the sheet opens again at that tab,
 *   which loads again.
 *
 * It takes the repositories it reads, the clock, the platform's time patterns and the language, which tests replace.
 */
@Suppress(
    // The repositories it reads, the clock, the time patterns and the language, which tests replace.
    "LongParameterList",
    // One function per event, the loading of a tab's pages and their lookups, and the mapping into the sheet.
    "TooManyFunctions",
)
@HiltViewModel
class MessageInteractionsViewModel internal constructor(
    private val savedState: SavedStateHandle,
    private val repository: MessageInteractionRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedState: SavedStateHandle,
        repository: MessageInteractionRepository,
        userRepository: UserRepository,
        chatRepository: ChatRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(savedState, repository, userRepository, chatRepository, clock, timePatterns, Locale::getDefault)

    private val request = MutableStateFlow(savedState.restoredRequest())
    private val selected = MutableStateFlow(savedState.get<String>(KEY_TAB)?.let(::tabOf))
    private val lists = MutableStateFlow<PersistentMap<InteractionsTab, TabList>>(persistentMapOf())

    /** The users and chats of the loaded pages, as they were looked up; users then follow `UserRepository`. */
    private val lookedUp = MutableStateFlow(LookedUp())

    /** What each tab is loading, by tab. */
    private val loads = mutableMapOf<InteractionsTab, Job>()

    private var formatter: ChatTimeFormatter? = null

    private val effectChannel = Channel<InteractionsEffect>(Channel.BUFFERED)

    /** The profiles and chats to open, once each. */
    val effects: Flow<InteractionsEffect> = effectChannel.receiveAsFlow()

    /** The open sheet; null while none is. */
    val sheet: StateFlow<MessageInteractionsSheet?> =
        combine(
            combine(request, selected, lists, ::Opened),
            lookedUp,
            userRepository.users,
            clock.minutes,
        ) { opened, lookedUp, users, now -> opened.toSheet(lookedUp, users, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        // Restored after process death: the tab loads again once the frame that created the view model is done.
        if (request.value != null) viewModelScope.launch(Dispatchers.Main) { selected.value?.let(::loadFirst) }
    }

    /** Opens the sheet for [request] at its viewers or its reactions, replacing one that is open. */
    fun open(request: MessageInteractionsRequest) {
        val tabs = request.tabs()
        if (tabs.isEmpty()) return
        close()
        val tab = if (request.opensReactions) tabs.firstOrNull { it != InteractionsTab.Seen } else null
        this.request.value = request
        savedState.save(request)
        select(tab ?: tabs.first())
    }

    fun onEvent(event: InteractionsEvent) {
        when (event) {
            is InteractionsEvent.SelectTab -> select(event.tab)
            InteractionsEvent.LoadMore -> selected.value?.let(::loadMore)
            InteractionsEvent.Retry -> selected.value?.let(::retry)
            is InteractionsEvent.Open -> openSender(event.sender)
            InteractionsEvent.Dismiss -> close()
        }
    }

    private fun select(tab: InteractionsTab) {
        val opened = request.value ?: return
        if (tab !in opened.tabs()) return
        selected.value = tab
        savedState[KEY_TAB] = tab.key
        if (tab !in lists.value) loadFirst(tab)
    }

    private fun retry(tab: InteractionsTab) {
        val list = lists.value[tab] ?: return
        when {
            list.firstError != null -> loadFirst(tab)
            list.moreError != null -> loadMore(tab)
        }
    }

    private fun loadFirst(tab: InteractionsTab) {
        val opened = request.value ?: return
        loads[tab]?.cancel()
        lists.update { it.putting(tab, TabList()) }
        loads[tab] = viewModelScope.launch { loadPage(opened, tab, offset = "") }
    }

    private fun loadMore(tab: InteractionsTab) {
        val opened = request.value
        val list = lists.value[tab] ?: return
        val offset = list.nextOffset?.takeIf { list.isLoaded && loads[tab]?.isActive != true }
        if (opened == null || offset == null) return
        lists.update { it.putting(tab, list.copy(isLoadingMore = true, moreError = null)) }
        loads[tab] = viewModelScope.launch { loadPage(opened, tab, offset) }
    }

    private suspend fun loadPage(
        opened: MessageInteractionsRequest,
        tab: InteractionsTab,
        offset: String,
    ) {
        when (val page = fetch(opened, tab, offset)) {
            is Outcome.Success -> {
                lookUp(page.value.entries.map { it.sender })
                updateList(tab) { list ->
                    val known = list.entries.mapTo(mutableSetOf()) { it.key }
                    list.copy(
                        entries = list.entries + page.value.entries.filterNot { it.key in known },
                        isLoaded = true,
                        isLoadingMore = false,
                        nextOffset = page.value.nextOffset,
                    )
                }
            }

            is Outcome.Failure -> {
                updateList(tab) { list ->
                    if (list.isLoaded) {
                        list.copy(isLoadingMore = false, moreError = page.error)
                    } else {
                        list.copy(firstError = page.error)
                    }
                }
            }
        }
    }

    private fun updateList(
        tab: InteractionsTab,
        change: (TabList) -> TabList,
    ) {
        lists.update { current -> current[tab]?.let { current.putting(tab, change(it)) } ?: current }
    }

    /** The page of [tab] from [offset]: the viewers (no more after them), or the reactions of the tab's kinds. */
    private suspend fun fetch(
        opened: MessageInteractionsRequest,
        tab: InteractionsTab,
        offset: String,
    ): Outcome<Page> =
        when (tab) {
            InteractionsTab.Seen -> {
                when (val viewers = repository.viewers(opened.chatId, opened.messageId)) {
                    is Outcome.Success -> {
                        val entries = viewers.value.map { Entry(MessageSender.User(it.userId), it.viewDate, null) }
                        Outcome.Success(Page(entries, nextOffset = null))
                    }

                    is Outcome.Failure -> {
                        viewers
                    }
                }
            }

            InteractionsTab.AllReactions, is InteractionsTab.OneReaction -> {
                val type = (tab as? InteractionsTab.OneReaction)?.label?.reactionType
                when (val page = repository.addedReactions(opened.chatId, opened.reactionsMessageId, type, offset)) {
                    is Outcome.Success -> {
                        val entries = page.value.reactions.map { Entry(it.sender, it.date, it.type) }
                        Outcome.Success(
                            Page(entries, nextOffset = page.value.nextOffset.takeUnless { page.value.isLast }),
                        )
                    }

                    is Outcome.Failure -> {
                        page
                    }
                }
            }
        }

    /** Looks up the users and chats of a page, from memory where they are, else from TDLib. */
    private suspend fun lookUp(senders: List<MessageSender>) =
        coroutineScope {
            val known = lookedUp.value
            val users =
                senders
                    .filterIsInstance<MessageSender.User>()
                    .map { it.userId }
                    .distinct()
                    .filterNot { it in known.users }
                    .map { async { userRepository.getUser(it).getOrNull() } }
            val chats =
                senders
                    .filterIsInstance<MessageSender.Chat>()
                    .map { it.chatId }
                    .distinct()
                    .filterNot { it in known.chats }
                    .map { async { chatRepository.getChat(it).getOrNull() } }
            val foundUsers = users.awaitAll().filterNotNull()
            val foundChats = chats.awaitAll().filterNotNull()
            lookedUp.update { current ->
                current.copy(
                    users = current.users.puttingAll(foundUsers.associateBy { it.id }),
                    chats = current.chats.puttingAll(foundChats.associateBy { it.id }),
                )
            }
        }

    private fun openSender(sender: MessageSender) {
        if (request.value == null) return
        close()
        effectChannel.trySend(
            when (sender) {
                is MessageSender.User -> InteractionsEffect.OpenProfile(sender.userId)
                is MessageSender.Chat -> InteractionsEffect.OpenChat(sender.chatId)
            },
        )
    }

    private fun close() {
        loads.values.forEach { it.cancel() }
        loads.clear()
        request.value = null
        selected.value = null
        lists.value = persistentMapOf()
        lookedUp.value = LookedUp()
        KEYS.forEach { savedState.remove<Any>(it) }
    }

    private fun Opened.toSheet(
        lookedUp: LookedUp,
        users: Map<Long, User>,
        now: Instant,
    ): MessageInteractionsSheet? {
        val opened = request
        val tab = selected
        if (opened == null || tab == null) return null
        val rows = RowMapper(timeFormatter(), now, lookedUp, users, showsReaction = tab == InteractionsTab.AllReactions)
        return MessageInteractionsSheet(
            tabs = opened.tabItems(viewerCount = lists[InteractionsTab.Seen]?.takeIf { it.isLoaded }?.entries?.size),
            selected = tab,
            content = lists[tab]?.toContent(rows::row) ?: InteractionsContent.Loading,
        )
    }

    private fun timeFormatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return formatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { formatter = it }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        const val KEY_CHAT = "interactions.chat"
        const val KEY_MESSAGE = "interactions.message"
        const val KEY_REACTIONS_MESSAGE = "interactions.reactionsMessage"
        const val KEY_VIEWERS = "interactions.viewers"
        const val KEY_REACTIONS = "interactions.reactions"
        const val KEY_COUNTS = "interactions.counts"
        const val KEY_OPENS_REACTIONS = "interactions.opensReactions"
        const val KEY_TAB = "interactions.tab"

        val KEYS =
            listOf(
                KEY_CHAT,
                KEY_MESSAGE,
                KEY_REACTIONS_MESSAGE,
                KEY_VIEWERS,
                KEY_REACTIONS,
                KEY_COUNTS,
                KEY_OPENS_REACTIONS,
                KEY_TAB,
            )

        fun SavedStateHandle.save(request: MessageInteractionsRequest) {
            set(KEY_CHAT, request.chatId)
            set(KEY_MESSAGE, request.messageId)
            set(KEY_REACTIONS_MESSAGE, request.reactionsMessageId)
            set(KEY_VIEWERS, request.canGetViewers)
            set(KEY_REACTIONS, request.reactions.mapNotNull { it.type.key }.toTypedArray())
            set(
                KEY_COUNTS,
                request.reactions
                    .filter { it.type.key != null }
                    .map { it.totalCount }
                    .toIntArray(),
            )
            set(KEY_OPENS_REACTIONS, request.opensReactions)
        }

        fun SavedStateHandle.restoredRequest(): MessageInteractionsRequest? {
            val chatId = get<Long>(KEY_CHAT)
            val messageId = get<Long>(KEY_MESSAGE)
            val reactionsMessageId = get<Long>(KEY_REACTIONS_MESSAGE)
            if (chatId == null || messageId == null || reactionsMessageId == null) return null
            val keys = get<Array<String>>(KEY_REACTIONS).orEmpty()
            val counts = get<IntArray>(KEY_COUNTS) ?: IntArray(0)
            val reactions =
                keys.zip(counts.toList()).mapNotNull { (key, count) ->
                    reactionTypeOf(key)?.let { Reaction(it, count, isChosen = false) }
                }
            return MessageInteractionsRequest(
                chatId = chatId,
                messageId = messageId,
                reactionsMessageId = reactionsMessageId,
                canGetViewers = get<Boolean>(KEY_VIEWERS) == true,
                reactions = reactions.toImmutableList(),
                opensReactions = get<Boolean>(KEY_OPENS_REACTIONS) == true,
            )
        }
    }
}

/** The request, the tab shown and what the tabs loaded, as they are now. */
private data class Opened(
    val request: MessageInteractionsRequest?,
    val selected: InteractionsTab?,
    val lists: PersistentMap<InteractionsTab, TabList>,
)

/**
 * What a tab loaded: its [entries] in Telegram's order, [isLoaded] once its first page is there, else [firstError];
 * [nextOffset] where its next page starts (null when there is none), and how that page fares.
 */
private data class TabList(
    val entries: List<Entry> = emptyList(),
    val isLoaded: Boolean = false,
    val firstError: DomainError? = null,
    val nextOffset: String? = null,
    val isLoadingMore: Boolean = false,
    val moreError: DomainError? = null,
) {
    fun toContent(row: (Entry) -> InteractionRow): InteractionsContent =
        when {
            firstError != null -> {
                InteractionsContent.Error(firstError)
            }

            !isLoaded -> {
                InteractionsContent.Loading
            }

            entries.isEmpty() && nextOffset == null -> {
                InteractionsContent.Empty
            }

            else -> {
                InteractionsContent.Rows(
                    rows = entries.map(row).toImmutableList(),
                    more =
                        when {
                            nextOffset == null -> MoreInteractions.None
                            isLoadingMore -> MoreInteractions.Loading
                            moreError != null -> MoreInteractions.Failed(moreError)
                            else -> MoreInteractions.Available
                        },
                )
            }
        }
}

/** Someone who saw the message at [date] ([reaction] null), or added [reaction] at [date] (0: not known). */
private data class Entry(
    val sender: MessageSender,
    val date: Long,
    val reaction: ReactionType?,
) {
    val key: String
        get() {
            val who =
                when (sender) {
                    is MessageSender.User -> "u${sender.userId}"
                    is MessageSender.Chat -> "c${sender.chatId}"
                }
            return reaction?.key?.let { "$who:$it" } ?: who
        }
}

/** A loaded page: its [entries], and where the next one starts (null when there is none). */
private class Page(
    val entries: List<Entry>,
    val nextOffset: String?,
)

/** The users and chats of the loaded pages, by id. */
private data class LookedUp(
    val users: PersistentMap<Long, User> = persistentMapOf(),
    val chats: PersistentMap<Long, Chat> = persistentMapOf(),
)

/** The sheet's tabs for [this]: the viewers where TDLib lists them, then every reaction and each kind, if any. */
private fun MessageInteractionsRequest.tabs(): List<InteractionsTab> = tabItems(viewerCount = null).map { it.tab }

private fun MessageInteractionsRequest.tabItems(viewerCount: Int?) =
    buildList {
        if (canGetViewers) add(InteractionsTabItem(InteractionsTab.Seen, viewerCount))
        val kinds = reactions.filter { it.type != ReactionType.Paid }
        if (kinds.isNotEmpty()) {
            add(InteractionsTabItem(InteractionsTab.AllReactions, kinds.sumOf { it.totalCount }))
            kinds.forEach { add(InteractionsTabItem(InteractionsTab.OneReaction(it.type.label), it.totalCount)) }
        }
    }.toImmutableList()

/** How the saved state keeps a tab. */
private val InteractionsTab.key: String
    get() =
        when (this) {
            InteractionsTab.Seen -> TAB_SEEN
            InteractionsTab.AllReactions -> TAB_ALL
            is InteractionsTab.OneReaction -> label.reactionType?.key.orEmpty()
        }

private fun tabOf(key: String): InteractionsTab? =
    when (key) {
        TAB_SEEN -> InteractionsTab.Seen
        TAB_ALL -> InteractionsTab.AllReactions
        else -> reactionTypeOf(key)?.let { InteractionsTab.OneReaction(it.label) }
    }

/** How the saved state keeps a reaction; null for the paid one, which has no tab. */
private val ReactionType.key: String?
    get() =
        when (this) {
            is ReactionType.Emoji -> "$EMOJI_PREFIX$emoji"
            is ReactionType.CustomEmoji -> "$CUSTOM_EMOJI_PREFIX$customEmojiId"
            ReactionType.Paid -> null
        }

private fun reactionTypeOf(key: String): ReactionType? =
    when {
        key.startsWith(EMOJI_PREFIX) -> ReactionType.Emoji(key.removePrefix(EMOJI_PREFIX))

        key.startsWith(
            CUSTOM_EMOJI_PREFIX,
        ) -> key.removePrefix(CUSTOM_EMOJI_PREFIX).toLongOrNull()?.let(ReactionType::CustomEmoji)

        else -> null
    }

private const val TAB_SEEN = "seen"
private const val TAB_ALL = "all"
private const val EMOJI_PREFIX = "emoji:"
private const val CUSTOM_EMOJI_PREFIX = "custom:"

/**
 * The rows of a tab, as of [now]: a user as [users] knows them now, else as [lookedUp]; a chat by its title; the time
 * worded by [formatter]; the reaction where the tab [showsReaction] (every reaction's).
 */
private class RowMapper(
    private val formatter: ChatTimeFormatter,
    private val now: Instant,
    private val lookedUp: LookedUp,
    private val users: Map<Long, User>,
    private val showsReaction: Boolean,
) {
    fun row(entry: Entry): InteractionRow {
        val time = entry.date.takeIf { it > 0 }?.let { timeText(it, isView = entry.reaction == null) }
        val reaction = entry.reaction?.takeIf { showsReaction }?.label
        return when (val sender = entry.sender) {
            is MessageSender.User -> {
                val user = users[sender.userId] ?: lookedUp.users[sender.userId]
                InteractionRow(
                    key = entry.key,
                    sender = sender,
                    name = user?.fullName() ?: UiText.resource(UiR.string.user_deleted_account),
                    photo = user?.profilePhoto,
                    avatarId = sender.userId,
                    time = time,
                    reaction = reaction,
                    placeholderIcon = if (user == null || user.isDeleted) UiR.drawable.ic_person_filled else null,
                )
            }

            is MessageSender.Chat -> {
                val chat = lookedUp.chats[sender.chatId]
                InteractionRow(
                    key = entry.key,
                    sender = sender,
                    name = UiText.Plain(chat?.title.orEmpty()),
                    photo = chat?.photo,
                    avatarId = sender.chatId,
                    time = time,
                    reaction = reaction,
                )
            }
        }
    }

    /** "Seen at 9:41 AM", "Reacted yesterday at 9:41 AM", "Seen Mar 17 at 9:41 AM". */
    private fun timeText(
        epochSeconds: Long,
        isView: Boolean,
    ): UiText =
        when (val at = formatter.dayAndTime(epochSeconds, now)) {
            is DayAndTime.Today -> {
                UiText.resource(
                    if (isView) R.string.interactions_seen_today else R.string.interactions_reacted_today,
                    at.time,
                )
            }

            is DayAndTime.Yesterday -> {
                val id = if (isView) R.string.interactions_seen_yesterday else R.string.interactions_reacted_yesterday
                UiText.resource(id, at.time)
            }

            is DayAndTime.OnDate -> {
                val id = if (isView) R.string.interactions_seen_on else R.string.interactions_reacted_on
                UiText.resource(id, at.date, at.time)
            }
        }
}
