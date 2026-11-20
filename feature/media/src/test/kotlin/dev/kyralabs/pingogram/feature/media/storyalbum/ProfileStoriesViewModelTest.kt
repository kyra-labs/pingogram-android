package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.feature.media.storyalbum.FakeStoryAlbumRepository.Companion.story
import dev.kyralabs.pingogram.feature.media.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.media.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

/**
 * A profile's Stories tab (owner decision 2026-11-20): its albums with their counts above its stories with their days,
 * paging, nothing at all, and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStoriesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val albums = FakeStoryAlbumRepository()
    private val users = FakeUserRepository()

    init {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.started(): ProfileStoriesViewModel {
        val viewModel = ProfileStoriesViewModel(albums, users, FakeWallClock(NOW), FakeTimePatterns()) { Locale.US }
        viewModel.start(CHAT)
        runCurrent()
        return viewModel
    }

    private val ProfileStoriesViewModel.content get() = uiState.value as ProfileStoriesUiState.Content

    @Test
    fun `shows the albums with their counts above the profile's stories with their days`() =
        runTest(dispatcher) {
            albums.albums = Outcome.Success(persistentListOf(StoryAlbum(7, "Hampi trip", cover = null)))
            albums.totalCount = 12
            albums.profile = listOf(story(9, OCT_3), story(4, LAST_YEAR))

            val content = started().content

            assertEquals(listOf("Hampi trip" to 12), content.albums.map { it.name to it.count })
            assertEquals(listOf(9, 4), content.stories.map { it.id })
            assertEquals(listOf("Oct 3", "Dec 31, 2025"), content.stories.map { it.date })
            assertTrue(content.endReached)
        }

    @Test
    fun `neither stories nor albums says so, and the albums show without stories`() =
        runTest(dispatcher) {
            assertEquals(ProfileStoriesUiState.Empty, started().uiState.value)

            albums.albums = Outcome.Success(persistentListOf(StoryAlbum(7, "Hampi trip", cover = null)))
            val viewModel =
                ProfileStoriesViewModel(albums, users, FakeWallClock(NOW), FakeTimePatterns()) { Locale.US }.also {
                    it.start(CHAT)
                    runCurrent()
                }

            assertEquals(1, viewModel.content.albums.size)
            assertTrue(viewModel.content.stories.isEmpty())
        }

    @Test
    fun `pages after the last story near the end, once, and stops at the end`() =
        runTest(dispatcher) {
            albums.profile = (45 downTo 1).map { story(it, OCT_3) }
            val viewModel = started()
            assertEquals(30, viewModel.content.stories.size)

            viewModel.onEvent(ProfileStoriesEvent.LoadMore)
            viewModel.onEvent(ProfileStoriesEvent.LoadMore)
            assertTrue(viewModel.content.loadingMore)
            runCurrent()

            assertEquals(listOf(0 to 30, 16 to 30), albums.profileRequests)
            assertEquals(45, viewModel.content.stories.size)
            assertTrue(viewModel.content.endReached)
            viewModel.onEvent(ProfileStoriesEvent.LoadMore)
            runCurrent()
            assertEquals(2, albums.profileRequests.size)
        }

    @Test
    fun `a failed first page is an error to retry, and failed albums are left out`() =
        runTest(dispatcher) {
            albums.albums = Outcome.Failure(DomainError.Network)
            albums.profileFailure = Outcome.Failure(DomainError.Network)
            val viewModel = started()
            assertEquals(ProfileStoriesUiState.Error(DomainError.Network), viewModel.uiState.value)

            albums.profile = listOf(story(9, OCT_3))
            viewModel.onEvent(ProfileStoriesEvent.Retry)
            runCurrent()

            assertEquals(listOf(9), viewModel.content.stories.map { it.id })
            assertTrue(viewModel.content.albums.isEmpty())
        }

    @Test
    fun `an album whose count fails shows without one`() =
        runTest(dispatcher) {
            albums.albums = Outcome.Success(persistentListOf(StoryAlbum(7, "Hampi trip", cover = null)))
            albums.countFailure = Outcome.Failure(DomainError.Network)
            albums.profile = listOf(story(9, OCT_3))

            assertNull(
                started()
                    .content.albums
                    .single()
                    .count,
            )
        }

    @Test
    fun `the user's own profile with stories can create an album, someone else's cannot`() =
        runTest(dispatcher) {
            albums.profile = listOf(story(9, OCT_3))
            users.myUserId.value = CHAT
            assertTrue(started().content.canCreateAlbum)

            users.myUserId.value = 1L
            assertFalse(started().content.canCreateAlbum)
        }

    @Test
    fun `coming back reads the tab again, keeping it meanwhile`() =
        runTest(dispatcher) {
            albums.profile = listOf(story(9, OCT_3))
            val viewModel = started()

            albums.albums = Outcome.Success(persistentListOf(StoryAlbum(7, "Hampi trip", cover = null)))
            viewModel.onEvent(ProfileStoriesEvent.Refresh)
            assertTrue(viewModel.uiState.value is ProfileStoriesUiState.Content)
            runCurrent()

            assertEquals(listOf("Hampi trip"), viewModel.content.albums.map { it.name })
        }

    private companion object {
        const val CHAT = 42L
        val NOW: Instant = Instant.parse("2026-11-10T12:00:00Z")
        val OCT_3 = Instant.parse("2026-10-03T09:00:00Z").epochSecond
        val LAST_YEAR = Instant.parse("2025-12-31T09:00:00Z").epochSecond
    }
}
