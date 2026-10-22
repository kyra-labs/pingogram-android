package dev.kyralabs.pingogram.feature.chat.ui.topics

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
 *
 * Mapped on [dispatcher]; it runs while the screen collects, and 5 s after, so a rotation does not restart it.
 */
@Suppress("LongParameterList") // The forum, its topics and people, the clock and formats that tests replace.
@HiltViewModel(assistedFactory = ForumTopicsViewModel.Factory::class)
class ForumTopicsViewModel internal constructor(
    private val chatId: Long,
    chatRepository: ChatRepository,
    private val repository: ForumTopicRepository,
    userRepository: UserRepository,
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
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(chatId, chatRepository, repository, userRepository, clock, timePatterns, dispatcher, Locale::getDefault)

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

    val uiState: StateFlow<ForumTopicsUiState> =
        combine(chatRepository.chat(chatId), repository.topics(chatId), load, people, clock.minutes, ::state)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ForumTopicsUiState())

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
    private fun state(
        chat: Chat?,
        topics: ForumTopicList,
        load: PageLoad,
        people: ChatPeople,
        now: Instant,
    ): ForumTopicsUiState {
        val header = chat?.let { headerOf(it, topics) }
        val content =
            when {
                topics.topics.isNotEmpty() -> {
                    val formatter = timeFormatter()
                    TopicsContent.Content(
                        topics = topics.topics.map { it.toRow(chat, people, formatter, now) }.toImmutableList(),
                        reachedEnd = topics.reachedEnd,
                        isLoadingMore = load.isLoading,
                        moreError = load.error,
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
        return ForumTopicsUiState(header, content)
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
    )
}
