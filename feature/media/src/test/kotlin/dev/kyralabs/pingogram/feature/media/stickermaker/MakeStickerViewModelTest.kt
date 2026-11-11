package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.navigation.MakeStickerRoute
import dev.kyralabs.pingogram.feature.media.stickermaker.FakeStickerMakerRepository.Companion.STICKER_PATH
import dev.kyralabs.pingogram.feature.media.stickermaker.FakeStickerMakerRepository.Companion.set
import dev.kyralabs.pingogram.feature.media.ui.FakeFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Making a sticker: the photo, the layers, the outline, keeping them, writing the sticker and adding it to a set. */
@OptIn(ExperimentalCoroutinesApi::class)
class MakeStickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val files = FakeFileRepository()
    private val maker = FakeStickerMakerRepository()
    private val creation = FakeStickerSetCreationRepository()
    private val emoji = FakeEmojiCatalogRepository()
    private val handle = SavedStateHandle()

    init {
        Dispatchers.setMain(dispatcher)
        files.setFile(PHOTO, FileState.Downloaded("/tdlib/photos/1.jpg", 100))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        MakeStickerViewModel(MakeStickerRoute(PHOTO), handle, files, StickerSets(maker, creation), emoji)

    private fun TestScope.ready(): MakeStickerViewModel = viewModel().also { advanceUntilIdle() }

    @Test
    fun `downloads the photo at visible priority and shows it once on the device`() =
        runTest(dispatcher) {
            files.setFile(PHOTO, FileState.Downloading(50, 100, isActive = true))
            val viewModel = ready()

            assertEquals(listOf("download:$PHOTO:Visible"), files.calls)
            assertEquals(StickerPhoto.Loading(0.5f), viewModel.uiState.value.photo)
            assertFalse(viewModel.uiState.value.canFinish)

            files.setFile(PHOTO, FileState.Downloaded("/tdlib/photos/1.jpg", 100))
            advanceUntilIdle()
            assertEquals(StickerPhoto.Ready("/tdlib/photos/1.jpg"), viewModel.uiState.value.photo)
            assertTrue(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `a photo TDLib cannot download says so, and trying again downloads it again`() =
        runTest(dispatcher) {
            files.setFile(PHOTO, FileState.NotDownloaded(100, canBeDownloaded = false))
            val viewModel = ready()
            assertEquals(StickerPhoto.Failed, viewModel.uiState.value.photo)

            files.setFile(PHOTO, FileState.Downloaded("/tdlib/photos/1.jpg", 100))
            viewModel.onEvent(MakeStickerEvent.RetryPhoto)
            advanceUntilIdle()

            assertEquals(2, files.calls.size)
            assertTrue(viewModel.uiState.value.photo is StickerPhoto.Ready)
        }

    @Test
    fun `adds typed text without control characters, and nothing for blank text`() =
        runTest(dispatcher) {
            val viewModel = ready()

            viewModel.onEvent(MakeStickerEvent.AddText)
            assertTrue(viewModel.uiState.value.textDialog)
            viewModel.onEvent(MakeStickerEvent.TextEntered("  Mon\u001Fday  "))
            viewModel.onEvent(MakeStickerEvent.AddText)
            viewModel.onEvent(MakeStickerEvent.TextEntered("   "))

            val state = viewModel.uiState.value
            assertFalse(state.textDialog)
            assertEquals(listOf("Monday"), state.layers.map { it.content })
            assertEquals(LayerKind.Text, state.layers.single().kind)
            assertEquals(state.layers.single().id, state.selected)
        }

    @Test
    fun `adds an emoji from the sheet, reading the emoji once`() =
        runTest(dispatcher) {
            val viewModel = ready()

            viewModel.onEvent(MakeStickerEvent.AddEmoji)
            advanceUntilIdle()
            assertEquals(EmojiTarget.Layer, viewModel.uiState.value.emojiFor)
            assertEquals(2, viewModel.uiState.value.emojiGroups.size)
            viewModel.onEvent(MakeStickerEvent.EmojiChosen("😺"))
            viewModel.onEvent(MakeStickerEvent.AddEmoji)
            viewModel.onEvent(MakeStickerEvent.EmojiChosen(null))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.emojiFor)
            assertEquals(
                listOf("😺"),
                viewModel.uiState.value.layers
                    .map { it.content },
            )
            assertEquals(1, emoji.reads)
        }

    @Test
    fun `takes no more layers than the limit`() =
        runTest(dispatcher) {
            val viewModel = ready()
            repeat(StickerMakerLimits.LAYER_COUNT_MAX + 2) {
                viewModel.onEvent(MakeStickerEvent.AddText)
                viewModel.onEvent(MakeStickerEvent.TextEntered("Text $it"))
            }

            assertEquals(StickerMakerLimits.LAYER_COUNT_MAX, viewModel.uiState.value.layers.size)
            viewModel.onEvent(MakeStickerEvent.AddText)
            assertFalse(viewModel.uiState.value.textDialog)
        }

    @Test
    fun `moves and removes layers, within the square`() =
        runTest(dispatcher) {
            val viewModel = ready()
            viewModel.onEvent(MakeStickerEvent.AddText)
            viewModel.onEvent(MakeStickerEvent.TextEntered("Hi"))
            val id =
                viewModel.uiState.value.layers
                    .single()
                    .id

            viewModel.onEvent(MakeStickerEvent.Moved(id, 1.4f, 0.2f))
            assertEquals(
                1f to 0.2f,
                viewModel.uiState.value.layers
                    .single()
                    .let { it.x to it.y },
            )

            viewModel.onEvent(MakeStickerEvent.Remove(id))
            assertTrue(
                viewModel.uiState.value.layers
                    .isEmpty(),
            )
            assertNull(viewModel.uiState.value.selected)
        }

    @Test
    fun `keeps the crop, the outline and the layers across process death`() =
        runTest(dispatcher) {
            val first = ready()
            first.onEvent(MakeStickerEvent.Cropped(StickerCrop(zoom = 9f, panX = 0.1f, panY = -0.2f)))
            first.onEvent(MakeStickerEvent.ToggleOutline)
            first.onEvent(MakeStickerEvent.AddText)
            first.onEvent(MakeStickerEvent.TextEntered("Office cats"))
            first.onEvent(MakeStickerEvent.AddEmoji)
            first.onEvent(MakeStickerEvent.EmojiChosen("😺"))

            val restored = viewModel().uiState.value

            assertEquals(StickerCrop(StickerMakerLimits.ZOOM_MAX, 0.1f, -0.2f), restored.crop)
            assertFalse(restored.outline)
            assertEquals(listOf("Office cats", "😺"), restored.layers.map { it.content })
            assertEquals(listOf(LayerKind.Text, LayerKind.Emoji), restored.layers.map { it.kind })
        }

    @Test
    fun `Next captures the drawing without the touched layer's frame and opens the sheet on the user's sets`() =
        runTest(dispatcher) {
            maker.sets = Outcome.Success(listOf(set("officecats_kl", "Office cats"), set("moods", "Daily moods", 30)))
            val viewModel = ready()
            viewModel.onEvent(MakeStickerEvent.AddText)
            viewModel.onEvent(MakeStickerEvent.TextEntered("Hi"))

            viewModel.effects.test {
                viewModel.onEvent(MakeStickerEvent.Next)
                assertEquals(MakeStickerEffect.Render, awaitItem())
            }
            assertNull(viewModel.uiState.value.selected)
            assertTrue(viewModel.uiState.value.rendering)

            viewModel.onEvent(MakeStickerEvent.Rendered(byteArrayOf(1, 2, 3)))
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.sheet
            assertEquals(STICKER_PATH, sheet?.stickerPath)
            assertEquals(listOf("Office cats", "Daily moods"), (sheet?.sets as OwnedSets.Loaded).sets.map { it.title })
            assertEquals("officecats_kl", sheet.choice)
            assertFalse(viewModel.uiState.value.rendering)
            assertEquals(listOf(3), maker.written.map { it.size })
        }

    @Test
    fun `a drawing that cannot be captured says so and shows the editor again`() =
        runTest(dispatcher) {
            val viewModel = ready()

            viewModel.effects.test {
                viewModel.onEvent(MakeStickerEvent.Next)
                skipItems(1)
                viewModel.onEvent(MakeStickerEvent.Rendered(null))
                assertEquals(MakeStickerEffect.ShowError(DomainError.PhotoInvalid), awaitItem())
            }
            assertFalse(viewModel.uiState.value.rendering)
            assertNull(viewModel.uiState.value.sheet)
        }

    @Test
    fun `adds the sticker with its emoji to the chosen set, then shares its link and closes`() =
        runTest(dispatcher) {
            maker.sets = Outcome.Success(listOf(set("officecats_kl", "Office cats"), set("moods", "Daily moods", 30)))
            val viewModel = sheetOpen()

            viewModel.sheet(AddToSetEvent.Choose("moods"))
            viewModel.sheet(AddToSetEvent.Emoji("😴"))
            viewModel.sheet(AddToSetEvent.Confirm)
            advanceUntilIdle()

            assertEquals(listOf("moods" to StickerDraft(STICKER_PATH, "😴")), maker.added)
            val sheet = viewModel.uiState.value.sheet
            assertEquals(AddToSetStep.Done, sheet?.step)
            assertEquals("Daily moods", sheet?.added?.title)
            assertEquals(31, sheet?.added?.size)
            assertEquals(listOf(STICKER_PATH), maker.discarded)

            viewModel.effects.test {
                // "Next" asked for the drawing first.
                assertEquals(MakeStickerEffect.Render, awaitItem())
                viewModel.sheet(AddToSetEvent.Share)
                assertEquals(MakeStickerEffect.ShareLink("https://t.me/addstickers/moods"), awaitItem())
                viewModel.sheet(AddToSetEvent.Done)
                assertEquals(MakeStickerEffect.Close, awaitItem())
            }
        }

    @Test
    fun `Telegram's refusal stays in the sheet, which can try again`() =
        runTest(dispatcher) {
            maker.sets = Outcome.Success(listOf(set("officecats_kl", "Office cats", size = 119)))
            maker.addResult = Outcome.Failure(DomainError.InputRejected)
            val viewModel = sheetOpen()

            viewModel.sheet(AddToSetEvent.Confirm)
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.sheet
            assertEquals(AddToSetStep.Choose, sheet?.step)
            assertEquals(DomainError.InputRejected, sheet?.error)
            assertFalse(sheet?.busy == true)
            assertTrue(maker.discarded.isEmpty())
        }

    @Test
    fun `without sets of its own the user makes a new one, named after Telegram's suggestion`() =
        runTest(dispatcher) {
            val viewModel = sheetOpen()
            assertEquals(
                AddToSetState.NEW_SET,
                viewModel.uiState.value.sheet
                    ?.choice,
            )

            viewModel.sheet(AddToSetEvent.Confirm)
            assertEquals(
                AddToSetStep.NewSet,
                viewModel.uiState.value.sheet
                    ?.step,
            )
            viewModel.sheet(AddToSetEvent.Title("Office cats"))
            advanceUntilIdle()

            val draft =
                viewModel.uiState.value.sheet
                    ?.newSet
            assertEquals("office_cats_kl", draft?.name)
            assertEquals(StickerSetNameCheck.Available, draft?.check)

            viewModel.sheet(AddToSetEvent.Create)
            advanceUntilIdle()

            assertEquals(
                listOf(Triple("Office cats", "office_cats_kl", listOf(StickerDraft(STICKER_PATH, "😀")))),
                creation.created,
            )
            assertEquals(
                AddToSetStep.Done,
                viewModel.uiState.value.sheet
                    ?.step,
            )
            assertEquals(
                "office_cats_kl",
                viewModel.uiState.value.sheet
                    ?.added
                    ?.name,
            )
        }

    @Test
    fun `a link the user types is checked after a pause, the last one only, and a taken one cannot be created`() =
        runTest(dispatcher) {
            creation.occupied += "cats"
            val viewModel = sheetOpen()
            viewModel.sheet(AddToSetEvent.Confirm)
            viewModel.sheet(AddToSetEvent.Title("Cats"))
            viewModel.sheet(AddToSetEvent.Name("ca"))
            viewModel.sheet(AddToSetEvent.Name("cats"))
            advanceUntilIdle()

            assertEquals(listOf("cats"), creation.checked)
            assertEquals(
                StickerSetNameCheck.Occupied,
                viewModel.uiState.value.sheet
                    ?.newSet
                    ?.check,
            )
            viewModel.sheet(AddToSetEvent.Create)
            advanceUntilIdle()
            assertTrue(creation.created.isEmpty())
        }

    @Test
    fun `closing the sheet, or the screen, discards a sticker added nowhere`() =
        runTest(dispatcher) {
            val viewModel = sheetOpen()

            viewModel.sheet(AddToSetEvent.Dismiss)

            assertNull(viewModel.uiState.value.sheet)
            assertEquals(listOf(STICKER_PATH), maker.discarded)
            assertTrue(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `the sticker's own emoji comes from the emoji sheet`() =
        runTest(dispatcher) {
            val viewModel = sheetOpen()

            viewModel.sheet(AddToSetEvent.MoreEmoji)
            assertEquals(EmojiTarget.Sticker, viewModel.uiState.value.emojiFor)
            viewModel.onEvent(MakeStickerEvent.EmojiChosen("🦊"))

            assertEquals(
                "🦊",
                viewModel.uiState.value.sheet
                    ?.emoji,
            )
            assertTrue(
                viewModel.uiState.value.layers
                    .isEmpty(),
            )
        }

    private fun TestScope.sheetOpen(): MakeStickerViewModel {
        val viewModel = ready()
        viewModel.onEvent(MakeStickerEvent.Next)
        viewModel.onEvent(MakeStickerEvent.Rendered(byteArrayOf(1)))
        advanceUntilIdle()
        return viewModel
    }

    private fun MakeStickerViewModel.sheet(event: AddToSetEvent) = onEvent(MakeStickerEvent.Sheet(event))

    private companion object {
        const val PHOTO = 42
    }
}
