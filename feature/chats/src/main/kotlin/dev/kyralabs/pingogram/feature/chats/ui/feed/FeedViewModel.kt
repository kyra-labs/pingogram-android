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
import dev.kyralabs.pingogram.core.domain.model.isUnreadPost
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
 * - **Read state**: a post seen for long enough ([FeedEvent.Seen]) is marked read in its channel, once, and keeps its
 *   place among the new posts; those read here are counted for "You're all caught up". Nothing new shows "No new
 *   posts", until [FeedEvent.ShowReadPosts].
 * - **Filter**: the chosen channels and "Only unread", kept in the [SavedStateHandle].
 * - **A post's menu** ([FeedEvent.Act]): open, forward, its link, a bookmark, read or its channel unread
 *   ([FeedCardActions]); results and failures come as [effects].
 * - **Sponsored messages** ([FeedSponsored]): each channel's, read once the newest posts are, after its newest post.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel internal constructor(
    private val repository: ChannelFeedRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val actions: FeedCardActions,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        repository: ChannelFeedRepository,
        savedStateHandle: SavedStateHandle,
        clock: WallClock,
        timePatterns: TimePatterns,
        actions: FeedCardActions,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(repository, savedStateHandle, clock, timePatterns, actions, dispatcher, Locale::getDefault)

    private val reads = MutableStateFlow(0)

    /** The posts reported read here, by card key, so each is reported once. */
    private val seen = mutableSetOf<String>()

    private val readHere = MutableStateFlow(0)

    /**
     * The posts unread at some point while the feed is open, by card key: they keep their place above "Earlier ·
     * read" once read, so reading never moves a card. Touched only by [toUiState], one state at a time.
     */
    private val fresh = mutableSetOf<String>()

    private var timeFormatter: ChatListTimeFormatter? = null

    private val effectChannel = Channel<FeedEffect>(Channel.BUFFERED)

    private val sponsored = FeedSponsored(actions, repository::view, viewModelScope) { effectChannel.trySend(it) }

    /** One-off effects for the screen: opening a channel at a post. */
    val effects: Flow<FeedEffect> = effectChannel.receiveAsFlow()

    /** The newest posts' reading: null while under way, then its failure, or none. */
    private val reading: Flow<Read> =
        reads.transformLatest {
            emit(Read.Running)
            emit(Read.Done((repository.load() as? Outcome.Failure)?.error))
            sponsored.load(
                repository.feed
                    .first()
                    .posts
                    .map { it.chat.id }
                    .distinct(),
            )
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
        combine(repository.feed, reading, choices, clock.minutes, sponsored.state, ::toUiState)
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeedUiState())

    fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.Seen -> see(event)
            FeedEvent.Retry -> reads.update { it + 1 }
            FeedEvent.ShowReadPosts -> savedStateHandle[KEY_SHOW_READ] = true
            is FeedEvent.Open -> effectChannel.trySend(FeedEffect.OpenPost(event.chatId, event.messageId))
            is FeedEvent.SetFilter -> setFilter(event.filter)
            is FeedEvent.Act -> act(event)
            is FeedEvent.MarkAllAsRead -> markAllAsRead(event.chosen)
            is FeedEvent.Refresh -> refresh(event)
            is FeedAdEvent -> sponsored.onEvent(event)
        }
    }

    private val mutableRefreshing = MutableStateFlow(false)

    /** A pull to refresh is under way: the indicator shows. */
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    /** Reads the posts of cards [FeedEvent.Refresh.keys] again, one call per channel; nothing if none is shown. */
    private fun refresh(event: FeedEvent.Refresh) {
        val content = uiState.value.content as? FeedContent.Posts ?: return
        val keys = event.keys.toSet()
        val posts =
            (content.unread + content.read)
                .filter { it.key in keys }
                .groupBy(FeedPost::chatId)
                .mapValues { (_, cards) -> cards.flatMap(FeedPost::messageIds) }
        if (posts.isEmpty()) return
        viewModelScope.launch {
            if (event.pulled) mutableRefreshing.value = true
            val outcome = repository.refresh(posts)
            mutableRefreshing.value = false
            if (event.pulled && outcome is Outcome.Failure) effectChannel.trySend(FeedEffect.ShowError(outcome.error))
        }
    }

    /** Marks every unread post of the [chosen] channels (null for all) read in its channel, one call per channel. */
    private fun markAllAsRead(chosen: Set<Long>?) {
        viewModelScope.launch {
            val unread =
                repository.feed
                    .first()
                    .posts
                    .filter { it.isUnreadPost && (chosen == null || it.chat.id in chosen) }
                    .groupBy({ it.chat.id }, { it.message.id })
            unread.forEach { (chatId, ids) ->
                val outcome = repository.view(chatId, ids)
                if (outcome is Outcome.Failure) effectChannel.trySend(FeedEffect.ShowError(outcome.error))
            }
        }
    }

    /** Does what post [FeedEvent.Act.key]'s menu asked, if the post is still shown. */
    private fun act(event: FeedEvent.Act) {
        val content = uiState.value.content as? FeedContent.Posts ?: return
        val post = (content.unread + content.read).firstOrNull { it.key == event.key } ?: return
        val first = post.messageIds.first()
        when (event.action) {
            FeedPostAction.Open -> effectChannel.trySend(FeedEffect.OpenPost(post.chatId, first))
            FeedPostAction.Forward -> effectChannel.trySend(FeedEffect.Forward(post.chatId, post.messageIds))
            FeedPostAction.MarkAsRead -> see(FeedEvent.Seen(post.chatId, post.messageIds))
            FeedPostAction.CopyLink -> launchAction({ actions.link(post) }) { FeedEffect.CopyLink(it) }
            FeedPostAction.Bookmark -> launchAction({ actions.bookmark(post) }) { FeedEffect.Bookmarked }
            FeedPostAction.MarkChannelUnread -> launchAction({ actions.markChannelUnread(post) }) { null }
        }
    }

    /** Runs [action], then sends [done]'s effect, or its failure. */
    private fun <T> launchAction(
        action: suspend () -> Outcome<T>,
        done: (T) -> FeedEffect?,
    ) {
        viewModelScope.launch {
            when (val outcome = action()) {
                is Outcome.Success -> done(outcome.value)?.let(effectChannel::trySend)
                is Outcome.Failure -> effectChannel.trySend(FeedEffect.ShowError(outcome.error))
            }
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
        ads: FeedAds,
    ): FeedUiState {
        val formatter = timeFormatter()
        val chosen = choices.filter.chosen
        val all = feedPosts(feed.posts) { formatter.format(it, now) }
        val posts = all.filter { chosen == null || it.chatId in chosen }
        posts.forEach { if (it.isUnread) fresh += it.key }
        val (unread, older) = posts.partition { it.key in fresh }
        val content =
            when {
                read == Read.Running -> {
                    FeedContent.Loading
                }

                unread.isEmpty() && !choices.showRead -> {
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
        val unreadCount = unread.count(FeedPost::isUnread)
        return FeedUiState(
            content = content,
            unreadCount = unreadCount,
            channelCount = feed.channelCount,
            error = (read as? Read.Done)?.error,
            filter = choices.filter,
            channels = feedChannels(all),
            ads = ads.byChannel,
            adSheet = ads.sheet,
        )
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
