package dev.kyralabs.pingogram.feature.media.story

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.StoryViewer
import dev.kyralabs.pingogram.core.domain.model.StoryViewersPage
import dev.kyralabs.pingogram.core.testing.FakeStoryRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures
import kotlinx.collections.immutable.persistentListOf
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
import java.time.Instant

/** Who saw the user's own story (board 26): paging, filters, search and failures, on fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewersViewModelTest {
    private val stories = FakeStoryRepository()
    private val users = FakeUserRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        users.setUsers(MediaFixtures.user(7, "Aisha", "Khan"), MediaFixtures.user(8, "Meera"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): StoryViewersViewModel {
        val viewModel = StoryViewersViewModel(stories, users, FakeWallClock(NOW))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun page(
        vararg viewers: StoryViewer,
        next: String? = null,
    ) = Outcome.Success(StoryViewersPage(24, 6, persistentListOf(*viewers), next))

    @Test
    fun `shows a story's viewers with their names, ages and reactions`() =
        runTest {
            stories.onViewers = { _, _, _, _ ->
                page(
                    StoryViewer(7, NOW.epochSecond - 7_200, ReactionType.Emoji("❤")),
                    StoryViewer(8, NOW.epochSecond, null),
                )
            }
            val viewModel = viewModel()

            viewModel.show(30)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(24, state.total)
            assertEquals(6, state.reactionTotal)
            assertEquals(
                listOf(StoryViewerRow(7, "Aisha Khan", null, 7_200, "❤"), StoryViewerRow(8, "Meera", null, 0, null)),
                state.rows,
            )
            assertEquals("viewers:30::false:false:", stories.calls.single())
        }

    @Test
    fun `pages on near the end, and stops at the last page`() =
        runTest {
            stories.onViewers = { _, _, _, offset ->
                if (offset.isEmpty()) page(StoryViewer(7, 0, null), next = "2") else page(StoryViewer(8, 0, null))
            }
            val viewModel = viewModel()
            viewModel.show(30)
            runCurrent()

            viewModel.onEvent(StoryViewersEvent.LoadMore)
            runCurrent()
            viewModel.onEvent(StoryViewersEvent.LoadMore)
            runCurrent()

            assertEquals(
                listOf(7L, 8L),
                viewModel.uiState.value.rows
                    .map { it.userId },
            )
            assertFalse(viewModel.uiState.value.hasMore)
            assertEquals(2, stories.calls.size)
        }

    @Test
    fun `Reactions shows only those who reacted, and Contacts asks for contacts only`() =
        runTest {
            stories.onViewers = { _, _, _, _ ->
                page(StoryViewer(7, 0, ReactionType.Emoji("❤")), StoryViewer(8, 0, null))
            }
            val viewModel = viewModel()
            viewModel.show(30)
            runCurrent()

            viewModel.onEvent(StoryViewersEvent.Filter(StoryViewersFilter.Reactions))
            runCurrent()
            assertEquals(
                listOf(7L),
                viewModel.uiState.value.rows
                    .map { it.userId },
            )
            assertEquals("viewers:30::false:true:", stories.calls.last())

            viewModel.onEvent(StoryViewersEvent.Filter(StoryViewersFilter.Contacts))
            runCurrent()
            assertEquals("viewers:30::true:false:", stories.calls.last())
        }

    @Test
    fun `searches 300 ms after the last key`() =
        runTest {
            val viewModel = viewModel()
            viewModel.show(30)
            runCurrent()

            viewModel.onEvent(StoryViewersEvent.Query("A"))
            advanceTimeBy(100)
            viewModel.onEvent(StoryViewersEvent.Query("Ai"))
            advanceTimeBy(301)
            runCurrent()

            assertEquals(listOf("viewers:30::false:false:", "viewers:30:Ai:false:false:"), stories.calls)
        }

    @Test
    fun `a failure shows Try again, which asks again`() =
        runTest {
            stories.onViewers = { _, _, _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            viewModel.show(30)
            runCurrent()
            assertEquals(DomainError.Network, viewModel.uiState.value.error)

            stories.onViewers = { _, _, _, _ -> page(StoryViewer(7, 0, null)) }
            viewModel.onEvent(StoryViewersEvent.Retry)
            runCurrent()

            assertTrue(viewModel.uiState.value.error == null)
            assertEquals(1, viewModel.uiState.value.rows.size)
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-15T09:41:00Z")
    }
}
