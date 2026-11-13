package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * [NewStickerSetViewModel]: the suggested and checked link name, adding stickers with their emoji, moving and
 * removing them, publishing, refusals and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewStickerSetViewModelTest {
    private val repository = FakeCreation()
    private val emoji =
        object : EmojiCatalogRepository {
            override suspend fun catalog() =
                listOf(EmojiGroup(EmojiCategory.SmileysAndEmotion, persistentListOf("😺", "😴")))
        }
    private val created = mutableListOf<NewStickerSetViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        NewStickerSetViewModel(handle, repository, emoji).also { created += it }

    private val NewStickerSetViewModel.state get() = uiState.value

    @Test
    fun `the link follows Telegram's suggestion for the title, and is checked`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(NewStickerSetEvent.TitleChanged("Office cats"))
            advanceUntilIdle()

            assertEquals("officecats", viewModel.state.name)
            assertEquals(NameCheck.Available, viewModel.state.nameCheck)
            assertTrue(viewModel.state.canGoOn)
        }

    @Test
    fun `a typed link stops following the title, and a taken or malformed one cannot go on`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(NewStickerSetEvent.TitleChanged("Office cats"))
            viewModel.onEvent(NewStickerSetEvent.NameChanged("taken_set"))
            advanceUntilIdle()
            assertEquals("taken_set", viewModel.state.name)
            assertEquals(NameCheck.Occupied, viewModel.state.nameCheck)

            viewModel.onEvent(NewStickerSetEvent.NameChanged("1cats"))
            assertEquals(NameCheck.Invalid, viewModel.state.nameCheck)
            assertFalse(viewModel.state.canGoOn)
            viewModel.onEvent(NewStickerSetEvent.TitleChanged("Dogs"))
            advanceUntilIdle()
            assertEquals("1cats", viewModel.state.name)
        }

    @Test
    fun `a picked photo becomes a sticker whose emoji the sheet chooses`() =
        runTest {
            val viewModel = atStickers()
            viewModel.onEvent(NewStickerSetEvent.PhotoPicked("content://cat"))
            advanceUntilIdle()

            assertEquals(listOf(StickerTile("/up/content://cat.webp", "🙂")), viewModel.state.stickers)
            assertEquals(0, viewModel.state.emojiFor)
            assertEquals(
                2,
                viewModel.emoji.value
                    .single()
                    .emoji.size,
            )
            viewModel.onEvent(NewStickerSetEvent.EmojiChosen("😺"))
            assertEquals(
                "😺",
                viewModel.state.stickers
                    .single()
                    .emoji,
            )
            assertNull(viewModel.state.emojiFor)
        }

    @Test
    fun `stickers move and a removed one's copy is discarded`() =
        runTest {
            val viewModel = atStickers()
            listOf("content://a", "content://b").forEach { viewModel.onEvent(NewStickerSetEvent.PhotoPicked(it)) }
            advanceUntilIdle()
            viewModel.onEvent(NewStickerSetEvent.Move(1, -1))
            val paths = viewModel.state.stickers.map { it.path }
            assertEquals(listOf("/up/content://b.webp", "/up/content://a.webp"), paths)

            viewModel.onEvent(NewStickerSetEvent.Remove(0))
            assertEquals(listOf("/up/content://b.webp"), repository.discarded)
            assertEquals(1, viewModel.state.stickers.size)
        }

    @Test
    fun `publishing sends the stickers in order, then shows the set`() =
        runTest {
            val viewModel = atStickers()
            viewModel.onEvent(NewStickerSetEvent.PhotoPicked("content://a"))
            advanceUntilIdle()
            viewModel.onEvent(NewStickerSetEvent.EmojiChosen("😺"))
            val effects = mutableListOf<NewStickerSetEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(NewStickerSetEvent.Publish)
            advanceUntilIdle()

            val sticker = StickerDraft("/up/content://a.webp", "😺")
            assertEquals(Triple("Office cats", "officecats", listOf(sticker)), repository.made)
            assertEquals(NewStickerSetStep.Published, viewModel.state.step)
            assertEquals(42L, viewModel.state.published)
            assertTrue(NewStickerSetEffect.Published in effects)
        }

    @Test
    fun `a refusal keeps the stickers to publish again`() =
        runTest {
            repository.refusal = DomainError.FloodWait(30)
            val viewModel = atStickers()
            viewModel.onEvent(NewStickerSetEvent.PhotoPicked("content://a"))
            advanceUntilIdle()
            viewModel.onEvent(NewStickerSetEvent.Publish)
            advanceUntilIdle()

            assertEquals(NewStickerSetStep.Stickers, viewModel.state.step)
            assertEquals(DomainError.FloodWait(30), viewModel.state.error)
            assertTrue(viewModel.state.canPublish)
        }

    @Test
    fun `the step, the texts and the stickers survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            first.onEvent(NewStickerSetEvent.TitleChanged("Office cats"))
            advanceUntilIdle()
            first.onEvent(NewStickerSetEvent.Next)
            first.onEvent(NewStickerSetEvent.PhotoPicked("content://a"))
            advanceUntilIdle()

            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            with(restored.state) {
                assertEquals(NewStickerSetStep.Stickers, step)
                assertEquals("Office cats", title)
                assertEquals("officecats", name)
                assertEquals(listOf("/up/content://a.webp"), stickers.map { it.path })
            }
        }

    @Test
    fun `back goes from the stickers to the name, then out of the page`() =
        runTest {
            val viewModel = atStickers()
            val effects = mutableListOf<NewStickerSetEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            viewModel.onEvent(NewStickerSetEvent.Back)
            assertEquals(NewStickerSetStep.Name, viewModel.state.step)
            viewModel.onEvent(NewStickerSetEvent.Back)
            assertEquals(listOf<NewStickerSetEffect>(NewStickerSetEffect.Close), effects)
        }

    /** A view model past the name: "Office cats", its suggested link free. */
    private fun TestScope.atStickers(): NewStickerSetViewModel {
        val viewModel = viewModel()
        viewModel.onEvent(NewStickerSetEvent.TitleChanged("Office cats"))
        advanceUntilIdle()
        viewModel.onEvent(NewStickerSetEvent.Next)
        return viewModel
    }

    private class FakeCreation : StickerSetCreationRepository {
        val discarded = mutableListOf<String>()
        var made: Triple<String, String, List<StickerDraft>>? = null
        var refusal: DomainError? = null

        override suspend fun suggestName(title: String) = Outcome.Success(title.lowercase().replace(" ", ""))

        override suspend fun checkName(name: String) =
            Outcome.Success(if (name == "taken_set") StickerSetNameCheck.Occupied else StickerSetNameCheck.Available)

        override suspend fun prepareSticker(uri: String) = Outcome.Success("/up/$uri.webp")

        override fun discardSticker(path: String) {
            discarded += path
        }

        override suspend fun create(
            title: String,
            name: String,
            stickers: List<StickerDraft>,
        ): Outcome<Long> {
            refusal?.let { return Outcome.Failure(it) }
            made = Triple(title, name, stickers)
            return Outcome.Success(42)
        }
    }
}
