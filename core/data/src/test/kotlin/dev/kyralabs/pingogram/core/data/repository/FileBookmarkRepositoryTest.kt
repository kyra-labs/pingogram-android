package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Bookmarks and their tags, kept on the device in a file of their own, the newest first, gone at logout. */
class FileBookmarkRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileBookmarkRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileBookmarkRepositoryTest.file) =
        FileBookmarkRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    private fun bookmark(
        messageId: Long,
        savedAt: Long = messageId,
        tagIds: List<Long> = emptyList(),
    ) = Bookmark(
        chatId = 7,
        messageId = messageId,
        savedAt = savedAt,
        tagIds = tagIds.toImmutableList(),
        note = "Book it\tby Friday\nplease",
        preview = BookmarkPreview(sender = "Meera", text = "Check the price"),
    )

    @Test
    fun `bookmarks and tags survive the app process, the newest bookmark first`() =
        runTest {
            val repository = repository()
            val tag = (repository.addTag("  Trips ", color = 3) as Outcome.Success).value
            repository.save(bookmark(1, savedAt = 100, tagIds = listOf(tag.id)))
            repository.save(bookmark(2, savedAt = 200))

            val again = repository()
            assertEquals(listOf(BookmarkTag(tag.id, "Trips", 3)), again.tags.first())
            assertEquals(listOf(2L, 1L), again.bookmarks.first().map { it.messageId })
            assertEquals(bookmark(1, savedAt = 100, tagIds = listOf(tag.id)), again.bookmark(7, 1).first())
        }

    @Test
    fun `saving a message again replaces its bookmark, and unknown tags are dropped`() =
        runTest {
            val repository = repository()
            repository.save(bookmark(1))
            repository.save(bookmark(1, savedAt = 5, tagIds = listOf(99)))

            assertEquals(listOf(bookmark(1, savedAt = 5)), repository.bookmarks.first())
        }

    @Test
    fun `removing a bookmark removes it`() =
        runTest {
            val repository = repository()
            repository.save(bookmark(1))

            repository.remove(7, 1)

            assertNull(repository().bookmark(7, 1).first())
        }

    @Test
    fun `deleting a tag keeps its bookmarks without it`() =
        runTest {
            val repository = repository()
            val trips = (repository.addTag("Trips", 0) as Outcome.Success).value
            val work = (repository.addTag("Work", 1) as Outcome.Success).value
            repository.save(bookmark(1, tagIds = listOf(trips.id, work.id)))

            repository.removeTag(trips.id)

            assertEquals(listOf(work), repository.tags.first())
            assertEquals(
                listOf(work.id),
                repository.bookmarks
                    .first()
                    .single()
                    .tagIds,
            )
        }

    @Test
    fun `tags rename, recolor and reorder`() =
        runTest {
            val repository = repository()
            val trips = (repository.addTag("Trips", 0) as Outcome.Success).value
            val work = (repository.addTag("Work", 1) as Outcome.Success).value

            repository.updateTag(trips.copy(name = "Travel", color = 5))
            repository.reorderTags(listOf(work.id))

            assertEquals(listOf(work, BookmarkTag(trips.id, "Travel", 5)), repository().tags.first())
        }

    @Test
    fun `logging out deletes them`() =
        runTest {
            val repository = repository()
            repository.addTag("Trips", 0)
            repository.save(bookmark(1))

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()

            assertFalse(file.exists())
            assertTrue(repository.bookmarks.first().isEmpty())
            assertTrue(repository.tags.first().isEmpty())
        }

    @Test
    fun `the demo keeps them in memory only`() =
        runTest {
            val repository = repository(file = null)
            repository.save(bookmark(1))

            assertEquals(1, repository.bookmarks.first().size)
            assertFalse(file.exists())
        }
}
