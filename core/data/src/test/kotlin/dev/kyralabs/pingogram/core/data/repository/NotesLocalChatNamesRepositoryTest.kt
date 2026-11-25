package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.domain.repository.NotesLocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.shownName
import dev.kyralabs.pingogram.core.testing.FakeChatNoteRepository
import dev.kyralabs.pingogram.core.testing.FakeNotesAndNamesRepository
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The names that show: the set ones while the setting is on, none while off; a contact's chat shows Telegram's. */
class NotesLocalChatNamesRepositoryTest {
    private val notes =
        FakeChatNoteRepository(
            persistentMapOf(
                GROUP to ChatNote(GROUP, name = "Book club", note = "Tuesdays"),
                PERSON to ChatNote(PERSON, name = "Dev from the meetup"),
                CHANNEL to ChatNote(CHANNEL, note = "Only a note"),
            ),
        )
    private val setting = FakeNotesAndNamesRepository()

    @Test
    fun `the names that are set show while the setting is on, and none while off`() =
        runTest {
            NotesLocalChatNamesRepository(notes, setting).names.test {
                assertEquals(mapOf(GROUP to "Book club", PERSON to "Dev from the meetup"), awaitItem())

                setting.enabled.value = false
                assertEquals(emptyMap<Long, String>(), awaitItem())

                setting.enabled.value = true
                awaitItem()
                notes.set(ChatNote(GROUP, name = "Readers"))
                assertEquals(mapOf(GROUP to "Readers", PERSON to "Dev from the meetup"), awaitItem())
            }
        }

    @Test
    fun `a contact's chat shows Telegram's name, its own kept for when it is no longer one`() {
        val names = mapOf(PERSON to "Dev from the meetup")

        assertEquals("Dev from the meetup", names.shownName(PERSON, isContact = false))
        assertNull(names.shownName(PERSON, isContact = true))
        assertNull(names.shownName(GROUP, isContact = false))
    }

    private companion object {
        const val GROUP = -100L
        const val CHANNEL = -200L
        const val PERSON = 7L
    }
}
