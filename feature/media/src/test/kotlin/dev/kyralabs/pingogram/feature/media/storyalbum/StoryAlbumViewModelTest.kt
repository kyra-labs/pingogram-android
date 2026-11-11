package dev.kyralabs.pingogram.feature.media.storyalbum

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.feature.media.storyalbum.FakeStoryAlbumRepository.Companion.story
import dev.kyralabs.pingogram.feature.media.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

/** An opened story album: its name, poster and count, its stories with their days, paging, and failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryAlbumViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val albums = FakeStoryAlbumRepository()
    private val chats = FakeChatRepository()

    init {
        Dispatchers.setMain(dispatcher)
        albums.albums = Outcome.Success(persistentListOf(StoryAlbum(ALBUM, "Hampi trip", cover = null)))
        chats.chats.value = mapOf(CHAT to chat(CHAT, "Neha Kulkarni"))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        StoryAlbumViewModel(
            StoryAlbumRoute(CHAT, ALBUM),
            albums,
            chats,
            FakeWallClock(NOW),
            FakeTimePatterns(),
        ) { Locale.US }

    @Test
    fun `shows the album's name, poster, count and its stories' days`() =
        runTest(dispatcher) {
            albums.stories = listOf(story(1, OCT_3), story(2, LAST_YEAR))

            viewModel().uiState.test {
                assertEquals(StoryAlbumUiState.Loading, awaitItem())
                val content = awaitItem() as StoryAlbumUiState.Content
                assertEquals("Hampi trip", content.name)
                assertEquals("Neha Kulkarni", content.posterName)
                assertEquals(2, content.totalCount)
                assertEquals(listOf("Oct 3", "Dec 31, 2025"), content.stories.map { it.date })
                assertTrue(content.endReached)
            }
        }

    @Test
    fun `loads the next page near the end, once, and stops at the end`() =
        runTest(dispatcher) {
            albums.stories = (1..45).map { story(it, OCT_3) }
            val viewModel = viewModel()

            viewModel.uiState.test {
                skipItems(1)
                assertEquals(30, (awaitItem() as StoryAlbumUiState.Content).stories.size)

                viewModel.onEvent(StoryAlbumEvent.LoadMore)
                viewModel.onEvent(StoryAlbumEvent.LoadMore)
                assertTrue((awaitItem() as StoryAlbumUiState.Content).loadingMore)
                val all = awaitItem() as StoryAlbumUiState.Content
                assertEquals(45, all.stories.size)
                assertTrue(all.endReached)

                viewModel.onEvent(StoryAlbumEvent.LoadMore)
                expectNoEvents()
            }
            assertEquals(listOf(0 to 30, 30 to 30), albums.requests)
        }

    @Test
    fun `a failed page waits for Try again`() =
        runTest(dispatcher) {
            albums.stories = (1..45).map { story(it, OCT_3) }
            val viewModel = viewModel()

            viewModel.uiState.test {
                skipItems(2)
                albums.failNext = Outcome.Failure(DomainError.Network)
                viewModel.onEvent(StoryAlbumEvent.LoadMore)
                skipItems(1)
                assertEquals(DomainError.Network, (awaitItem() as StoryAlbumUiState.Content).moreError)

                viewModel.onEvent(StoryAlbumEvent.LoadMore)
                expectNoEvents()

                viewModel.onEvent(StoryAlbumEvent.Retry)
                skipItems(1)
                assertEquals(45, (awaitItem() as StoryAlbumUiState.Content).stories.size)
            }
        }

    @Test
    fun `a failed album says why and loads again on Try again`() =
        runTest(dispatcher) {
            albums.stories = listOf(story(1, OCT_3))
            albums.failNext = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()

            viewModel.uiState.test {
                skipItems(1)
                assertEquals(StoryAlbumUiState.Error(DomainError.Network), awaitItem())

                viewModel.onEvent(StoryAlbumEvent.Retry)
                assertEquals(StoryAlbumUiState.Loading, awaitItem())
                assertEquals(1, (awaitItem() as StoryAlbumUiState.Content).stories.size)
            }
        }

    private companion object {
        const val CHAT = 42L
        const val ALBUM = 7
        val NOW: Instant = Instant.parse("2026-11-10T12:00:00Z")
        val OCT_3 = Instant.parse("2026-10-03T09:00:00Z").epochSecond
        val LAST_YEAR = Instant.parse("2025-12-31T09:00:00Z").epochSecond
    }
}
