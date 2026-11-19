package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeSearchRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [FeedSearchViewModel] on a fake search (`REQUIREMENTS.md` FR-X5, owner decision 2026-11-19): channels' posts only,
 * after the query settles; the feed's channel filter; pages, a failed page's retry, and a new query replacing the old.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSearchViewModelTest {
    private val search = FakeSearchRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the feed. */
    private fun TestScope.viewModel(): FeedSearchViewModel {
        val viewModel =
            FeedSearchViewModel(
                searchRepository = search,
                userRepository = users,
                groupRepository = FakeGroupRepository(),
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /** Types [query] and waits until it is searched. */
    private fun TestScope.type(
        viewModel: FeedSearchViewModel,
        query: String,
    ) {
        viewModel.onEvent(FeedSearchEvent.QueryChanged(query))
        advanceTimeBy(FeedSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    private fun TestScope.send(
        viewModel: FeedSearchViewModel,
        event: FeedSearchEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun post(
        channelId: Long,
        id: Long,
    ) = FoundMessage(
        message = message(channelId, id = id),
        chat = chat(channelId, type = ChatType.Supergroup(channelId, isChannel = true)),
    )

    private fun page(
        vararg posts: FoundMessage,
        next: String? = null,
    ): Outcome<FoundMessages> =
        Outcome.Success(FoundMessages(posts.toList().toImmutableList(), next, totalCount = null))

    private val FeedSearchViewModel.found get() = uiState.value.results.map { it.chatId to it.messageId }

    @Test
    fun `a settled query searches the channels' posts only, and clearing it shows nothing`() =
        runTest {
            search.onSearchMessages = { _, _ -> page(post(1, 11), post(2, 21)) }
            val viewModel = viewModel()

            viewModel.onEvent(FeedSearchEvent.QueryChanged("rel"))
            runCurrent()
            assertTrue(search.calls.isEmpty())
            assertTrue(viewModel.uiState.value.isLoading)
            type(viewModel, " release ")

            assertEquals(listOf("messages:release:null:channels"), search.calls)
            assertEquals(listOf(1L to 11L, 2L to 21L), viewModel.found)
            assertEquals(listOf("release"), viewModel.uiState.value.highlight.terms)
            assertFalse(viewModel.uiState.value.isLoading)
            type(viewModel, "")
            assertEquals(FeedSearchUiState(), viewModel.uiState.value)
        }

    @Test
    fun `channels the feed's filter leaves out are left out until it shows all channels`() =
        runTest {
            search.onSearchMessages = { _, _ -> page(post(1, 11), post(2, 21), post(1, 12)) }
            val viewModel = viewModel()
            send(viewModel, FeedSearchEvent.ChannelsChosen(setOf(1L)))
            type(viewModel, "release")

            assertEquals(listOf(1L to 11L, 1L to 12L), viewModel.found)
            send(viewModel, FeedSearchEvent.ChannelsChosen(null))
            assertEquals(listOf(1L to 11L, 2L to 21L, 1L to 12L), viewModel.found)
            assertEquals(1, search.calls.size)
        }

    @Test
    fun `more pages are read near the end, and a failed one is read again on retry`() =
        runTest {
            val failure: Outcome<FoundMessages> = Outcome.Failure(DomainError.Network)
            var answers = listOf(page(post(1, 11), next = "o2"), failure, page(post(2, 21)))
            search.onSearchMessages = { _, _ -> answers.first().also { answers = answers.drop(1) } }
            val viewModel = viewModel()
            type(viewModel, "release")
            assertTrue(viewModel.uiState.value.canLoadMore)

            send(viewModel, FeedSearchEvent.LoadMore)
            assertEquals(DomainError.Network, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.canLoadMore)
            send(viewModel, FeedSearchEvent.Retry)

            assertEquals(
                listOf(
                    "messages:release:null:channels",
                    "messages:release:o2:channels",
                    "messages:release:o2:channels",
                ),
                search.calls,
            )
            assertEquals(listOf(1L to 11L, 2L to 21L), viewModel.found)
            assertFalse(viewModel.uiState.value.canLoadMore)
            assertEquals(null, viewModel.uiState.value.error)
        }

    @Test
    fun `a new query stops the old one's search, whose posts never show`() =
        runTest {
            val old = CompletableDeferred<Outcome<FoundMessages>>()
            search.onSearchMessages = { query, _ -> if (query == "old") old.await() else page(post(2, 21)) }
            val viewModel = viewModel()
            type(viewModel, "old")

            type(viewModel, "new")
            old.complete(page(post(1, 11)))
            runCurrent()

            assertEquals(listOf("messages:old:null:channels"), search.cancelled)
            assertEquals(listOf(2L to 21L), viewModel.found)
            assertEquals("new", viewModel.uiState.value.query)
        }
}
