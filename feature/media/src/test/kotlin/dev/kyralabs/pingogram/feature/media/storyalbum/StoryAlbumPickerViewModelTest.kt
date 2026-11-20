package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.navigation.StoryAlbumPickerRoute
import dev.kyralabs.pingogram.feature.media.storyalbum.FakeStoryAlbumRepository.Companion.story
import dev.kyralabs.pingogram.feature.media.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

/**
 * Picking stories for one of the user's albums (owner decision 2026-11-20): a new album's from the profile, stories to
 * add (not in the album yet) or to remove (the album's), confirming in the order shown, a refusal, and a failed read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryAlbumPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val albums = FakeStoryAlbumRepository()

    init {
        Dispatchers.setMain(dispatcher)
        albums.profile = listOf(story(9, DAY), story(7, DAY), story(4, DAY))
        albums.stories = listOf(story(7, DAY))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(route: StoryAlbumPickerRoute): StoryAlbumPickerViewModel {
        val viewModel =
            StoryAlbumPickerViewModel(route, albums, FakeWallClock(NOW), FakeTimePatterns(), SavedStateHandle()) {
                Locale.US
            }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val StoryAlbumPickerViewModel.content get() = uiState.value as StoryAlbumPickerUiState.Content

    private fun TestScope.pick(
        viewModel: StoryAlbumPickerViewModel,
        vararg ids: Int,
    ) {
        ids.forEach { viewModel.onEvent(StoryAlbumPickerEvent.Toggle(it)) }
        runCurrent()
    }

    @Test
    fun `a new album is made of the profile's stories in the order shown, then picking ends`() =
        runTest(dispatcher) {
            val viewModel = viewModel(StoryAlbumPickerRoute(CHAT, name = " Hampi "))
            assertEquals(StoryAlbumPickPurpose.Create, viewModel.content.purpose)
            assertEquals(listOf(9, 7, 4), viewModel.content.stories.map { it.id })
            assertFalse(viewModel.content.canConfirm)

            viewModel.effects.test {
                pick(viewModel, 4, 9, 7, 7)
                viewModel.onEvent(StoryAlbumPickerEvent.Confirm)
                runCurrent()

                assertEquals(StoryAlbumPickerEffect.Done, awaitItem())
            }
            assertEquals(listOf("create:Hampi:[9, 4]"), albums.changes)
        }

    @Test
    fun `stories to add leave out those in the album, and stories to remove are the album's`() =
        runTest(dispatcher) {
            val adding = viewModel(StoryAlbumPickerRoute(CHAT, albumId = ALBUM))
            assertEquals(StoryAlbumPickPurpose.Add, adding.content.purpose)
            assertEquals(listOf(9, 4), adding.content.stories.map { it.id })
            pick(adding, 4)
            adding.onEvent(StoryAlbumPickerEvent.Confirm)
            runCurrent()

            val removing = viewModel(StoryAlbumPickerRoute(CHAT, albumId = ALBUM, removing = true))
            assertEquals(StoryAlbumPickPurpose.Remove, removing.content.purpose)
            assertEquals(listOf(7), removing.content.stories.map { it.id })
            pick(removing, 7)
            removing.onEvent(StoryAlbumPickerEvent.Confirm)
            runCurrent()

            assertEquals(listOf("add:$ALBUM:[4]", "remove:$ALBUM:[7]"), albums.changes)
        }

    @Test
    fun `a refusal is a snackbar and the picked stories stay`() =
        runTest(dispatcher) {
            albums.changeFailure = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel(StoryAlbumPickerRoute(CHAT, name = "Hampi"))
            pick(viewModel, 9)

            viewModel.effects.test {
                viewModel.onEvent(StoryAlbumPickerEvent.Confirm)
                runCurrent()
                assertEquals(StoryAlbumPickerEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals(setOf(9), viewModel.content.selected)
            assertTrue(viewModel.content.canConfirm)
        }

    @Test
    fun `a failed read is an error to retry`() =
        runTest(dispatcher) {
            albums.profileFailure = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel(StoryAlbumPickerRoute(CHAT, name = "Hampi"))
            assertEquals(StoryAlbumPickerUiState.Error(DomainError.Network), viewModel.uiState.value)

            viewModel.onEvent(StoryAlbumPickerEvent.Retry)
            runCurrent()

            assertEquals(3, viewModel.content.stories.size)
        }

    private companion object {
        const val CHAT = 42L
        const val ALBUM = 7
        val NOW: Instant = Instant.parse("2026-11-10T12:00:00Z")
        val DAY = Instant.parse("2026-10-03T09:00:00Z").epochSecond
    }
}
