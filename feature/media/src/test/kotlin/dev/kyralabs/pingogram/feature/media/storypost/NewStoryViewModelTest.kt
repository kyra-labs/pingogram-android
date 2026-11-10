package dev.kyralabs.pingogram.feature.media.storypost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.feature.media.storypost.FakeAttachmentRepository.Companion.CAMERA_FILE
import dev.kyralabs.pingogram.feature.media.storypost.FakeAttachmentRepository.Companion.photo
import dev.kyralabs.pingogram.feature.media.storypost.FakeAttachmentRepository.Companion.video
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Picking a story's photos and videos (`docs/adr/0036-posting-stories.md`): the picker opening once, items added in
 * order and refused when they are no photo or video, the camera, selecting, removing, reordering, the limit of ten,
 * process death, and the copies deleted when the screen closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewStoryViewModelTest {
    private val repository = FakeAttachmentRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = NewStoryViewModel(savedState, repository)

    @Test
    fun `opens the photo picker once, as the screen first shows`() =
        runTest {
            val savedState = SavedStateHandle()
            viewModel(savedState).effects.test {
                assertEquals(NewStoryEffect.OpenPicker(MAX_STORY_ITEMS), awaitItem())
            }
            viewModel(savedState).effects.test { expectNoEvents() }
        }

    @Test
    fun `adds picked items in their order, as media, and shows the last`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg", "b.jpg")))

            val state = viewModel.uiState.value
            assertEquals(listOf("a.jpg", "b.jpg"), state.items.map { it.key })
            assertEquals("b.jpg", state.shown?.key)
            assertEquals(listOf("a.jpg", "b.jpg").map { it to AttachmentKind.Media }, repository.prepared)
            assertTrue(state.canGoNext)
        }

    @Test
    fun `counts items still being prepared and waits for them before going on`() =
        runTest {
            val ready = CompletableDeferred<Outcome<Attachment>>()
            repository.onPrepare = { ready.await() }
            val viewModel = viewModel()
            viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))

            assertEquals(1, viewModel.uiState.value.preparing)
            assertFalse(viewModel.uiState.value.canGoNext)

            ready.complete(Outcome.Success(photo("a.jpg")))
            assertEquals(0, viewModel.uiState.value.preparing)
            assertTrue(viewModel.uiState.value.canGoNext)
        }

    @Test
    fun `a file that is no photo or video is deleted and said`() =
        runTest {
            val document = Attachment.Document("a.pdf", size = 10, fileName = "a.pdf", mimeType = "application/pdf")
            repository.onPrepare = { Outcome.Success(document) }
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Picked(listOf("a.pdf")))
                assertEquals(NewStoryEffect.NotPostable, awaitItem())
            }
            assertEquals(listOf(document), repository.discarded)
            assertTrue(
                viewModel.uiState.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun `a file that cannot be read is said`() =
        runTest {
            repository.onPrepare = { Outcome.Failure(DomainError.FileUnavailable) }
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))
                assertEquals(NewStoryEffect.Failed(DomainError.FileUnavailable), awaitItem())
            }
        }

    @Test
    fun `cancelling the picker with nothing picked closes the screen`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Picked(emptyList()))
                assertEquals(NewStoryEffect.Close, awaitItem())
            }
        }

    @Test
    fun `cancelling Add more keeps what was picked`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Picked(emptyList()))
                expectNoEvents()
            }
            assertEquals(1, viewModel.uiState.value.items.size)
        }

    @Test
    fun `Add more opens the picker for the places left, and takes no more than ten`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.onEvent(NewStoryEvent.Picked((1..8).map { "$it.jpg" }))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.AddMore)
                assertEquals(NewStoryEffect.OpenPicker(2), awaitItem())
            }
            viewModel.onEvent(NewStoryEvent.Picked((9..12).map { "$it.jpg" }))

            assertEquals(MAX_STORY_ITEMS, viewModel.uiState.value.items.size)
            assertEquals(0, viewModel.uiState.value.remaining)
        }

    @Test
    fun `the camera's photo joins the story, and a cancelled one's file is deleted`() =
        runTest {
            val savedState = SavedStateHandle(mapOf("newStory.started" to true))
            val viewModel = viewModel(savedState)
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.TakePhoto)
                assertEquals(NewStoryEffect.LaunchCamera(CAMERA_FILE), awaitItem())
            }
            viewModel.onEvent(NewStoryEvent.PhotoTaken(taken = true))
            assertEquals(
                listOf("camera.jpg"),
                viewModel.uiState.value.items
                    .map { it.key },
            )

            viewModel.onEvent(NewStoryEvent.TakePhoto)
            viewModel.onEvent(NewStoryEvent.PhotoTaken(taken = false))
            assertEquals(listOf(CAMERA_FILE), repository.discardedCameraFiles)
            assertEquals(1, viewModel.uiState.value.items.size)
        }

    @Test
    fun `selects, moves and removes items`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg", "b.jpg", "c.jpg")))

            viewModel.onEvent(NewStoryEvent.Select("a.jpg"))
            assertEquals(
                "a.jpg",
                viewModel.uiState.value.shown
                    ?.key,
            )

            viewModel.onEvent(NewStoryEvent.Move("a.jpg", 2))
            assertEquals(
                listOf("b.jpg", "c.jpg", "a.jpg"),
                viewModel.uiState.value.items
                    .map { it.key },
            )

            viewModel.onEvent(NewStoryEvent.Remove("a.jpg"))
            assertEquals(
                listOf("b.jpg", "c.jpg"),
                viewModel.uiState.value.items
                    .map { it.key },
            )
            assertEquals(
                "c.jpg",
                viewModel.uiState.value.shown
                    ?.key,
            )
            assertEquals(listOf(photo("a.jpg")), repository.discarded)
        }

    @Test
    fun `keeps the items, their order and the shown one across process death`() =
        runTest {
            repository.onPrepare = { uri -> Outcome.Success(if (uri.endsWith(".mp4")) video(uri) else photo(uri)) }
            val savedState = SavedStateHandle()
            val before = viewModel(savedState)
            before.onEvent(NewStoryEvent.Picked(listOf("a.jpg", "b.mp4")))
            before.onEvent(NewStoryEvent.Move("b.mp4", 0))
            before.onEvent(NewStoryEvent.Select("a.jpg"))

            val after = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(before.uiState.value, after.uiState.value)
            assertEquals(listOf(video("b.mp4"), photo("a.jpg")), after.picked)
        }

    @Test
    fun `closing deletes the copies and leaves`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Close)
                assertEquals(NewStoryEffect.Close, awaitItem())
            }
            assertEquals(listOf(photo("a.jpg")), repository.discarded)
        }

    @Test
    fun `leaving the screen deletes the copies unless they were handed over`() =
        runTest {
            val left = viewModel()
            left.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))
            clear(left)
            assertEquals(listOf(photo("a.jpg")), repository.discarded)

            val handedOver = viewModel()
            handedOver.onEvent(NewStoryEvent.Picked(listOf("b.jpg")))
            handedOver.handOver()
            clear(handedOver)
            assertEquals(listOf(photo("a.jpg")), repository.discarded)
        }

    @Test
    fun `Next goes on only once every item is ready`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf("newStory.started" to true)))
            viewModel.effects.test {
                viewModel.onEvent(NewStoryEvent.Next)
                expectNoEvents()
                viewModel.onEvent(NewStoryEvent.Picked(listOf("a.jpg")))
                viewModel.onEvent(NewStoryEvent.Next)
                assertEquals(NewStoryEffect.Next, awaitItem())
            }
            assertNull(repository.discarded.firstOrNull())
        }

    /** Clears [viewModel] as leaving the screen does: through a [ViewModelStore]. */
    private fun clear(viewModel: NewStoryViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[NewStoryViewModel::class]
        store.clear()
    }
}
