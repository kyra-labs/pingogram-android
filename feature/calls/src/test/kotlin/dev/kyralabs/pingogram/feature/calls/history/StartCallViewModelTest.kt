package dev.kyralabs.pingogram.feature.calls.history

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.feature.calls.ui.FakeCameraAccess
import dev.kyralabs.pingogram.feature.calls.ui.invite.FakeContacts
import dev.kyralabs.pingogram.feature.calls.ui.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The contacts "Start a call" offers: people only, the query, the camera and what survives process death. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartCallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val contacts = FakeContacts()
    private val camera = FakeCameraAccess()

    init {
        Dispatchers.setMain(dispatcher)
        contacts.users.value =
            Outcome.Success(
                persistentListOf(
                    user(7, "Grace", "Hopper"),
                    user(8, "Alan", "Turing"),
                    user(10, "Helper", "").copy(type = UserType.Bot),
                ),
            )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `offers the contacts, people only, with video calls where the device has a camera`() =
        runTest(dispatcher) {
            camera.hasCamera = true
            val viewModel = StartCallViewModel(SavedStateHandle(), contacts, camera)

            viewModel.uiState.test {
                assertEquals(StartCallUiState.Loading, awaitItem())
                val content = awaitItem() as StartCallUiState.Content
                assertEquals(listOf(7L, 8L), content.people.map { it.userId })
                assertEquals("Grace Hopper", content.people.first().name)
                assertTrue(content.canVideoCall)
            }
        }

    @Test
    fun `without a camera the rows offer voice calls only`() =
        runTest(dispatcher) {
            camera.hasCamera = false
            val viewModel = StartCallViewModel(SavedStateHandle(), contacts, camera)

            viewModel.uiState.test {
                skipItems(1)
                assertFalse((awaitItem() as StartCallUiState.Content).canVideoCall)
            }
        }

    @Test
    fun `the query filters by the start of a name, and says when nobody matches`() =
        runTest(dispatcher) {
            val viewModel = StartCallViewModel(SavedStateHandle(), contacts, camera)

            viewModel.uiState.test {
                skipItems(2)
                viewModel.setQuery("tur")
                assertEquals(listOf(8L), (awaitItem() as StartCallUiState.Content).people.map { it.userId })

                viewModel.setQuery("zzz")
                val none = awaitItem() as StartCallUiState.Content
                assertTrue(none.people.isEmpty())
                assertTrue(none.noMatches)
            }
        }

    @Test
    fun `the query survives process death, and closing the sheet forgets it`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            StartCallViewModel(handle, contacts, camera).setQuery("gr")

            val restored = StartCallViewModel(handle, contacts, camera)
            assertEquals("gr", restored.query.value)

            restored.reset()
            assertEquals("", restored.query.value)
        }

    @Test
    fun `a failure to read the contacts says why`() =
        runTest(dispatcher) {
            contacts.users.value = Outcome.Failure(DomainError.Network)
            val viewModel = StartCallViewModel(SavedStateHandle(), contacts, camera)

            viewModel.uiState.test {
                skipItems(1)
                assertEquals(StartCallUiState.Error(DomainError.Network), awaitItem())
            }
        }
}
