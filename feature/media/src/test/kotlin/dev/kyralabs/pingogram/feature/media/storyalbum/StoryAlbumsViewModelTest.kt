package dev.kyralabs.pingogram.feature.media.storyalbum

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumCover
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A profile's story albums: shown once read, hidden without any or when they fail, read once per chat. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryAlbumsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val albums = FakeStoryAlbumRepository()

    init {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the chat's albums with their covers`() =
        runTest(dispatcher) {
            val mini = Minithumbnail(4, 3, byteArrayOf(1))
            albums.albums =
                Outcome.Success(
                    persistentListOf(
                        StoryAlbum(1, "Hampi trip", StoryAlbumCover.Video(mini, thumbnail = null)),
                        StoryAlbum(2, "Monsoon", cover = null),
                    ),
                )
            val viewModel = StoryAlbumsViewModel(albums)

            viewModel.uiState.test {
                assertEquals(StoryAlbumsUiState.Hidden, awaitItem())
                viewModel.start(CHAT)
                viewModel.start(CHAT)
                val content = awaitItem() as StoryAlbumsUiState.Content
                assertEquals(listOf("Hampi trip", "Monsoon"), content.albums.map { it.name })
                assertEquals(mini, content.albums[0].cover?.minithumbnail)
                assertNull(content.albums[0].cover?.fileIdFor(100))
                assertNull(content.albums[1].cover)
                expectNoEvents()
            }
        }

    @Test
    fun `no albums or a failure show nothing`() =
        runTest(dispatcher) {
            albums.albums = Outcome.Failure(DomainError.Network)
            val failed = StoryAlbumsViewModel(albums)
            failed.start(CHAT)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(StoryAlbumsUiState.Hidden, failed.uiState.value)

            albums.albums = Outcome.Success(persistentListOf())
            val empty = StoryAlbumsViewModel(albums)
            empty.start(CHAT)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(StoryAlbumsUiState.Hidden, empty.uiState.value)
        }

    private companion object {
        const val CHAT = 42L
    }
}
