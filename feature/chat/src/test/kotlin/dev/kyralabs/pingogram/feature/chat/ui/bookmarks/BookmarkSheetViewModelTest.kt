package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.testing.FakeBookmarkRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.BookmarkEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Bookmarking a message (the owner's board 34a): the sheet, its tags, saving with or without them, removing. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkSheetViewModelTest {
    private val bookmarks = FakeBookmarkRepository()
    private val messages = FakeMessageRepository()
    private val users = FakeUserRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messages.onMessage = { _, id -> Outcome.Success(message(id)) }
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): BookmarkSheetViewModel {
        val viewModel =
            BookmarkSheetViewModel(
                PRIVATE_CHAT,
                handle,
                bookmarks,
                messages,
                FakeChatRepository(),
                users,
                FakeWallClock(NOW),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: BookmarkSheetViewModel): List<BookmarkEffect> {
        val effects = mutableListOf<BookmarkEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `opening shows what the message says and the tags, none chosen`() =
        runTest {
            bookmarks.savedTags.value = listOf(BookmarkTag(1, "Trips", 0))
            val viewModel = createViewModel()

            viewModel.onEvent(BookmarkEvent.Open(42))
            runCurrent()

            val sheet = viewModel.sheet.value!!
            assertEquals(42L, sheet.messageId)
            assertEquals(UiText.Plain("Ada Lovelace"), sheet.sender)
            assertEquals(UiText.Plain("Message 42"), sheet.text)
            assertEquals(listOf(TagChoice(BookmarkTag(1, "Trips", 0), isChosen = false)), sheet.tags)
            assertEquals(false, sheet.isBookmarked)
        }

    @Test
    fun `saving without a tag keeps the bookmark with the message's words and the note`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(BookmarkEvent.Open(42))
            runCurrent()

            viewModel.onEvent(
                BookmarkEvent.Save(note = "  Book by Friday ", sender = "Ada Lovelace", text = "Message 42"),
            )
            runCurrent()

            assertEquals(
                listOf(
                    Bookmark(
                        chatId = PRIVATE_CHAT,
                        messageId = 42,
                        savedAt = NOW.epochSecond,
                        note = "Book by Friday",
                        preview = BookmarkPreview("Ada Lovelace", "Message 42"),
                    ),
                ),
                bookmarks.saved.value,
            )
            assertEquals(listOf<BookmarkEffect>(BookmarkEffect.Saved), effects)
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `a new tag is chosen at once, in the next color, and saved with the bookmark`() =
        runTest {
            bookmarks.savedTags.value = listOf(BookmarkTag(1, "Trips", 0))
            val viewModel = createViewModel()
            viewModel.onEvent(BookmarkEvent.Open(42))
            runCurrent()

            viewModel.onEvent(BookmarkEvent.ToggleTag(1))
            viewModel.onEvent(BookmarkEvent.NewTag("Work"))
            runCurrent()
            assertEquals(BookmarkTag(2, "Work", 1), bookmarks.savedTags.value.last())
            assertTrue(
                viewModel.sheet.value!!
                    .tags
                    .all { it.isChosen },
            )

            viewModel.onEvent(BookmarkEvent.Save("", "Ada", "Message 42"))
            runCurrent()
            assertEquals(
                persistentListOf(1L, 2L),
                bookmarks.saved.value
                    .single()
                    .tagIds,
            )
        }

    @Test
    fun `a bookmarked message opens with its tags and note, and can be removed`() =
        runTest {
            bookmarks.savedTags.value = listOf(BookmarkTag(1, "Trips", 0))
            bookmarks.saved.value =
                listOf(Bookmark(PRIVATE_CHAT, 42, savedAt = 5, persistentListOf(1L), note = "Hotel"))
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(BookmarkEvent.Open(42))
            runCurrent()
            assertTrue(viewModel.sheet.value!!.isBookmarked)
            assertEquals("Hotel", viewModel.sheet.value!!.note)
            assertTrue(
                viewModel.sheet.value!!
                    .tags
                    .single()
                    .isChosen,
            )

            viewModel.onEvent(BookmarkEvent.Remove)
            runCurrent()

            assertTrue(bookmarks.saved.value.isEmpty())
            assertEquals(listOf<BookmarkEffect>(BookmarkEffect.Removed), effects)
        }

    @Test
    fun `a failed save keeps the sheet and says why`() =
        runTest {
            bookmarks.failure = DomainError.Unknown(0, "disk")
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(BookmarkEvent.Open(42))
            runCurrent()

            viewModel.onEvent(BookmarkEvent.Save("", "Ada", "Message 42"))
            runCurrent()

            assertEquals(listOf<BookmarkEffect>(BookmarkEffect.ShowError(DomainError.Unknown(0, "disk"))), effects)
            assertEquals(42L, viewModel.sheet.value?.messageId)
        }

    @Test
    fun `the open sheet and its chosen tags survive process death, and dismissing closes it`() =
        runTest {
            bookmarks.savedTags.value = listOf(BookmarkTag(1, "Trips", 0))
            val handle = SavedStateHandle()
            val first = createViewModel(handle)
            first.onEvent(BookmarkEvent.Open(42))
            runCurrent()
            first.onEvent(BookmarkEvent.ToggleTag(1))

            val restored = createViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            runCurrent()
            assertEquals(UiText.Plain("Message 42"), restored.sheet.value?.text)
            assertTrue(
                restored.sheet.value!!
                    .tags
                    .single()
                    .isChosen,
            )

            restored.onEvent(BookmarkEvent.Dismiss)
            runCurrent()
            assertNull(restored.sheet.value)
        }
}
