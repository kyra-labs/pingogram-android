package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.FoundPublicPosts
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.PublicPostSearchLimits
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.SearchViewModel.Companion.DEBOUNCE_MILLIS
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Global search's Posts chip ([SearchFilter.Posts]): Telegram's limits read as the query is typed, the search only on
 * the user's tap, the day's free searches used up, failures and paging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPostsTest {
    private val search = FakeSearchRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model under the Posts chip that typed [query], collected as by the screen. */
    private fun TestScope.posts(query: String = "android"): SearchViewModel {
        val viewModel =
            SearchViewModel(
                savedStateHandle = SavedStateHandle(),
                searchRepository = search,
                userRepository = users,
                groupRepository = FakeGroupRepository(),
                chatLock = FakeChatLockRepository(),
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onEvent(SearchEvent.FilterChanged(SearchFilter.Posts))
        viewModel.onEvent(SearchEvent.QueryChanged(query))
        advanceTimeBy(DEBOUNCE_MILLIS + 1)
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: SearchViewModel,
        event: SearchEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val SearchViewModel.results get() = uiState.value.content as SearchContent.Results

    private fun MessageSection.ids() = (first as SearchSection.Items).items.map { it.chatId to it.messageId }

    private fun post(
        chatId: Long,
        id: Long,
    ) = FoundMessage(
        message(chatId, id = id, content = MessageContent.Text(ChatListFixtures.text("Android 17"), null)),
        chat(chatId),
    )

    private fun found(
        vararg posts: FoundMessage,
        next: String? = null,
        left: Int = 6,
        exceeded: Boolean = false,
    ) = Outcome.Success(
        FoundPublicPosts(
            FoundMessages(posts.toList().toImmutableList(), next, totalCount = null),
            PublicPostSearchLimits(10, left, nextFreeQueryInSeconds = 3_600, starCount = 10, isQueryFree = left > 0),
            areLimitsExceeded = exceeded,
        ),
    )

    @Test
    fun `typing reads the limits and offers the search, spending nothing`() =
        runTest {
            val viewModel = posts()

            assertEquals(listOf("postLimits:android"), search.calls)
            val results = viewModel.results
            assertEquals(PostsPrompt.Offer(left = 7, daily = 10), results.posts)
            assertFalse(results.isEmpty)
            assertFalse(results.isSearching)
            assertNull(results.postsLeft)
        }

    @Test
    fun `the other sections are not searched under Posts`() =
        runTest {
            posts()

            assertTrue(search.callsOf("chats").isEmpty())
            assertTrue(search.callsOf("contacts").isEmpty())
            assertTrue(search.callsOf("messages").isEmpty())
        }

    @Test
    fun `Search posts finds them once, with how many free searches are left`() =
        runTest {
            search.onSearchPosts = { _, _ -> found(post(-100, 1), post(-101, 2)) }
            val viewModel = posts()

            send(viewModel, SearchEvent.SearchPosts)
            send(viewModel, SearchEvent.SearchPosts)

            assertEquals(listOf("posts:android:null"), search.callsOf("posts"))
            val results = viewModel.results
            assertEquals(PostsPrompt.None, results.posts)
            assertEquals(listOf(-100L to 1L, -101L to 2L), results.messages.ids())
            assertEquals(UiText.plural(R.plurals.search_posts_left, 6, 6, 10), results.postsLeft)
        }

    @Test
    fun `no free search left says when the next one comes, and offers nothing`() =
        runTest {
            search.postLimits = PublicPostSearchLimits(10, 0, 3_600, starCount = 10, isQueryFree = false)
            val viewModel = posts()

            // NOW is 9:41:30 AM UTC; the next free search an hour later.
            assertEquals(PostsPrompt.Exhausted(daily = 10, nextFree = "10:41 AM"), viewModel.results.posts)
            send(viewModel, SearchEvent.SearchPosts)
            assertTrue(search.callsOf("posts").isEmpty())
        }

    @Test
    fun `a search Telegram refuses for the limits ends with them`() =
        runTest {
            search.onSearchPosts = { _, _ -> found(left = 0, exceeded = true) }
            val viewModel = posts()

            send(viewModel, SearchEvent.SearchPosts)

            val results = viewModel.results
            assertEquals(PostsPrompt.Exhausted(daily = 10, nextFree = "10:41 AM"), results.posts)
            assertEquals(emptyList<Pair<Long, Long>>(), results.messages.ids())
            assertNull(results.postsLeft)
        }

    @Test
    fun `posts page as the list nears the end`() =
        runTest {
            search.onSearchPosts = { _, offset ->
                if (offset == null) found(post(-100, 1), next = "o1") else found(post(-100, 2))
            }
            val viewModel = posts()
            send(viewModel, SearchEvent.SearchPosts)
            assertTrue(viewModel.results.messages.canLoadMore)

            send(viewModel, SearchEvent.LoadMoreMessages)

            assertEquals(listOf("posts:android:null", "posts:android:o1"), search.callsOf("posts"))
            assertEquals(listOf(-100L to 1L, -100L to 2L), viewModel.results.messages.ids())
        }

    @Test
    fun `limits that cannot be read fail, and retry reads them again`() =
        runTest {
            search.onPostLimits = { Outcome.Failure(DomainError.Network) }
            val viewModel = posts()
            assertEquals(PostsPrompt.Failed(DomainError.Network), viewModel.results.posts)

            search.onPostLimits = { Outcome.Success(search.postLimits) }
            send(viewModel, SearchEvent.Retry)

            assertEquals(PostsPrompt.Offer(left = 7, daily = 10), viewModel.results.posts)
            assertEquals(2, search.callsOf("postLimits").size)
        }

    @Test
    fun `a failed search shows its error, and retry searches again`() =
        runTest {
            search.onSearchPosts = { _, _ -> Outcome.Failure(DomainError.FloodWait(30L)) }
            val viewModel = posts()
            send(viewModel, SearchEvent.SearchPosts)
            assertEquals(SearchSection.Failed(DomainError.FloodWait(30L)), viewModel.results.messages.first)

            search.onSearchPosts = { _, _ -> found(post(-100, 1)) }
            send(viewModel, SearchEvent.Retry)

            assertEquals(listOf(-100L to 1L), viewModel.results.messages.ids())
        }

    @Test
    fun `a new query asks again before searching`() =
        runTest {
            search.onSearchPosts = { _, _ -> found(post(-100, 1)) }
            val viewModel = posts()
            send(viewModel, SearchEvent.SearchPosts)

            viewModel.onEvent(SearchEvent.QueryChanged("kotlin"))
            advanceTimeBy(DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals(PostsPrompt.Offer(left = 7, daily = 10), viewModel.results.posts)
            assertEquals(listOf("posts:android:null"), search.callsOf("posts"))
            assertEquals(persistentListOf<Pair<Long, Long>>(), viewModel.results.messages.ids())
        }
}
