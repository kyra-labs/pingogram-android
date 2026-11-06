package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.SearchViewModel.Companion.DEBOUNCE_MILLIS
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The search chips' counts ([SearchUiState.counts], `docs/DESIGN_SYSTEM.md` §6 "Global search"): TDLib's totals from
 * the searches already made for the query, by chip, never a request of their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchCountsTest {
    private val search =
        FakeSearchRepository().apply {
            totalCounts =
                mapOf(
                    MessageSearchFilter.All to 42,
                    MessageSearchFilter.PhotoAndVideo to 7,
                    MessageSearchFilter.Url to 3,
                )
        }
    private val chatLock = FakeChatLockRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a query counts its messages on the Messages chip, the chips not searched yet have none`() =
        runTest {
            val viewModel = searching("moon")

            assertEquals(mapOf(SearchFilter.Messages to 42), viewModel.uiState.value.counts)
        }

    @Test
    fun `each chip searched adds its count and keeps the others of the same query`() =
        runTest {
            val viewModel = searching("moon")

            select(viewModel, SearchFilter.Media)
            select(viewModel, SearchFilter.Links)
            select(viewModel, SearchFilter.Chats)

            assertEquals(
                mapOf(SearchFilter.Messages to 42, SearchFilter.Media to 7, SearchFilter.Links to 3),
                viewModel.uiState.value.counts,
            )
        }

    @Test
    fun `counting asks for nothing more than the searches themselves`() =
        runTest {
            val viewModel = searching("moon")
            select(viewModel, SearchFilter.Media)

            assertEquals(listOf("messages:moon:null", "messages:moon:null:PhotoAndVideo"), search.callsOf("messages"))
        }

    @Test
    fun `a new query drops the counts of the previous one`() =
        runTest {
            val viewModel = searching("moon")
            select(viewModel, SearchFilter.Media)
            select(viewModel, SearchFilter.Chats)

            type(viewModel, "mars")

            assertEquals(emptyMap<SearchFilter, Int>(), viewModel.uiState.value.counts)
        }

    @Test
    fun `no count where TDLib does not know the total`() =
        runTest {
            search.totalCounts = emptyMap()

            assertEquals(emptyMap<SearchFilter, Int>(), searching("moon").uiState.value.counts)
        }

    @Test
    fun `no counts while locked chats are hidden, whose messages TDLib would count`() =
        runTest {
            chatLock.lock(setOf(3L))

            assertEquals(emptyMap<SearchFilter, Int>(), searching("moon").uiState.value.counts)
        }

    /** A view model whose state is collected, as by the screen, with [query] typed and searched. */
    private fun TestScope.searching(query: String): SearchViewModel {
        val viewModel =
            SearchViewModel(
                savedStateHandle = SavedStateHandle(),
                searchRepository = search,
                userRepository = FakeUserRepository().apply { myUserId.value = ME },
                groupRepository = FakeGroupRepository(),
                chatLock = chatLock,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        type(viewModel, query)
        return viewModel
    }

    private fun TestScope.type(
        viewModel: SearchViewModel,
        query: String,
    ) {
        viewModel.onEvent(SearchEvent.QueryChanged(query))
        advanceTimeBy(DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    private fun TestScope.select(
        viewModel: SearchViewModel,
        filter: SearchFilter,
    ) {
        viewModel.onEvent(SearchEvent.FilterChanged(filter))
        runCurrent()
    }
}
