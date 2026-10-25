package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** [DefaultAppLockRepository] on [FakePasscodeRepository] and the real login steps on [FakeTdlibClient]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppLockRepositoryTest {
    private val client = FakeTdlibClient(authState = AuthState.Ready)
    private val time = TestTimeSource()

    private fun TestScope.lock(passcodes: FakePasscodeRepository): DefaultAppLockRepository =
        TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), testScheduler.timeSource)
            .let { auth ->
                DefaultAppLockRepository(
                    passcodeRepository = passcodes,
                    authSteps = auth.authStep,
                    logOut = auth::logOut,
                    scope = backgroundScope,
                    timeSource = time,
                )
            }.also { runCurrent() }

    private fun withPasscode(autoLock: AutoLock = AutoLock.OneMinute) =
        FakePasscodeRepository(PasscodeSettings(autoLock = autoLock), passcode = "1234")

    private suspend fun TestScope.tdlib(state: TdApi.AuthorizationState) {
        client.emitAuthorizationState(state)
        runCurrent()
    }

    @Test
    fun `starts locked with a passcode and unlocked without`() =
        runTest {
            assertEquals(LockState.Locked, lock(withPasscode()).state.value)
            assertEquals(LockState.Unlocked, lock(FakePasscodeRepository()).state.value)
        }

    @Test
    fun `is unknown until the settings are read`() =
        runTest {
            val authRepository =
                TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), testScheduler.timeSource)
            val lock =
                DefaultAppLockRepository(
                    withPasscode(),
                    authRepository.authStep,
                    authRepository::logOut,
                    backgroundScope,
                    time,
                )

            assertEquals(LockState.Unknown, lock.state.value)
            runCurrent()
            assertEquals(LockState.Locked, lock.state.value)
        }

    @Test
    fun `the right passcode unlocks, a wrong one does not`() =
        runTest {
            val lock = lock(withPasscode())

            assertEquals(PasscodeCheck.Wrong, lock.unlock("4321"))
            assertEquals(LockState.Locked, lock.state.value)

            assertEquals(PasscodeCheck.Correct, lock.unlock("1234"))
            assertEquals(LockState.Unlocked, lock.state.value)
        }

    @Test
    fun `checking a passcode leaves the lock as it is`() =
        runTest {
            val lock = lock(withPasscode())

            assertEquals(PasscodeCheck.Correct, lock.checkPasscode("1234"))
            assertEquals(LockState.Locked, lock.state.value)
        }

    @Test
    fun `locks once hidden for the auto-lock delay, not before`() =
        runTest {
            val lock = lock(withPasscode(AutoLock.OneMinute))
            lock.unlock("1234")

            lock.onAppHidden()
            time += 59.seconds
            lock.onAppShown()
            assertEquals(LockState.Unlocked, lock.state.value)

            lock.onAppHidden()
            time += 1.minutes
            lock.onAppShown()
            assertEquals(LockState.Locked, lock.state.value)
        }

    @Test
    fun `locks as soon as it is hidden with an immediate auto-lock`() =
        runTest {
            val lock = lock(withPasscode(AutoLock.Immediately))
            lock.unlock("1234")

            lock.onAppHidden()

            assertEquals(LockState.Locked, lock.state.value)
        }

    @Test
    fun `setting a passcode leaves the app open, removing it unlocks`() =
        runTest {
            val passcodes = FakePasscodeRepository()
            val lock = lock(passcodes)

            passcodes.setPasscode("1234", PasscodeType.Pin)
            runCurrent()
            assertEquals(LockState.Unlocked, lock.state.value)

            lock.onAppHidden()
            time += 1.minutes
            lock.onAppShown()
            assertEquals(LockState.Locked, lock.state.value)

            passcodes.removePasscode()
            runCurrent()
            assertEquals(LockState.Unlocked, lock.state.value)
        }

    @Test
    fun `without an account it is unlocked, and the next login starts unlocked`() =
        runTest {
            val lock = lock(withPasscode())

            tdlib(TdApiFixtures.loggingOut())
            assertEquals(LockState.Unlocked, lock.state.value)

            tdlib(TdApiFixtures.closed())
            tdlib(TdApiFixtures.waitPhoneNumber())
            tdlib(TdApiFixtures.ready())
            assertEquals(LockState.Unlocked, lock.state.value)
        }

    @Test
    fun `does not lock while signed out`() =
        runTest {
            client.authState.value = AuthState.WaitPhoneNumber
            val lock = lock(withPasscode(AutoLock.Immediately))

            lock.onAppHidden()

            assertEquals(LockState.Unlocked, lock.state.value)
        }

    @Test
    fun `makes attempts wait after five wrong passcodes, doubling`() =
        runTest {
            val lock = lock(withPasscode())

            repeat(4) { assertEquals(PasscodeCheck.Wrong, lock.unlock("0000")) }
            assertEquals(PasscodeCheck.Wait(30.seconds), lock.unlock("0000"))

            // Not even the right passcode is checked meanwhile.
            time += 10.seconds
            assertEquals(PasscodeCheck.Wait(20.seconds), lock.unlock("1234"))
            assertEquals(LockState.Locked, lock.state.value)

            time += 20.seconds
            assertEquals(Duration.ZERO, lock.remainingWait())
            assertEquals(PasscodeCheck.Wait(60.seconds), lock.unlock("0000"))

            time += 60.seconds
            assertEquals(PasscodeCheck.Correct, lock.unlock("1234"))
            assertEquals(Duration.ZERO, lock.remainingWait())
        }

    @Test
    fun `waits at most half an hour`() {
        assertEquals(Duration.ZERO, DefaultAppLockRepository.waitAfter(4))
        assertEquals(30.seconds, DefaultAppLockRepository.waitAfter(5))
        assertEquals(16.minutes, DefaultAppLockRepository.waitAfter(10))
        assertEquals(30.minutes, DefaultAppLockRepository.waitAfter(11))
        assertEquals(30.minutes, DefaultAppLockRepository.waitAfter(Int.MAX_VALUE))
    }

    @Test
    fun `a restart with attempts to wait for starts the wait over`() =
        runTest {
            val passcodes = FakePasscodeRepository(PasscodeSettings(failedAttempts = 6), passcode = "1234")
            val lock = lock(passcodes)

            assertEquals(60.seconds, lock.remainingWait())
            assertEquals(PasscodeCheck.Wait(60.seconds), lock.unlock("1234"))
        }

    @Test
    fun `biometrics unlock only where turned on, and forget the failed attempts`() =
        runTest {
            val off = FakePasscodeRepository(PasscodeSettings(biometricUnlock = false), passcode = "1234")
            val lockedOff = lock(off)
            lockedOff.unlockWithBiometrics()
            assertEquals(LockState.Locked, lockedOff.state.value)

            val passcodes = withPasscode()
            val lock = lock(passcodes)
            repeat(5) { lock.unlock("0000") }

            lock.unlockWithBiometrics()

            assertEquals(LockState.Unlocked, lock.state.value)
            assertEquals(0, passcodes.settings.value.failedAttempts)
            assertEquals(Duration.ZERO, lock.remainingWait())
        }

    @Test
    fun `a check that cannot be read fails and changes nothing`() =
        runTest {
            val passcodes = withPasscode()
            val lock = lock(passcodes)
            passcodes.failure = DomainError.Unknown(0, "Disk unavailable")

            assertEquals(PasscodeCheck.Failed(DomainError.Unknown(0, "Disk unavailable")), lock.unlock("1234"))
            assertEquals(LockState.Locked, lock.state.value)
        }

    @Test
    fun `forgetting the passcode logs out, then removes it`() =
        runTest {
            client.on(TdApi.LogOut::class, TdResult.Success(TdApi.Ok()))
            val passcodes = withPasscode()
            val lock = lock(passcodes)

            assertEquals(Outcome.Success(Unit), lock.forgetPasscode())

            assertEquals(1, client.sentOf<TdApi.LogOut>().size)
            assertFalse(passcodes.settings.value.isSet)
            assertEquals(LockState.Unlocked, lock.state.value)
        }

    @Test
    fun `a failed logout keeps the passcode and the lock`() =
        runTest {
            client.on(TdApi.LogOut::class, TdResult.Failure(TdError(500, "Internal")))
            val passcodes = withPasscode()
            val lock = lock(passcodes)

            assertTrue(lock.forgetPasscode() is Outcome.Failure)

            assertTrue(passcodes.settings.value.isSet)
            assertEquals(LockState.Locked, lock.state.value)
        }
}
