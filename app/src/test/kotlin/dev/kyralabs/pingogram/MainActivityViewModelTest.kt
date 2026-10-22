package dev.kyralabs.pingogram

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.share.IncomingShare
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertNull
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

    // The settings are read already unless a test says otherwise.
    private val appearance = FakeAppearanceRepository(initial = AppearanceSettings())
    private val appLock = FakeAppLockRepository()
    private val passcodes = FakePasscodeRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(
        appearanceRepository: FakeAppearanceRepository = appearance,
    ): MainActivityViewModel =
        MainActivityViewModel(
            TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), TimeSource.Monotonic),
            appearanceRepository,
            appLock,
            passcodes,
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
    fun `has no appearance until the settings are read`() =
        runTest {
            val unread = FakeAppearanceRepository()

            assertNull(viewModel(unread).appearance.value)
        }

    @Test
    fun `follows the appearance settings`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(AppearanceSettings(), viewModel.appearance.value)

            appearance.save(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true))
            runCurrent()

            assertEquals(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true), viewModel.appearance.value)
        }

    @Test
    fun `keeps the splash screen until the settings are read, for at most 300 ms`() =
        runTest {
            client.authState.value = AuthState.Ready
            val unread = FakeAppearanceRepository()
            val viewModel = viewModel(unread)
            assertTrue(viewModel.keepSplashScreen())

            timeSource += 299.milliseconds
            assertTrue(viewModel.keepSplashScreen())

            timeSource += 1.milliseconds
            assertFalse(viewModel.keepSplashScreen())
        }

    @Test
    fun `releases the splash screen once both the login step and the settings are known`() =
        runTest {
            val unread = FakeAppearanceRepository()
            val viewModel = viewModel(unread)

            tdlib(TdApiFixtures.waitPhoneNumber())
            assertTrue(viewModel.keepSplashScreen())

            unread.save(AppearanceSettings(themeMode = ThemeMode.Light))
            runCurrent()
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

    @Test
    fun `a chat to open waits until it is opened, and a newer one replaces it`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(null, viewModel.chatToOpen.value)

            viewModel.openChat(100)
            viewModel.openChat(200)
            assertEquals(200L, viewModel.chatToOpen.value)

            viewModel.onChatOpened()
            assertEquals(null, viewModel.chatToOpen.value)
        }

    @Test
    fun `a share waits until it is opened, and a newer one replaces it`() =
        runTest {
            val viewModel = viewModel()
            val first = IncomingShare(SharedContent(FormattedText("Hi", persistentListOf())))
            val second = IncomingShare(SharedContent(FormattedText("Look", persistentListOf())), chatId = 100)
            assertEquals(null, viewModel.shareToOpen.value)

            viewModel.openShare(first)
            viewModel.openShare(second)
            assertEquals(second, viewModel.shareToOpen.value)

            viewModel.onShareOpened()
            assertEquals(null, viewModel.shareToOpen.value)
        }

    @Test
    fun `keeps the splash screen until the lock is known, for at most 300 ms`() =
        runTest {
            client.authState.value = AuthState.Ready
            appLock.setState(LockState.Unknown)
            val viewModel = viewModel()
            assertTrue(viewModel.keepSplashScreen())

            appLock.setState(LockState.Locked)
            assertEquals(LockState.Locked, viewModel.lock.value)
            assertFalse(viewModel.keepSplashScreen())
        }

    @Test
    fun `tells the lock when the app leaves the screen and comes back`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAppHidden()
            viewModel.onAppShown()

            assertEquals(1, appLock.hiddenCount)
            assertEquals(1, appLock.shownCount)
        }

    @Test
    fun `hides the content from recent apps only with a passcode and the setting on`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.hideContent.value)

            passcodes.setPasscode("1234", PasscodeType.Pin)
            runCurrent()
            assertTrue(viewModel.hideContent.value)

            passcodes.setHideContent(false)
            runCurrent()
            assertFalse(viewModel.hideContent.value)
        }

    @Test
    fun `shows the content in recent apps without a passcode, whatever the setting`() =
        runTest {
            val withoutPasscode = FakePasscodeRepository(PasscodeSettings(hideContent = true))

            val viewModel =
                MainActivityViewModel(
                    TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), TimeSource.Monotonic),
                    appearance,
                    appLock,
                    withoutPasscode,
                    timeSource,
                ).also { runCurrent() }

            assertFalse(viewModel.hideContent.value)
        }
}
