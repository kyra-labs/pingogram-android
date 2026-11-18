package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChannelFeed
import dev.kyralabs.pingogram.core.domain.repository.ChannelFeedRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5) as [uiState]: the followed channels' posts ([ChannelFeedRepository]), as
 * cards ([feedPosts]), unread ones first.
 *
 * - **Reading**: the newest posts are read once the screen collects [uiState] (skeleton cards meanwhile), and again on
 *   [FeedEvent.Retry]; a failure shows above the posts already on the phone. TDLib keeps the rest current.
 * - **Read state**: a post seen for long enough ([FeedEvent.Seen]) is marked read in its channel, once; the posts read
 *   since the feed opened are counted for "You're all caught up". Nothing new and none read here shows "No new
 *   posts", until [FeedEvent.ShowReadPosts].
 * - **Filter**: the chosen channels and "Only unread", kept in the [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel internal constructor(
    private val repository: ChannelFeedRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        repository: ChannelFeedRepository,
        savedStateHandle: SavedStateHandle,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(repository, savedStateHandle, clock, timePatterns, dispatcher, Locale::getDefault)

    private val reads = MutableStateFlow(0)

    /** The posts reported read here, by card key, so each is reported once. */
    private val seen = mutableSetOf<String>()

    private val readHere = MutableStateFlow(0)

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<FeedEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a channel at a post. */
    val effects: Flow<FeedEffect> = effectChannel.receiveAsFlow()

    /** The newest posts' reading: null while under way, then its failure, or none. */
    private val reading: Flow<Read> =
        reads.transformLatest {
            emit(Read.Running)
            emit(Read.Done((repository.load() as? Outcome.Failure)?.error))
        }

    private val choices =
        combine(
            savedStateHandle.getStateFlow<LongArray?>(KEY_CHOSEN, null),
            savedStateHandle.getStateFlow(KEY_ONLY_UNREAD, false),
            savedStateHandle.getStateFlow(KEY_SHOW_READ, false),
            readHere,
        ) { chosen, onlyUnread, showRead, readHere ->
            Choices(FeedFilter(chosen?.toSet()?.toImmutableSet(), onlyUnread), showRead, readHere)
        }

    val uiState: StateFlow<FeedUiState> =
        combine(repository.feed, reading, choices, clock.minutes, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeedUiState())

    fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.Seen -> see(event)
            FeedEvent.Retry -> reads.update { it + 1 }
            FeedEvent.ShowReadPosts -> savedStateHandle[KEY_SHOW_READ] = true
            is FeedEvent.Open -> effectChannel.trySend(FeedEffect.OpenPost(event.chatId, event.messageId))
            is FeedEvent.SetFilter -> setFilter(event.filter)
        }
    }

    /** Marks a post read in its channel, once, if it is unread. */
    private fun see(event: FeedEvent.Seen) {
        val key = "${event.chatId}:${event.messageIds.first()}"
        val content = uiState.value.content as? FeedContent.Posts ?: return
        if (content.unread.none { it.key == key } || !seen.add(key)) return
        readHere.update { it + 1 }
        viewModelScope.launch { repository.view(event.chatId, event.messageIds) }
    }

    private fun setFilter(filter: FeedFilter) {
        savedStateHandle[KEY_CHOSEN] = filter.chosen?.toLongArray()
        savedStateHandle[KEY_ONLY_UNREAD] = filter.onlyUnread
    }

    /** Runs on the dispatcher: the cards of [feed] that [choices] let through. */
    private fun toUiState(
        feed: ChannelFeed,
        read: Read,
        choices: Choices,
        now: Instant,
    ): FeedUiState {
        val formatter = timeFormatter()
        val chosen = choices.filter.chosen
        val posts =
            feedPosts(feed.posts.filter { chosen == null || it.chat.id in chosen }) { formatter.format(it, now) }
        val (unread, older) = posts.partition(FeedPost::isUnread)
        val content =
            when {
                read == Read.Running -> {
                    FeedContent.Loading
                }

                unread.isEmpty() && choices.readHere == 0 && !choices.showRead -> {
                    FeedContent.NoNewPosts
                }

                else -> {
                    FeedContent.Posts(
                        unread = unread.toImmutableList(),
                        read = if (choices.filter.onlyUnread) persistentListOf() else older.toImmutableList(),
                        readHere = choices.readHere,
                    )
                }
            }
        return FeedUiState(content, unread.size, feed.channelCount, (read as? Read.Done)?.error, choices.filter)
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatListTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** Reading the newest posts: [Running], or [Done] with its failure, if any. */
    private sealed interface Read {
        data object Running : Read

        data class Done(
            val error: DomainError?,
        ) : Read
    }

    /** What the user chose: the [filter], whether to show read posts with nothing new, and the posts read here. */
    private data class Choices(
        val filter: FeedFilter,
        val showRead: Boolean,
        val readHere: Int,
    )

    private companion object {
        const val KEY_CHOSEN = "feed.chosen"
        const val KEY_ONLY_UNREAD = "feed.onlyUnread"
        const val KEY_SHOW_READ = "feed.showRead"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
