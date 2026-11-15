package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.testing.FakeBookmarkRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Editing the bookmarks' tags (the owner's board 34c): adding, renaming, reordering and deleting after a question. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkTagsViewModelTest {
    private val bookmarks = FakeBookmarkRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        bookmarks.savedTags.value =
            listOf(BookmarkTag(1, "Trips", 3), BookmarkTag(2, "Work", 5), BookmarkTag(3, "Ideas", 7))
        bookmarks.saved.value =
            listOf(
                bookmark(100, listOf(1L)),
                bookmark(101, listOf(1L, 2L)),
                bookmark(102, listOf(2L)),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bookmark(
        messageId: Long,
        tags: List<Long>,
    ) = Bookmark(chatId = 1, messageId = messageId, savedAt = messageId, tagIds = tags.toImmutableList())

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): BookmarkTagsViewModel {
        val viewModel = BookmarkTagsViewModel(handle, bookmarks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: BookmarkTagsViewModel): List<BookmarkTagsEffect> {
        val effects = mutableListOf<BookmarkTagsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun BookmarkTagsViewModel.content() = uiState.value as BookmarkTagsUiState.Content

    @Test
    fun `the tags show in their order with how many bookmarks have them, and how many have only them`() =
        runTest {
            val tags = createViewModel().content().tags

            assertEquals(listOf("Trips", "Work", "Ideas"), tags.map { it.name })
            assertEquals(listOf(2, 2, 0), tags.map { it.count })
            assertEquals(listOf(1, 1, 0), tags.map { it.alone })
        }

    @Test
    fun `a new tag takes the next color and is saved trimmed`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkTagsEvent.StartNew)
            assertEquals(TagDraft(id = null, name = "", color = 3), viewModel.content().draft)
            viewModel.onEvent(BookmarkTagsEvent.EditName("  Recipes "))
            viewModel.onEvent(BookmarkTagsEvent.SaveDraft)
            runCurrent()

            assertNull(viewModel.content().draft)
            assertEquals(BookmarkTag(4, "Recipes", 3), bookmarks.savedTags.value.last())
        }

    @Test
    fun `a blank name is not saved`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkTagsEvent.StartNew)
            viewModel.onEvent(BookmarkTagsEvent.EditName("   "))
            viewModel.onEvent(BookmarkTagsEvent.SaveDraft)

            assertEquals(3, bookmarks.savedTags.value.size)
            assertEquals(false, viewModel.content().draft?.canSave)
        }

    @Test
    fun `renaming keeps the tag's place and takes its new color`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkTagsEvent.StartEdit(2))
            assertEquals(TagDraft(id = 2, name = "Work", color = 5), viewModel.content().draft)
            viewModel.onEvent(BookmarkTagsEvent.EditName("Office"))
            viewModel.onEvent(BookmarkTagsEvent.EditColor(1))
            viewModel.onEvent(BookmarkTagsEvent.SaveDraft)
            runCurrent()

            assertEquals(BookmarkTag(2, "Office", 1), bookmarks.savedTags.value[1])
        }

    @Test
    fun `the draft survives process death, and cancelling drops it`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = createViewModel(handle)
            viewModel.onEvent(BookmarkTagsEvent.StartEdit(1))
            viewModel.onEvent(BookmarkTagsEvent.EditName("Travel"))

            val restored = createViewModel(handle)
            assertEquals(TagDraft(id = 1, name = "Travel", color = 3), restored.content().draft)

            restored.onEvent(BookmarkTagsEvent.CancelDraft)
            assertNull(restored.content().draft)
            assertEquals(
                "Trips",
                bookmarks.savedTags.value
                    .first()
                    .name,
            )
        }

    @Test
    fun `a drag and screen readers' moves reorder the tags`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkTagsEvent.Reorder(listOf(3, 1, 2)))
            runCurrent()
            assertEquals(listOf(3L, 1L, 2L), bookmarks.savedTags.value.map { it.id })

            viewModel.onEvent(BookmarkTagsEvent.Move(2, up = true))
            runCurrent()
            assertEquals(listOf(3L, 2L, 1L), bookmarks.savedTags.value.map { it.id })

            viewModel.onEvent(BookmarkTagsEvent.Move(3, up = true))
            runCurrent()
            assertEquals(listOf(3L, 2L, 1L), bookmarks.savedTags.value.map { it.id })
        }

    @Test
    fun `deleting asks first, then takes the tag off its bookmarks and keeps them`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(BookmarkTagsEvent.AskDelete(1))
            assertEquals(1L, viewModel.content().deleting?.id)
            assertEquals(3, bookmarks.savedTags.value.size)

            viewModel.onEvent(BookmarkTagsEvent.ConfirmDelete)
            runCurrent()

            assertNull(viewModel.content().deleting)
            assertEquals(listOf("Work", "Ideas"), bookmarks.savedTags.value.map { it.name })
            assertEquals(3, bookmarks.saved.value.size)
            assertEquals(listOf(emptyList(), listOf(2L), listOf(2L)), bookmarks.saved.value.map { it.tagIds })
            assertEquals(listOf<BookmarkTagsEffect>(BookmarkTagsEffect.Deleted("Trips")), effects)
        }

    @Test
    fun `cancelling the question deletes nothing`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkTagsEvent.AskDelete(1))
            viewModel.onEvent(BookmarkTagsEvent.CancelDelete)

            assertNull(viewModel.content().deleting)
            assertEquals(3, bookmarks.savedTags.value.size)
        }

    @Test
    fun `a change that cannot be saved says why`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            bookmarks.failure = DomainError.Network

            viewModel.onEvent(BookmarkTagsEvent.Reorder(listOf(3, 2, 1)))
            runCurrent()

            assertEquals(listOf<BookmarkTagsEffect>(BookmarkTagsEffect.ShowError(DomainError.Network)), effects)
        }
}
