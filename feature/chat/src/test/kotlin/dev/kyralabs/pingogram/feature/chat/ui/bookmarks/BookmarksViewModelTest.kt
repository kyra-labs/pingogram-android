package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.testing.FakeBookmarkRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/** The Bookmarks page (the owner's board 34b): newest first, filtered by a tag, opening, removing and undoing. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val bookmarks = FakeBookmarkRepository()
    private val chats = FakeChatRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        chats.setChats(chat(id = 1, title = "Ada Lovelace"), chat(id = 2, title = "Trips"))
        bookmarks.savedTags.value = listOf(BookmarkTag(10, "Ideas", 0), BookmarkTag(11, "Trips", 3))
        bookmarks.saved.value =
            listOf(
                bookmark(chatId = 1, messageId = 100, minutesAgo = 90, tags = listOf(10L), note = "Read later"),
                bookmark(chatId = 2, messageId = 200, minutesAgo = 5, tags = listOf(11L)),
                bookmark(chatId = 1, messageId = 101, minutesAgo = 30),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bookmark(
        chatId: Long,
        messageId: Long,
        minutesAgo: Long,
        tags: List<Long> = emptyList(),
        note: String = "",
    ) = Bookmark(
        chatId = chatId,
        messageId = messageId,
        savedAt = NOW.epochSecond - minutesAgo * 60,
        tagIds = tags.toImmutableList(),
        note = note,
        preview = BookmarkPreview("Ada", "Message $messageId"),
    )

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): BookmarksViewModel {
        val viewModel =
            BookmarksViewModel(
                handle,
                bookmarks,
                chats,
                BookmarkTimes(FakeWallClock(NOW), FakeTimePatterns()) { Locale.US },
                UnconfinedTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: BookmarksViewModel): List<BookmarksEffect> {
        val effects = mutableListOf<BookmarksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun BookmarksViewModel.content() = uiState.value as BookmarksUiState.Content

    @Test
    fun `the bookmarks show the newest first, with their chats, notes and tags`() =
        runTest {
            val content = createViewModel().content()

            assertEquals(listOf(200L, 101L, 100L), content.items.map { it.messageId })
            assertEquals("Ada Lovelace", content.items.last().chatTitle)
            assertEquals("Read later", content.items.last().note)
            assertEquals(
                listOf("Ideas"),
                content.items
                    .last()
                    .tags
                    .map { it.name },
            )
            assertEquals("Ada", content.items.first().sender)
        }

    @Test
    fun `a sender named as the chat is left out`() =
        runTest {
            chats.setChats(chat(id = 1, title = "Ada"), chat(id = 2, title = "Trips"))

            val content = createViewModel().content()

            assertEquals("", content.items.last().sender)
        }

    @Test
    fun `the filters count all bookmarks, then each tag's`() =
        runTest {
            val filters = createViewModel().content().filters

            assertEquals(listOf(null, 10L, 11L), filters.map { it.tagId })
            assertEquals(listOf(3, 1, 1), filters.map { it.count })
        }

    @Test
    fun `a tag filters the bookmarks and survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            createViewModel(handle).onEvent(BookmarksEvent.SelectTag(11))
            runCurrent()

            val restored = createViewModel(handle).content()

            assertEquals(11L, restored.selectedTagId)
            assertEquals(listOf(200L), restored.items.map { it.messageId })
        }

    @Test
    fun `a removed tag no longer filters`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(BookmarksEvent.SelectTag(11))
            runCurrent()

            bookmarks.savedTags.value = listOf(BookmarkTag(10, "Ideas", 0))
            runCurrent()

            assertEquals(null, viewModel.content().selectedTagId)
            assertEquals(3, viewModel.content().items.size)
        }

    @Test
    fun `nothing bookmarked shows the empty state`() =
        runTest {
            bookmarks.saved.value = emptyList()

            assertEquals(BookmarksUiState.Empty, createViewModel().uiState.value)
        }

    @Test
    fun `a chat TDLib has not announced is asked for once`() =
        runTest {
            chats.setChats(chat(id = 1, title = "Ada Lovelace"))

            val viewModel = createViewModel()
            bookmarks.saved.value = bookmarks.saved.value.toList()
            runCurrent()

            assertEquals(listOf(2L), chats.getChatRequests)
            assertEquals(
                "",
                viewModel
                    .content()
                    .items
                    .first()
                    .chatTitle,
            )
        }

    @Test
    fun `a card opens its message`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(BookmarksEvent.Open(chatId = 2, messageId = 200))
            runCurrent()

            assertEquals(listOf(BookmarksEffect.OpenMessage(2, 200)), effects)
        }

    @Test
    fun `removing offers undo, which brings the bookmark back as it was`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            val before = bookmarks.saved.value.first { it.messageId == 100L }

            viewModel.onEvent(BookmarksEvent.Remove(chatId = 1, messageId = 100))
            runCurrent()

            assertEquals(listOf(BookmarksEffect.Removed), effects)
            assertTrue(bookmarks.saved.value.none { it.messageId == 100L })

            viewModel.onEvent(BookmarksEvent.UndoRemove)
            runCurrent()

            assertEquals(before, bookmarks.saved.value.first { it.messageId == 100L })
        }

    @Test
    fun `a removal that cannot be saved says why`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            bookmarks.failure = DomainError.Network

            viewModel.onEvent(BookmarksEvent.Remove(chatId = 1, messageId = 100))
            runCurrent()

            assertEquals(listOf(BookmarksEffect.ShowError(DomainError.Network)), effects)
        }
}
