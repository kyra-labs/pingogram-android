package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import kotlinx.collections.immutable.persistentMapOf
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
 * [NewContactViewModel] on fake repositories (`REQUIREMENTS.md` FR-P1): the calling code it starts the number with,
 * the country of the number, adding by phone number and by username, a number not on Telegram and its invitation,
 * field errors, `FLOOD_WAIT`, other errors, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewContactViewModelTest {
    private val contacts = FakeContactRepository()
    private val countries = FakeCountryRepository()
    private val users =
        FakeUserRepository().apply {
            myUserId.value = ME
            userMap.value = persistentMapOf(ME to contact(ME, "Me", phoneNumber = "447700900123", isContact = false))
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): NewContactViewModel {
        val viewModel = NewContactViewModel(handle, contacts, countries, users)
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: NewContactViewModel,
        event: NewContactEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun TestScope.effectsOf(viewModel: NewContactViewModel): List<NewContactEffect> {
        val effects = mutableListOf<NewContactEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.fill(
        viewModel: NewContactViewModel,
        firstName: String = "Grace",
        phoneNumber: String = "+44 7911 123456",
    ) {
        send(viewModel, NewContactEvent.FirstNameChanged(firstName))
        send(viewModel, NewContactEvent.PhoneNumberChanged(phoneNumber))
    }

    // Starting

    @Test
    fun `the number starts with the calling code of the user's own, and its country`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals("+44 ", state.phoneNumber)
            assertEquals("🇬🇧 United Kingdom", state.country)
            assertEquals(ContactMethod.PhoneNumber, state.method)
            assertFalse(state.canSubmit)
        }

    @Test
    fun `without the user's own number the field starts empty`() =
        runTest {
            users.myUserId.value = null

            assertEquals("", viewModel().uiState.value.phoneNumber)
        }

    @Test
    fun `the country follows the number after a pause in typing`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NewContactEvent.PhoneNumberChanged("+1 202"))
            assertEquals("🇬🇧 United Kingdom", viewModel.uiState.value.country)
            advanceTimeBy(COUNTRY_DELAY)
            runCurrent()

            assertEquals("🇺🇸 United States", viewModel.uiState.value.country)
        }

    @Test
    fun `letters pasted into the number are left out`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NewContactEvent.PhoneNumberChanged("tel: +44 (79) 11-12"))

            assertEquals(" +44 (79) 11-12", viewModel.uiState.value.phoneNumber)
        }

    @Test
    fun `names are cut at Telegram's 64 characters`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NewContactEvent.FirstNameChanged("a".repeat(70)))

            assertEquals(64, viewModel.uiState.value.firstName.length)
        }

    // By phone number

    @Test
    fun `adding by phone number sends its digits after a +, with the names, and closes`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            fill(viewModel)
            send(viewModel, NewContactEvent.LastNameChanged("Hopper"))

            send(viewModel, NewContactEvent.Submit)

            assertTrue(contacts.calls.contains("addByPhoneNumber:+447911123456:Grace:Hopper"))
            assertEquals(listOf(NewContactEffect.Added(7)), effects)
        }

    @Test
    fun `add needs a first name and a number`() =
        runTest {
            val viewModel = viewModel()
            send(viewModel, NewContactEvent.PhoneNumberChanged("+44 7911 123456"))

            send(viewModel, NewContactEvent.Submit)

            assertFalse(viewModel.uiState.value.canSubmit)
            assertTrue(contacts.calls.none { it.startsWith("add") })
        }

    @Test
    fun `a number of too few or too many digits shows at its field, without asking Telegram`() =
        runTest {
            val viewModel = viewModel()
            listOf("+44 79", "+44 7911 1234 5678 90").forEach { number ->
                fill(viewModel, phoneNumber = number)

                send(viewModel, NewContactEvent.Submit)

                assertEquals(ContactFieldError(ContactField.PhoneNumber), viewModel.uiState.value.fieldError)
            }
            assertTrue(contacts.calls.none { it.startsWith("add") })
        }

    @Test
    fun `typing clears the field's error`() =
        runTest {
            val viewModel = viewModel()
            fill(viewModel, phoneNumber = "+44 79")
            send(viewModel, NewContactEvent.Submit)

            send(viewModel, NewContactEvent.PhoneNumberChanged("+44 791"))

            assertNull(viewModel.uiState.value.fieldError)
        }

    @Test
    fun `a number Telegram rejects shows at its field, a rejected name at the first name`() =
        runTest {
            val viewModel = viewModel()
            fill(viewModel)
            contacts.onAddByPhoneNumber = { _, _, _ -> Outcome.Failure(DomainError.PhoneNumberInvalid) }
            send(viewModel, NewContactEvent.Submit)
            assertEquals(
                ContactFieldError(ContactField.PhoneNumber, DomainError.PhoneNumberInvalid),
                viewModel.uiState.value.fieldError,
            )

            contacts.onAddByPhoneNumber = { _, _, _ -> Outcome.Failure(DomainError.FirstNameInvalid) }
            send(viewModel, NewContactEvent.Submit)
            assertEquals(
                ContactFieldError(ContactField.FirstName, DomainError.FirstNameInvalid),
                viewModel.uiState.value.fieldError,
            )
        }

    @Test
    fun `it is working while Telegram answers, and fields wait`() =
        runTest {
            val answer = CompletableDeferred<Outcome<AddContactResult>>()
            contacts.onAddByPhoneNumber = { _, _, _ -> answer.await() }
            val viewModel = viewModel()
            fill(viewModel)

            send(viewModel, NewContactEvent.Submit)
            assertTrue(viewModel.uiState.value.isWorking)
            send(viewModel, NewContactEvent.FirstNameChanged("Ada"))
            assertEquals("Grace", viewModel.uiState.value.firstName)

            answer.complete(Outcome.Success(AddContactResult.Added(7)))
            runCurrent()
            assertFalse(viewModel.uiState.value.isWorking)
        }

    @Test
    fun `a number not on Telegram offers to invite, and changing it returns to the form`() =
        runTest {
            contacts.onAddByPhoneNumber = { _, _, _ -> Outcome.Success(AddContactResult.NotOnTelegram) }
            contacts.link = "https://t.me/dl"
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            fill(viewModel)

            send(viewModel, NewContactEvent.Submit)
            assertTrue(viewModel.uiState.value.notOnTelegram)

            send(viewModel, NewContactEvent.Invite)
            assertEquals(listOf(NewContactEffect.ShareInvite("https://t.me/dl")), effects)

            send(viewModel, NewContactEvent.ChangeNumber)
            assertFalse(viewModel.uiState.value.notOnTelegram)
        }

    @Test
    fun `a flood wait counts down while add waits`() =
        runTest {
            contacts.onAddByPhoneNumber = { _, _, _ -> Outcome.Failure(DomainError.FloodWait(3)) }
            val viewModel = viewModel()
            fill(viewModel)

            send(viewModel, NewContactEvent.Submit)
            assertEquals(3L, viewModel.uiState.value.floodWaitSeconds)
            assertEquals(3L, viewModel.uiState.value.floodWaitTotalSeconds)
            assertFalse(viewModel.uiState.value.canSubmit)

            advanceTimeBy(3_001)
            runCurrent()
            assertNull(viewModel.uiState.value.floodWaitSeconds)
            assertTrue(viewModel.uiState.value.canSubmit)
        }

    @Test
    fun `other errors are snackbars, network and unauthorized alike`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            fill(viewModel)

            listOf(DomainError.Network, DomainError.Unauthorized).forEach { error ->
                contacts.onAddByPhoneNumber = { _, _, _ -> Outcome.Failure(error) }
                send(viewModel, NewContactEvent.Submit)
            }

            assertEquals(
                listOf(
                    NewContactEffect.ShowError(DomainError.Network),
                    NewContactEffect.ShowError(DomainError.Unauthorized),
                ),
                effects,
            )
            assertNull(viewModel.uiState.value.fieldError)
        }

    // By username

    @Test
    fun `adding by username takes the name of a link or after @, without names needed`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, NewContactEvent.MethodChanged(ContactMethod.Username))
            send(viewModel, NewContactEvent.UsernameChanged("https://t.me/grace"))
            assertTrue(viewModel.uiState.value.canSubmit)

            send(viewModel, NewContactEvent.Submit)

            assertTrue(contacts.calls.contains("addByUsername:grace::"))
            assertEquals(listOf(NewContactEffect.Added(7)), effects)
        }

    @Test
    fun `a username no one has shows at its field`() =
        runTest {
            contacts.onAddByUsername = { _, _, _ -> Outcome.Failure(DomainError.NotFound) }
            val viewModel = viewModel()
            send(viewModel, NewContactEvent.MethodChanged(ContactMethod.Username))
            send(viewModel, NewContactEvent.UsernameChanged("@nobody"))

            send(viewModel, NewContactEvent.Submit)

            assertEquals(
                ContactFieldError(ContactField.Username, DomainError.NotFound),
                viewModel.uiState.value.fieldError,
            )
        }

    // Process death

    @Test
    fun `what was typed survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)
            send(viewModel, NewContactEvent.MethodChanged(ContactMethod.Username))
            send(viewModel, NewContactEvent.FirstNameChanged("Grace"))
            send(viewModel, NewContactEvent.UsernameChanged("grace"))

            val restored =
                viewModel(
                    SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }),
                ).uiState.value

            assertEquals(ContactMethod.Username, restored.method)
            assertEquals("Grace", restored.firstName)
            assertEquals("grace", restored.username)
            assertEquals("+44 ", restored.phoneNumber)
        }

    private companion object {
        const val ME = 1L
        const val COUNTRY_DELAY = 301L
    }
}
