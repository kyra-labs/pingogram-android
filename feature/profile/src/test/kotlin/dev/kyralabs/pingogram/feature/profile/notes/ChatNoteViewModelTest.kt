package dev.kyralabs.pingogram.feature.profile.notes

import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.testing.FakeChatNoteRepository
import dev.kyralabs.pingogram.core.testing.FakeNotesAndNamesRepository
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A chat's name and note on this phone: shown with the setting, edited, saved, cleared, and a failed save. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatNoteViewModelTest {
    private val notes = FakeChatNoteRepository(persistentMapOf(GROUP to ChatNote(GROUP, "Book club", "Tuesdays")))
    private val setting = FakeNotesAndNamesRepository()
    private val created = mutableListOf<ChatNoteViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    /** A view model opened on [chatId], its state collected so it stays live. */
    private fun TestScope.viewModel(chatId: Long = GROUP): ChatNoteViewModel =
        ChatNoteViewModel(notes, setting).also { viewModel ->
            created += viewModel
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.open(chatId)
            runCurrent()
        }

    private fun TestScope.send(
        viewModel: ChatNoteViewModel,
        event: ChatNoteEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    @Test
    fun `the chat's name and note show while the setting is on`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ChatNoteUiState(shown = true, name = "Book club", note = "Tuesdays"), viewModel.state.value)

            setting.enabled.value = false
            runCurrent()

            assertFalse(viewModel.state.value.shown)
        }

    @Test
    fun `a chat without one shows empty`() =
        runTest {
            assertEquals(ChatNoteUiState(shown = true), viewModel(chatId = OTHER).state.value)
        }

    @Test
    fun `editing starts from what is kept, and saving keeps the change`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatNoteEvent.Edit)
            assertEquals(ChatNoteEditor("Book club", "Tuesdays", "Book club", "Tuesdays"), viewModel.state.value.editor)
            assertFalse(
                viewModel.state.value.editor!!
                    .canSave,
            )

            send(viewModel, ChatNoteEvent.NameChanged("Readers"))
            assertTrue(
                viewModel.state.value.editor!!
                    .canSave,
            )
            send(viewModel, ChatNoteEvent.Save)

            assertNull(viewModel.state.value.editor)
            assertEquals(ChatNote(GROUP, "Readers", "Tuesdays"), notes.all.value[GROUP])
            assertEquals("Readers", viewModel.state.value.name)
        }

    @Test
    fun `clearing both removes them, and a field stops at its limit`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, ChatNoteEvent.Edit)
            send(viewModel, ChatNoteEvent.NameChanged("n".repeat(ChatNote.MAX_NAME_LENGTH + 5)))
            assertEquals(
                ChatNote.MAX_NAME_LENGTH,
                viewModel.state.value.editor!!
                    .name.length,
            )

            send(viewModel, ChatNoteEvent.NameChanged(""))
            send(viewModel, ChatNoteEvent.NoteChanged(""))
            send(viewModel, ChatNoteEvent.Save)

            assertNull(notes.all.value[GROUP])
            assertEquals(ChatNoteUiState(shown = true), viewModel.state.value)
        }

    @Test
    fun `a failed save keeps the editor open and says so, and cancelling keeps nothing`() =
        runTest {
            val viewModel = viewModel()
            notes.failure = DomainError.Unknown(code = 0, message = "disk full")
            send(viewModel, ChatNoteEvent.Edit)
            send(viewModel, ChatNoteEvent.NoteChanged("Wednesdays"))
            send(viewModel, ChatNoteEvent.Save)

            assertEquals(
                true,
                viewModel.state.value.editor
                    ?.failed,
            )
            assertEquals(
                "Tuesdays",
                notes.all.value
                    .getValue(GROUP)
                    .note,
            )

            send(viewModel, ChatNoteEvent.Dismiss)
            assertNull(viewModel.state.value.editor)
            assertEquals("Tuesdays", viewModel.state.value.note)
        }

    private companion object {
        const val GROUP = -100L
        const val OTHER = -200L
    }
}
