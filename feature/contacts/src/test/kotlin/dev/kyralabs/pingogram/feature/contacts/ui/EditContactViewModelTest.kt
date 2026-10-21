package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/**
 * [EditContactViewModel] on a [FakeContactRepository] (`REQUIREMENTS.md` FR-P1): loading the user, editing a contact
 * and adding a user who is none, sharing the phone number where it applies, deleting with its question, errors
 * (`FLOOD_WAIT`, network, unauthorized, a rejected name) and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditContactViewModelTest {
    private val repository = FakeContactRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): EditContactViewModel {
        val viewModel = EditContactViewModel(USER, handle, repository)
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: EditContactViewModel,
        event: EditContactEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun TestScope.effectsOf(viewModel: EditContactViewModel): List<EditContactEffect> {
        val effects = mutableListOf<EditContactEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val EditContactViewModel.content get() = uiState.value as EditContactUiState.Content

    private fun editable(
        isContact: Boolean = true,
        canSharePhoneNumber: Boolean = false,
    ) = Outcome.Success(
        EditableContact(
            contact(USER, "Grace", "Hopper", phoneNumber = "447911123456", isContact = isContact),
            canSharePhoneNumber,
        ),
    )

    // Loading

    @Test
    fun `it loads the route's user after it started, with their names in the fields`() =
        runTest {
            val answer = CompletableDeferred<Outcome<EditableContact>>()
            repository.onEditableContact = { answer.await() }
            val viewModel = viewModel()
            assertEquals(EditContactUiState.Loading, viewModel.uiState.value)
            assertEquals(listOf("editableContact:$USER"), repository.calls)

            answer.complete(editable())
            runCurrent()

            val content = viewModel.content
            assertEquals("Grace Hopper", content.name)
            assertEquals("Grace", content.firstName)
            assertEquals("Hopper", content.lastName)
            assertEquals("447911123456", content.phoneNumber)
            assertTrue(content.isContact)
            assertFalse(content.canSharePhoneNumber)
        }

    @Test
    fun `a failure to load shows its error until try again`() =
        runTest {
            listOf(DomainError.FloodWait(5), DomainError.Network, DomainError.Unauthorized).forEach { error ->
                repository.onEditableContact = { Outcome.Failure(error) }
                assertEquals(EditContactUiState.Error(error), viewModel().uiState.value)
            }

            repository.onEditableContact = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            repository.onEditableContact = { editable() }
            send(viewModel, EditContactEvent.Retry)
            assertEquals("Grace", viewModel.content.firstName)
        }

    // Saving

    @Test
    fun `saving sends the names and closes`() =
        runTest {
            repository.onEditableContact = { editable() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditContactEvent.FirstNameChanged("Admiral"))
            send(viewModel, EditContactEvent.Save)

            assertTrue(repository.calls.contains("save:$USER:Admiral:Hopper:false"))
            assertEquals(listOf(EditContactEffect.Done), effects)
        }

    @Test
    fun `saving needs a first name`() =
        runTest {
            repository.onEditableContact = { editable() }
            val viewModel = viewModel()

            send(viewModel, EditContactEvent.FirstNameChanged(" "))
            send(viewModel, EditContactEvent.Save)

            assertFalse(viewModel.content.canSave)
            assertTrue(repository.calls.none { it.startsWith("save") })
        }

    @Test
    fun `sharing the phone number is offered only where the user cannot see it`() =
        runTest {
            repository.onEditableContact = { editable(isContact = false, canSharePhoneNumber = true) }
            val viewModel = viewModel()

            send(viewModel, EditContactEvent.SharePhoneNumberChanged(true))
            send(viewModel, EditContactEvent.Save)
            assertTrue(repository.calls.contains("save:$USER:Grace:Hopper:true"))

            repository.onEditableContact = { editable(canSharePhoneNumber = false) }
            val other = viewModel()
            send(other, EditContactEvent.SharePhoneNumberChanged(true))
            assertFalse(other.content.sharePhoneNumber)
        }

    @Test
    fun `a rejected name shows at the first name, a flood wait counts down, other errors are snackbars`() =
        runTest {
            repository.onEditableContact = { editable() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            repository.onSave = { _, _, _, _ -> Outcome.Failure(DomainError.FirstNameInvalid) }
            send(viewModel, EditContactEvent.Save)
            assertEquals(
                ContactFieldError(ContactField.FirstName, DomainError.FirstNameInvalid),
                viewModel.content.fieldError,
            )

            repository.onSave = { _, _, _, _ -> Outcome.Failure(DomainError.FloodWait(2)) }
            send(viewModel, EditContactEvent.Save)
            assertEquals(2L, viewModel.content.floodWaitSeconds)
            assertFalse(viewModel.content.canSave)
            advanceTimeBy(2_001)
            runCurrent()
            assertNull(viewModel.content.floodWaitSeconds)

            repository.onSave = { _, _, _, _ -> Outcome.Failure(DomainError.Network) }
            send(viewModel, EditContactEvent.Save)
            repository.onSave = { _, _, _, _ -> Outcome.Failure(DomainError.Unauthorized) }
            send(viewModel, EditContactEvent.Save)
            assertEquals(
                listOf(
                    EditContactEffect.ShowError(DomainError.Network),
                    EditContactEffect.ShowError(DomainError.Unauthorized),
                ),
                effects,
            )
        }

    // Deleting

    @Test
    fun `deleting asks first, then deletes and closes`() =
        runTest {
            repository.onEditableContact = { editable() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditContactEvent.Delete)
            assertTrue(viewModel.content.showsDeleteDialog)
            assertTrue(repository.calls.none { it.startsWith("delete") })

            send(viewModel, EditContactEvent.ConfirmDelete)

            assertFalse(viewModel.content.showsDeleteDialog)
            assertTrue(repository.calls.contains("delete:$USER"))
            assertEquals(listOf(EditContactEffect.Done), effects)
        }

    @Test
    fun `not confirming deletes nothing, and a user who is no contact cannot be deleted`() =
        runTest {
            repository.onEditableContact = { editable() }
            val viewModel = viewModel()
            send(viewModel, EditContactEvent.Delete)
            send(viewModel, EditContactEvent.DismissDialog)
            send(viewModel, EditContactEvent.ConfirmDelete)
            assertTrue(repository.calls.none { it.startsWith("delete") })

            repository.onEditableContact = { editable(isContact = false) }
            val stranger = viewModel()
            send(stranger, EditContactEvent.Delete)
            assertFalse(stranger.content.showsDeleteDialog)
        }

    // Process death

    @Test
    fun `what was typed and the question survive process death`() =
        runTest {
            repository.onEditableContact = { editable(canSharePhoneNumber = true) }
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)
            send(viewModel, EditContactEvent.FirstNameChanged("Admiral"))
            send(viewModel, EditContactEvent.SharePhoneNumberChanged(true))
            send(viewModel, EditContactEvent.Delete)

            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })).content

            assertEquals("Admiral", restored.firstName)
            assertTrue(restored.sharePhoneNumber)
            assertTrue(restored.showsDeleteDialog)
        }

    private companion object {
        const val USER = 4L
    }
}
