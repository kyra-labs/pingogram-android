package dev.kyralabs.pingogram

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {
    private val client = FakeTdlibClient()
    private val timeSource = TestTimeSource()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(): MainActivityViewModel =
        MainActivityViewModel(
            TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), TimeSource.Monotonic),
            timeSource,
        ).also { runCurrent() }

    private suspend fun TestScope.tdlib(state: TdApi.AuthorizationState) {
        client.emitAuthorizationState(state)
        runCurrent()
    }

    private val MainActivityViewModel.state get() = uiState.value

    @Test
    fun `is loading while TDLib starts`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(MainActivityUiState.Loading, viewModel.state)

            tdlib(TdApiFixtures.waitTdlibParameters())

            assertEquals(MainActivityUiState.Loading, viewModel.state)
        }

    @Test
    fun `is signed in once TDLib is ready`() =
        runTest {
            val viewModel = viewModel()

            tdlib(TdApiFixtures.ready())

            assertEquals(MainActivityUiState.SignedIn, viewModel.state)
        }

    @Test
    fun `is signed out on every step but ready`() =
        runTest {
            val signedOutSteps =
                TdApiFixtures.allAuthorizationStates().filterNot {
                    it is TdApi.AuthorizationStateReady || it is TdApi.AuthorizationStateWaitTdlibParameters
                }
            signedOutSteps.forEach { step ->
                // A new view model starts from TDLib's current step.
                client.authState.value = checkNotNull(AuthState.from(step))

                assertEquals(MainActivityUiState.SignedOut, viewModel().state, "for ${AuthState.from(step)}")
            }
        }

    @Test
    fun `logging out signs out and stays signed out while TDLib starts over`() =
        runTest {
            client.authState.value = AuthState.Ready
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(MainActivityUiState.SignedIn, awaitItem())

                tdlib(TdApiFixtures.loggingOut())
                assertEquals(MainActivityUiState.SignedOut, awaitItem())

                // Closed creates a new TDLib instance, which starts over from its parameters.
                tdlib(TdApiFixtures.closing())
                tdlib(TdApiFixtures.closed())
                tdlib(TdApiFixtures.waitTdlibParameters())
                tdlib(TdApiFixtures.waitPhoneNumber())
                expectNoEvents()
            }
        }

    @Test
    fun `a session that closes without logging out signs out`() =
        runTest {
            client.authState.value = AuthState.Ready
            val viewModel = viewModel()

            tdlib(TdApiFixtures.closed())

            assertEquals(MainActivityUiState.SignedOut, viewModel.state)
        }

    @Test
    fun `logging in again after a logout signs in`() =
        runTest {
            client.authState.value = AuthState.Ready
            val viewModel = viewModel()
            tdlib(TdApiFixtures.loggingOut())
            tdlib(TdApiFixtures.closed())
            tdlib(TdApiFixtures.waitPhoneNumber())
            tdlib(TdApiFixtures.waitCode())

            tdlib(TdApiFixtures.ready())

            assertEquals(MainActivityUiState.SignedIn, viewModel.state)
        }

    @Test
    fun `keeps the splash screen while loading, for at most 300 ms`() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.keepSplashScreen())

            timeSource += 299.milliseconds
            assertTrue(viewModel.keepSplashScreen())

            timeSource += 1.milliseconds
            assertFalse(viewModel.keepSplashScreen())
        }

    @Test
    fun `releases the splash screen as soon as TDLib reports a step`() =
        runTest {
            val viewModel = viewModel()

            tdlib(TdApiFixtures.waitPhoneNumber())

            assertFalse(viewModel.keepSplashScreen())
        }

    @Test
    fun `a new view model after process death starts from TDLib's current step`() =
        runTest {
            client.authState.value = AuthState.Ready

            val viewModel = viewModel()

            assertEquals(MainActivityUiState.SignedIn, viewModel.state)
            assertFalse(viewModel.keepSplashScreen())
        }
}
