package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChannelFeed
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.repository.ChannelFeedRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [FeedViewModel] on a fake [ChannelFeedRepository] (`REQUIREMENTS.md` FR-X5): reading once collected, unread posts
 * first, nothing new and its "Show read posts", marking a seen post read once and "all caught up", errors and their
 * retry, the filter, and opening a post.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val repository = FakeChannelFeedRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the screen. */
    private fun TestScope.viewModel(): FeedViewModel {
        val viewModel =
            FeedViewModel(
                repository = repository,
                savedStateHandle = SavedStateHandle(),
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: FeedViewModel,
        event: FeedEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    /** Post [id] of channel [chatId], read if [lastRead] is at least [id]. */
    private fun post(
        chatId: Long,
        id: Long,
        lastRead: Long = 0,
    ) = FoundMessage(
        message(chatId, id = id),
        chat(chatId, type = ChatType.Supergroup(chatId, isChannel = true)).copy(lastReadInboxMessageId = lastRead),
    )

    private fun feed(vararg posts: FoundMessage) {
        repository.feed.value = ChannelFeed(posts.toList().toImmutableList(), posts.map { it.chat.id }.distinct().size)
    }

    private val FeedViewModel.posts get() = uiState.value.content as FeedContent.Posts

    private fun FeedContent.Posts.keys() = unread.map { it.key } to read.map { it.key }

    @Test
    fun `the newest posts are read once collected, with skeletons meanwhile, unread ones first`() =
        runTest {
            feed(post(1, 12), post(2, 25, lastRead = 25), post(1, 11))
            val load = CompletableDeferred<Outcome<Unit>>()
            repository.nextLoad = load
            val viewModel = viewModel()
            assertEquals(FeedContent.Loading, viewModel.uiState.value.content)

            load.complete(Outcome.Success(Unit))
            runCurrent()

            assertEquals(1, repository.loads)
            assertEquals(listOf("1:12", "1:11") to listOf("2:25"), viewModel.posts.keys())
            assertEquals(2, viewModel.uiState.value.unreadCount)
            assertEquals(2, viewModel.uiState.value.channelCount)
        }

    @Test
    fun `with nothing new the feed says so, until read posts are asked for`() =
        runTest {
            feed(post(1, 12, lastRead = 12))
            val viewModel = viewModel()
            assertEquals(FeedContent.NoNewPosts, viewModel.uiState.value.content)

            send(viewModel, FeedEvent.ShowReadPosts)

            assertEquals(emptyList<String>() to listOf("1:12"), viewModel.posts.keys())
        }

    @Test
    fun `a seen unread post is marked read once and counted for all caught up`() =
        runTest {
            feed(post(1, 12))
            val viewModel = viewModel()

            send(viewModel, FeedEvent.Seen(1, persistentListOf(12)))
            send(viewModel, FeedEvent.Seen(1, persistentListOf(12)))
            feed(post(1, 12, lastRead = 12))
            runCurrent()

            assertEquals(listOf(1L to listOf(12L)), repository.viewed)
            assertEquals(1, viewModel.posts.readHere)
            assertEquals(emptyList<String>() to listOf("1:12"), viewModel.posts.keys())
        }

    @Test
    fun `a failed read shows its error, and a retry reads again`() =
        runTest {
            feed(post(1, 12))
            repository.result = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            assertEquals(DomainError.Network, viewModel.uiState.value.error)
            assertEquals(listOf("1:12"), viewModel.posts.unread.map { it.key })

            repository.result = Outcome.Success(Unit)
            send(viewModel, FeedEvent.Retry)

            assertEquals(2, repository.loads)
            assertEquals(null, viewModel.uiState.value.error)
        }

    @Test
    fun `the filter keeps the chosen channels, and only unread posts`() =
        runTest {
            feed(post(1, 12), post(2, 25), post(1, 11, lastRead = 11))
            val viewModel = viewModel()

            send(viewModel, FeedEvent.SetFilter(FeedFilter(chosen = persistentSetOf(1), onlyUnread = true)))

            assertEquals(listOf("1:12") to emptyList<String>(), viewModel.posts.keys())
            assertEquals(persistentSetOf(1L), viewModel.uiState.value.filter.chosen)
        }

    @Test
    fun `a card opens its channel at the post`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<FeedEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, FeedEvent.Open(chatId = 1, messageId = 12))

            assertEquals(listOf(FeedEffect.OpenPost(1, 12)), effects)
        }

    /** A feed set by the test; loading answers [result], or once [nextLoad] when set; seen posts are recorded. */
    private class FakeChannelFeedRepository : ChannelFeedRepository {
        override val feed = MutableStateFlow(ChannelFeed(persistentListOf(), 0))
        var result: Outcome<Unit> = Outcome.Success(Unit)
        var nextLoad: CompletableDeferred<Outcome<Unit>>? = null
        var loads = 0
        val viewed = mutableListOf<Pair<Long, List<Long>>>()

        override suspend fun load(): Outcome<Unit> {
            loads++
            return nextLoad?.also { nextLoad = null }?.await() ?: result
        }

        override suspend fun view(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> {
            viewed += chatId to messageIds
            return Outcome.Success(Unit)
        }
    }
}
