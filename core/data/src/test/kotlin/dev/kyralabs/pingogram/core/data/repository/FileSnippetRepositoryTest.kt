package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Snippets, kept on the device in a file of their own in the user's order, their names unique, gone at logout. */
class FileSnippetRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileSnippetRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileSnippetRepositoryTest.file) =
        FileSnippetRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    private suspend fun FileSnippetRepository.added(
        name: String,
        text: String = "Text of $name",
    ): Snippet = (add(name, text) as Outcome.Success).value

    @Test
    fun `snippets survive the app process, in order, their texts as written`() =
        runTest {
            val repository = repository()
            val address = repository.added("  address ", "12 Main St\n\tFlat 4\\B")
            val thanks = repository.added("thanks")

            assertEquals(
                listOf(Snippet(address.id, "address", "12 Main St\n\tFlat 4\\B"), thanks),
                repository().snippets.first(),
            )
        }

    @Test
    fun `a name taken, letter case aside, or not one word is refused`() =
        runTest {
            val repository = repository()
            repository.added("address")

            assertInstanceOf(Outcome.Failure::class.java, repository.add("Address", "Other"))
            assertInstanceOf(Outcome.Failure::class.java, repository.add("my address", "Other"))
            assertInstanceOf(Outcome.Failure::class.java, repository.add("", "Other"))
            assertInstanceOf(Outcome.Failure::class.java, repository.add("empty", "  "))
            assertEquals(listOf("address"), repository.snippets.first().map { it.name })
        }

    @Test
    fun `no more than the most snippets are kept`() =
        runTest {
            val repository = repository()
            repeat(Snippet.MAX_SNIPPETS) { repository.added("s$it") }

            assertInstanceOf(Outcome.Failure::class.java, repository.add("one_more", "Text"))
            assertEquals(Snippet.MAX_SNIPPETS, repository().snippets.first().size)
        }

    @Test
    fun `editing keeps a snippet's place, and cannot take another's name`() =
        runTest {
            val repository = repository()
            val address = repository.added("address")
            val thanks = repository.added("thanks")

            repository.update(address.copy(name = "home", text = "New text"))
            val clash = repository.update(thanks.copy(name = "HOME"))

            assertInstanceOf(Outcome.Failure::class.java, clash)
            assertEquals(listOf(Snippet(address.id, "home", "New text"), thanks), repository().snippets.first())
        }

    @Test
    fun `snippets reorder and delete`() =
        runTest {
            val repository = repository()
            val first = repository.added("first")
            val second = repository.added("second")
            val third = repository.added("third")

            repository.reorder(listOf(third.id, first.id))
            repository.remove(first.id)

            assertEquals(listOf(third, second), repository().snippets.first())
        }

    @Test
    fun `logging out deletes them`() =
        runTest {
            val repository = repository()
            repository.added("address")

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()

            assertFalse(file.exists())
            assertTrue(repository.snippets.first().isEmpty())
        }

    @Test
    fun `the demo keeps them in memory only`() =
        runTest {
            val repository = repository(file = null)
            repository.added("address")

            assertEquals(1, repository.snippets.first().size)
            assertFalse(file.exists())
        }
}
