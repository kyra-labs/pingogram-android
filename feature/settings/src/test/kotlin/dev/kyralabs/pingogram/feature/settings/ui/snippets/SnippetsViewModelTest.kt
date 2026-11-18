package dev.kyralabs.pingogram.feature.settings.ui.snippets

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.testing.FakeSnippetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Snippets: the saved texts in their order, moved by a drag or a screen reader's move; the editor adding, changing and
 * deleting one, its name checked as typed and its draft surviving process death; a failed save said.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnippetsViewModelTest {
    private val repository = FakeSnippetRepository()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.saved.value = listOf(ADDRESS, THANKS, IBAN)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): SnippetsViewModel =
        SnippetsViewModel(handle, repository).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }

    private fun SnippetsViewModel.content() = uiState.value as SnippetsUiState.Content

    private fun SnippetsViewModel.ids() = content().snippets.map { it.id }

    private fun TestScope.send(
        viewModel: SnippetsViewModel,
        vararg events: SnippetsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    @Test
    fun `the page shows the snippets in the user's order`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(listOf(ADDRESS, THANKS, IBAN), (viewModel.uiState.value as SnippetsUiState.Content).snippets)
        }

    @Test
    fun `a drag saves the order it leaves`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(SnippetsEvent.Reorder(listOf(IBAN.id, ADDRESS.id, THANKS.id)))
            runCurrent()

            assertEquals(listOf(IBAN.id, ADDRESS.id, THANKS.id), viewModel.ids())
        }

    @Test
    fun `a screen reader moves a snippet one place, never past the ends`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(SnippetsEvent.Move(THANKS.id, up = true))
            runCurrent()
            viewModel.onEvent(SnippetsEvent.Move(THANKS.id, up = true))
            runCurrent()
            viewModel.onEvent(SnippetsEvent.Move(ADDRESS.id, up = false))
            runCurrent()

            assertEquals(listOf(THANKS.id, IBAN.id, ADDRESS.id), viewModel.ids())
        }

    @Test
    fun `an order that cannot be saved says so and stays`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<SnippetsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Unknown(code = 0, message = "Disk full")

            viewModel.onEvent(SnippetsEvent.Move(IBAN.id, up = true))
            runCurrent()

            assertEquals(listOf(SnippetsEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
            assertEquals(listOf(ADDRESS.id, THANKS.id, IBAN.id), viewModel.ids())
        }

    @Test
    fun `a new snippet is saved at the end and the editor closes`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                SnippetsEvent.StartNew,
                SnippetsEvent.EditName("home"),
                SnippetsEvent.EditText("Flat 4"),
                SnippetsEvent.SaveDraft,
            )

            assertEquals(Snippet(4, "home", "Flat 4"), repository.saved.value.last())
            assertNull(viewModel.content().draft)
        }

    @Test
    fun `a name that is not one word or is taken in any case cannot be saved`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, SnippetsEvent.StartNew, SnippetsEvent.EditText("Text"))

            send(viewModel, SnippetsEvent.EditName("my home"))
            assertEquals(SnippetNameError.NotOneWord, viewModel.content().draft?.nameError)
            send(viewModel, SnippetsEvent.EditName("Thanks"))
            assertEquals(SnippetNameError.Taken, viewModel.content().draft?.nameError)
            assertFalse(viewModel.content().draft!!.canSave)

            send(viewModel, SnippetsEvent.SaveDraft)
            assertEquals(3, repository.saved.value.size)
        }

    @Test
    fun `editing keeps the snippet's own name allowed and its place`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                SnippetsEvent.StartEdit(THANKS.id),
                SnippetsEvent.EditName("THANKS"),
                SnippetsEvent.EditText("Thanks a lot!"),
                SnippetsEvent.SaveDraft,
            )

            assertEquals(listOf(ADDRESS, Snippet(THANKS.id, "THANKS", "Thanks a lot!"), IBAN), repository.saved.value)
        }

    @Test
    fun `the draft survives process death`() =
        runTest {
            val first = viewModel()
            send(first, SnippetsEvent.StartEdit(ADDRESS.id), SnippetsEvent.EditText("New road"))

            val again = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(SnippetDraft(ADDRESS.id, "address", "New road"), again.content().draft)
        }

    @Test
    fun `deleting asks first, and only yes deletes`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, SnippetsEvent.StartEdit(IBAN.id), SnippetsEvent.AskDelete)
            assertEquals(IBAN, viewModel.content().deleting)

            send(viewModel, SnippetsEvent.CancelDelete)
            assertEquals(3, repository.saved.value.size)

            send(viewModel, SnippetsEvent.AskDelete, SnippetsEvent.ConfirmDelete)
            assertEquals(listOf(ADDRESS, THANKS), repository.saved.value)
            assertNull(viewModel.content().draft)
        }

    @Test
    fun `no new snippet past the most`() =
        runTest {
            repository.saved.value = (1L..Snippet.MAX_SNIPPETS).map { Snippet(it, "s$it", "Text") }

            assertFalse(viewModel().content().canAdd)
        }

    @Test
    fun `a failed save keeps the draft and says so`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<SnippetsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Unknown(code = 0, message = "Disk full")

            send(viewModel, SnippetsEvent.StartEdit(ADDRESS.id), SnippetsEvent.EditText("New"), SnippetsEvent.SaveDraft)

            assertEquals(listOf(SnippetsEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
            assertEquals("New", viewModel.content().draft?.text)
        }

    private companion object {
        val ADDRESS = Snippet(1, "address", "12 Crescent Road")
        val THANKS = Snippet(2, "thanks", "Thank you!")
        val IBAN = Snippet(3, "iban", "GB33 BUKB 2020 1555 5555 55")
    }
}
