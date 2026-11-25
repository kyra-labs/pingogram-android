package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Notes and names on chats kept on the device by chat, one each, in a file of their own, gone at logout only. */
class FileChatNoteRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileChatNoteRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileChatNoteRepositoryTest.file) =
        FileChatNoteRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    @Test
    fun `a note keeps its lines and tabs and survives the app process`() =
        runTest {
            val note = ChatNote(-100, name = "Book club", note = "Meets on Tuesdays\n\tbring the book")

            assertEquals(Outcome.Success(Unit), repository().set(note))

            assertEquals(mapOf(-100L to note), repository().notes.first())
            assertEquals("-100\tBook club\tMeets on Tuesdays\\n\\tbring the book\n", file.readText())
        }

    @Test
    fun `setting again replaces the chat's note, and an empty one removes it`() =
        runTest {
            val repository = repository()
            repository.set(ChatNote(7, name = "Mum"))
            repository.set(ChatNote(8, note = "Landlord"))
            repository.set(ChatNote(7, name = "Mom", note = "Call on Sundays"))
            assertEquals(
                mapOf(7L to ChatNote(7, "Mom", "Call on Sundays"), 8L to ChatNote(8, note = "Landlord")),
                repository.notes.first(),
            )

            repository.set(ChatNote(7, name = " ", note = ""))

            assertEquals(mapOf(8L to ChatNote(8, note = "Landlord")), repository().notes.first())
        }

    @Test
    fun `a name is trimmed and both are cut to their limits`() =
        runTest {
            val repository = repository()

            repository.set(ChatNote(7, name = "  ${"n".repeat(70)}  ", note = "x".repeat(2_100)))

            val kept = repository.notes.first().getValue(7)
            assertEquals("n".repeat(ChatNote.MAX_NAME_LENGTH), kept.name)
            assertEquals(ChatNote.MAX_NOTE_LENGTH, kept.note.length)
        }

    @Test
    fun `a new chat beyond the limit is refused, an existing one still changes`() =
        runTest {
            val repository = repository(file = null)
            repeat(FileChatNoteRepository.MAX_NOTES) { repository.set(ChatNote(it.toLong(), name = "chat $it")) }

            assertTrue(repository.set(ChatNote(-1, name = "one too many")) is Outcome.Failure)
            assertEquals(Outcome.Success(Unit), repository.set(ChatNote(0, name = "renamed")))
            assertEquals(
                "renamed",
                repository.notes
                    .first()
                    .getValue(0)
                    .name,
            )
        }

    @Test
    fun `lines that are not a note are skipped`() =
        runTest {
            file.writeText("7\tMom\t\nnot a line\nx\tname\tnote\n8\t\tLandlord\n9\t\t\n")

            assertEquals(
                mapOf(7L to ChatNote(7, name = "Mom"), 8L to ChatNote(8, note = "Landlord")),
                repository().notes.first(),
            )
        }

    @Test
    fun `logging out deletes them, closing keeps them`() =
        runTest {
            val repository = repository()
            repository.set(ChatNote(7, name = "Mom"))

            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())
            testScheduler.runCurrent()
            assertTrue(file.exists())

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()
            assertFalse(file.exists())
            assertEquals(emptyMap<Long, ChatNote>(), repository.notes.first())
        }

    @Test
    fun `the demo keeps them in memory`() =
        runTest {
            val repository = repository(file = null)
            repository.set(ChatNote(7, name = "Mom"))

            assertEquals(mapOf(7L to ChatNote(7, name = "Mom")), repository.notes.first())
            assertFalse(file.exists())
        }
}
