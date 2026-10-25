package dev.kyralabs.pingogram.feature.chat.ui.topics

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
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.senderNameOf
import dev.kyralabs.pingogram.feature.chat.ui.shortNameOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * The topic list of forum [chatId] (`REQUIREMENTS.md` FR-C9) as [uiState]: the forum in the top bar and its topics.
 *
 * - **Pages** ([ForumTopicRepository.loadMore]): the first once the screen's first frame is done (dispatched, so the
 *   frame does not wait), then one after another while the list reports its end near
 *   ([ForumTopicsEvent.NearEndChanged]), until every topic is loaded. A failed first page is the screen's error, a
 *   failed next one shows below the last topic; both wait for [ForumTopicsEvent.Retry] or the end coming near again.
 * - **Topics** follow TDLib's updates through [ForumTopicRepository.topics]: names, icons, closed and pinned, their
 *   last messages and unread counts.
 * - **Rows**: the last message's sender before its preview ("You" for the user's own, none where the forum itself
 *   posts), its time as of the minute ([WallClock.minutes]) in the zone, language and clock setting.
 * - **Managing** ([TopicManagement], through [onTopicEvent]): what the user may do follows their rights in the forum
 *   ([GroupInfoRepository.groupInfo], [ForumRights]): "New topic", each topic's menu, a hidden General topic shown to
 *   those who may show it again, and dragging the pinned topics into another order, which shows at once and goes back
 *   when Telegram refuses it ([ForumTopicsEvent.Reorder], [ForumTopicsEvent.Move]). A created topic opens, a deleted
 *   one and every refusal are snackbars ([effects]).
 *
 * Mapped on [dispatcher]; it runs while the screen collects, and 5 s after, so a rotation does not restart it.
 */
