package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryInteractionInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.core.testing.FakeStoryRepository
import dev.kyralabs.pingogram.feature.media.storyalbum.FakeStoryAlbumRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeFileRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeVideoPlayer
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.cast

/** The ⋮ menu of the user's own story and its Delete (board 26), on the fakes of [StoryViewerViewModelHarness]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StoryViewerOwnStoryTest : StoryViewerViewModelHarness() {
    @Test
    fun `only a story the user may delete has the menu, which pauses it while it shows`() =
        runViewModelTest {
            val viewModel = viewModel()
            assertNull(viewModel.content.own)

            users.myUserId.value = ME
            stories.setActive(active(ME, order = 20, maxRead = 0, ids = listOf(30, 31)))
            stories.setStory(photoStory(30, ME, canBeDeleted = true))
            stories.setStory(photoStory(31, ME, canBeDeleted = true))
            val own = viewModel(StoryViewerRoute(ME))
            own.onEvent(StoryViewerEvent.Own.MenuOpen(true))
            runCurrent()

            assertEquals(OwnStoryUi(menuOpen = true), own.content.own)
            assertTrue(own.content.isPaused)
            own.onEvent(StoryViewerEvent.Own.MenuOpen(false))
            runCurrent()
            assertFalse(own.content.isPaused)
        }

    @Test
    fun `Delete asks first, then deletes the story, says so and moves on`() =
        runViewModelTest {
            users.myUserId.value = ME
            stories.setActive(active(ME, order = 20, maxRead = 0, ids = listOf(30, 31)))
            stories.setStory(photoStory(30, ME, canBeDeleted = true))
            stories.setStory(photoStory(31, ME, canBeDeleted = true))
            val own = viewModel(StoryViewerRoute(ME))

            own.onEvent(StoryViewerEvent.Own.MenuOpen(true))
            own.onEvent(StoryViewerEvent.Own.Delete)
            runCurrent()
            assertEquals(OwnStoryUi(deleteAsked = true), own.content.own)
            assertTrue(own.content.isPaused)
            assertFalse(stories.calls.any { it.startsWith("delete:") })

            own.effects.test {
                own.onEvent(StoryViewerEvent.Own.DeleteConfirmed)
                runCurrent()
                assertEquals(StoryViewerEffect.StoryDeleted, awaitItem())
            }
            assertEquals("delete:$ME:30", stories.calls.single { it.startsWith("delete:") })
            assertEquals(ME to 31, own.shownStory)
            assertEquals(OwnStoryUi(), own.content.own)
        }

    @Test
    fun `a dismissed question deletes nothing, and a refused deletion keeps the story`() =
        runViewModelTest {
            users.myUserId.value = ME
            stories.setActive(active(ME, order = 20, maxRead = 0, ids = listOf(30)))
            stories.setStory(photoStory(30, ME, canBeDeleted = true))
            stories.onDelete = { _, _ -> Outcome.Failure(DomainError.Network) }
            val own = viewModel(StoryViewerRoute(ME))

            own.onEvent(StoryViewerEvent.Own.Delete)
            own.onEvent(StoryViewerEvent.Own.DeleteDismissed)
            runCurrent()
            assertEquals(OwnStoryUi(), own.content.own)
            assertFalse(own.content.isPaused)

            own.effects.test {
                own.onEvent(StoryViewerEvent.Own.Delete)
                own.onEvent(StoryViewerEvent.Own.DeleteConfirmed)
                runCurrent()
                assertEquals(StoryViewerEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals(ME to 30, own.shownStory)
        }

    @Test
    fun `the user's own story shows how many saw and reacted, with the faces TDLib announced`() =
        runViewModelTest {
            users.myUserId.value = ME
            users.setUsers(MediaFixtures.user(7, "Aisha"))
            stories.setActive(active(ME, order = 20, maxRead = 0, ids = listOf(30)))
            stories.setStory(
                photoStory(30, ME, canBeDeleted = true)
                    .copy(interactions = StoryInteractionInfo(24, 6, persistentListOf(7L, 8L))),
            )
            val own = viewModel(StoryViewerRoute(ME))

            val views = own.content.views!!
            assertEquals(24, views.viewCount)
            assertEquals(6, views.reactionCount)
            assertEquals(listOf(7L), views.recent.map { it.userId })

            own.onEvent(StoryViewerEvent.ViewersOpen(true))
            runCurrent()
            assertTrue(own.content.views!!.isOpen)
            assertTrue(own.content.isPaused)
        }

    @Test
    fun `others' stories have no views pill`() =
        runViewModelTest {
            assertNull(viewModel().content.views)
        }
}