@Suppress("LongParameterList") // The forum, its topics, rights and people, the clock and formats that tests replace.
@HiltViewModel(assistedFactory = ForumTopicsViewModel.Factory::class)
class ForumTopicsViewModel internal constructor(
    private val chatId: Long,
    chatRepository: ChatRepository,
    private val repository: ForumTopicRepository,
    groupInfoRepository: GroupInfoRepository,
    userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        chatRepository: ChatRepository,
        repository: ForumTopicRepository,
        groupInfoRepository: GroupInfoRepository,
        userRepository: UserRepository,
        savedStateHandle: SavedStateHandle,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        chatId,
        chatRepository,
        repository,
        groupInfoRepository,
        userRepository,
        savedStateHandle,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Creates the view model of forum `chatId`'s topic list. */
    @AssistedFactory
    interface Factory {
        fun create(chatId: Long): ForumTopicsViewModel
    }

    private val load = MutableStateFlow(PageLoad())

    private var isNearEnd = false

    private var timeFormatter: ChatTimeFormatter? = null

    private val people =
        combine(userRepository.users, userRepository.myUserId) { users, myUserId ->
            ChatPeople(users, myUserId, emptyMap())
        }

    /** The topics as TDLib last reported them, for the menu's actions. */
    @Volatile
    private var latestTopics: List<ForumTopic> = emptyList()

    private val management =
        TopicManagement(chatId, repository, viewModelScope, savedStateHandle) { id ->
            latestTopics.firstOrNull { it.id == id }
        }

    private val rights =
        groupInfoRepository
            .groupInfo(chatId)
            .map { (it as? Outcome.Success)?.value?.forumRights() ?: ForumRights.None }
            .onStart { emit(ForumRights.None) }

    /** The pinned topics' order while Telegram takes the one they were dragged into; null otherwise. */
    private val pendingOrder = MutableStateFlow<List<Int>?>(null)

    private val managing = combine(rights, pendingOrder, management.state, ::Managing)

    val uiState: StateFlow<ForumTopicsUiState> =
        combine(
            chatRepository.chat(chatId),
            repository.topics(chatId),
            load,
            people,
            combine(clock.minutes, managing, ::Pair),
        ) { chat, topics, load, people, (now, managing) -> state(chat, topics, load, people, now, managing) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ForumTopicsUiState())

    private val ownEffects = Channel<ForumTopicsEffect>(Channel.BUFFERED)

    /** A created topic to open, a deleted one and refusals, each once. */
    val effects: Flow<ForumTopicsEffect> =
        merge(
            ownEffects.receiveAsFlow(),
            management.effects.map { effect ->
                when (effect) {
                    is TopicEffect.Created -> ForumTopicsEffect.OpenTopic(effect.topicId)
                    is TopicEffect.Deleted -> ForumTopicsEffect.TopicDeleted
                    is TopicEffect.Failed -> ForumTopicsEffect.Failed(effect.error)
                }
            },
        )

    init {
        // The screen creates the view model in the frame that first composes it; the first page loads after it.
        viewModelScope.launch(Dispatchers.Main) { loadPage() }
    }

    fun onEvent(event: ForumTopicsEvent) {
        when (event) {
            is ForumTopicsEvent.NearEndChanged -> {
                isNearEnd = event.isNearEnd
                if (event.isNearEnd && uiState.value.reachedEnd == false) loadPage()
            }

            ForumTopicsEvent.Retry -> {
                loadPage()
            }

            is ForumTopicsEvent.Reorder -> {
                reorder(event.topicIds)
            }

            is ForumTopicsEvent.Move -> {
                val pinned = pinnedIds()
                val from = pinned.indexOf(event.topicId)
                val to = if (event.up) from - 1 else from + 1
                if (from >= 0 && to in pinned.indices) {
                    reorder(pinned.toMutableList().apply { add(to, removeAt(from)) })
                }
            }
        }
    }

    /** What the user does to manage topics: "New topic", a topic's menu, the editor and the question. */
    fun onTopicEvent(event: TopicEvent) = management.onEvent(event)

    /** The pinned topics in the order the list shows them. */
    private fun pinnedIds(): List<Int> =
        (uiState.value.content as? TopicsContent.Content)
            ?.topics
            ?.filter { it.isPinned }
            ?.map { it.id }
            .orEmpty()

    /** Shows the pinned topics in [topicIds]' order at once, and asks Telegram for it; a refusal takes it back. */
    private fun reorder(topicIds: List<Int>) {
        if (topicIds == pinnedIds()) return
        pendingOrder.value = topicIds
        viewModelScope.launch {
            val result = repository.reorderPinned(chatId, topicIds)
            // Taken: the repository has the order by now. Refused: the list goes back to Telegram's.
            pendingOrder.update { if (it == topicIds) null else it }
            if (result is Outcome.Failure) ownEffects.send(ForumTopicsEffect.Failed(result.error))
        }
    }

    /** Loads the next page, unless one loads; another follows while the end is still near. */
    private fun loadPage() {
        if (load.value.isLoading) return
        load.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.loadMore(chatId)
            val error = (result as? Outcome.Failure)?.error
            load.update { PageLoad(loaded = it.loaded || result is Outcome.Success, error = error) }
            // The repository has applied the page by now; a fresh look tells whether it was the last.
            val reachedEnd = result is Outcome.Success && repository.topics(chatId).first().reachedEnd
            if (result is Outcome.Success && isNearEnd && !reachedEnd) loadPage()
        }
    }

    /** Runs on the dispatcher: the header and the rows. */
    @Suppress("LongParameterList") // The flows the state combines.
    private fun state(
        chat: Chat?,
        allTopics: ForumTopicList,
        load: PageLoad,
        people: ChatPeople,
        now: Instant,
        managing: Managing,
    ): ForumTopicsUiState {
        latestTopics = allTopics.topics
        val rights = managing.rights
        val shown = allTopics.topics.filter { !(it.isGeneral && it.isHidden) || rights.canManage }
        val topics = allTopics.copy(topics = shown.inOrder(managing.pendingOrder).toImmutableList())
        val header = chat?.let { headerOf(it, topics) }
        val content =
            when {
                topics.topics.isNotEmpty() -> {
                    val formatter = timeFormatter()
                    TopicsContent.Content(
                        topics =
                            topics.topics
                                .map { it.toRow(chat, people, formatter, now, rights) }
                                .toImmutableList(),
                        reachedEnd = topics.reachedEnd,
                        isLoadingMore = load.isLoading,
                        moreError = load.error,
                        canReorder = rights.canManage && topics.topics.count { it.isPinned } > 1,
                    )
                }

                load.error != null && !load.loaded -> {
                    TopicsContent.Error(load.error)
                }

                load.loaded && topics.reachedEnd -> {
                    TopicsContent.Empty
                }

                else -> {
                    TopicsContent.Loading
                }
            }
        return ForumTopicsUiState(header, content, rights.canCreate, managing.management)
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What managing the topics adds to the list: the user's rights, a dragged order on its way, the editor. */
private data class Managing(
    val rights: ForumRights,
    val pendingOrder: List<Int>?,
    val management: TopicManagementUiState,
)

/** [this] list with its pinned topics in [order] (General first, the others after them), or as it is without one. */
private fun List<ForumTopic>.inOrder(order: List<Int>?): List<ForumTopic> {
    if (order == null) return this
    val (pinned, others) = partition { it.isPinned && !it.isGeneral }
    val general = others.filter { it.isGeneral }
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return general + pinned.sortedBy { rank[it.id] ?: Int.MAX_VALUE } + others.filterNot { it.isGeneral }
}

/** Whether a page loads, the last one's failure, and whether one ever loaded. */
private data class PageLoad(
    val isLoading: Boolean = false,
    val error: DomainError? = null,
    val loaded: Boolean = false,
)

/** Whether every topic is loaded; null while the list shows none. */
private val ForumTopicsUiState.reachedEnd: Boolean?
    get() = (content as? TopicsContent.Content)?.reachedEnd

/** The forum's avatar and name over "Topics" or "12 topics" (all of them loaded). */
private fun headerOf(
    chat: Chat,
    topics: ForumTopicList,
): ChatHeader {
    val count = topics.topics.size
    val subtitle =
        if (topics.reachedEnd && count > 0) {
            UiText.plural(R.plurals.topics_subtitle, count, count)
        } else {
            UiText.resource(R.string.topics_subtitle_forum)
        }
    return ChatHeader(chat.id, UiText.Plain(chat.title), chat.photo, null, ChatSubtitle.Status(subtitle))
}

/** The row of a topic: its last message's sender (none where the forum itself posts) and preview, and its time. */
private fun ForumTopic.toRow(
    chat: Chat?,
    people: ChatPeople,
    formatter: ChatTimeFormatter,
    now: Instant,
    rights: ForumRights,
): TopicRow {
    val last = lastMessage?.takeIf { chat != null }
    val postedByForum = (last?.sender as? MessageSender.Chat)?.chatId == chatId
    return TopicRow(
        id = id,
        name = name,
        icon = TopicIcon(icon.color, isGeneral),
        sender =
            if (last == null || chat == null || postedByForum) {
                null
            } else {
                senderNameOf(last, chat, people).bidiIsolated()
            },
        preview =
            if (last == null || chat == null) {
                null
            } else {
                messagePreviewText(last, people.myUserId) { sender -> shortNameOf(sender, chat, people) }
            },
        time = last?.let { formatter.listTime(it.date, now) },
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        isPinned = isPinned,
        isClosed = isClosed,
        isHidden = isGeneral && isHidden,
        menu = menuFor(rights),
    )
}
