package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * [DefaultChatLockRepository] (`docs/adr/0029-locked-chats.md`) on in-memory ids, [FakePasscodeRepository],
 * [FakeAppLockRepository] and the real login steps on [FakeTdlibClient].
 */
class DefaultChatLockRepositoryTest {
    private val client = FakeTdlibClient(authState = AuthState.Ready)
    private val storage = MemoryLockedChatIds()
    private val appLock = FakeAppLockRepository()

    private fun TestScope.repository(
        passcodes: FakePasscodeRepository = FakePasscodeRepository(passcode = "1234"),
    ): DefaultChatLockRepository =
        DefaultChatLockRepository(
            storage = storage,
            passcodeRepository = passcodes,
            appLock = appLock,
            authRepository =
                TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), testScheduler.timeSource),
            scope = backgroundScope,
            timeSource = testScheduler.timeSource,
        ).also { runCurrent() }

    @Test
    fun `chats lock and unlock, and stay hidden until the passcode opens them`() =
        runTest {
            val chats = repository()

            assertEquals(Outcome.Success(Unit), chats.setLocked(setOf(1, 2), locked = true))
            assertEquals(setOf(1L, 2L), chats.lockedChatIds.first())
            runCurrent()
            assertEquals(setOf(1L, 2L), chats.hiddenChatIds.value)

            assertEquals(PasscodeCheck.Wrong, chats.unlock("0000"))
            assertFalse(chats.unlocked.value)
            assertEquals(PasscodeCheck.Correct, chats.unlock("1234"))
            assertTrue(chats.unlocked.value)
            runCurrent()
            assertEquals(emptySet<Long>(), chats.hiddenChatIds.value)
            assertEquals(setOf(1L, 2L), chats.lockedChatIds.first())

            chats.setLocked(setOf(1), locked = false)
            assertEquals(setOf(2L), chats.lockedChatIds.first())
        }

    @Test
    fun `the passcode is checked as the app's lock checks it, waits included`() =
        runTest {
            val chats = repository()
            appLock.wait = 30.seconds

            assertTrue(chats.unlock("1234") is PasscodeCheck.Wait)
            assertFalse(chats.unlocked.value)
            assertEquals(listOf("1234"), appLock.checked)
        }

    @Test
    fun `a minute out of sight or the app locking closes them again`() =
        runTest {
            val chats = repository()
            chats.setLocked(setOf(1), locked = true)
            chats.unlock("1234")

            // The photo picker, a call: back within the minute, they stay open.
            chats.onAppHidden()
            advanceTimeBy(30.seconds)
            chats.onAppShown()
            assertTrue(chats.unlocked.value)

            chats.onAppHidden()
            advanceTimeBy(DefaultChatLockRepository.RELOCK_DELAY)
            chats.onAppShown()
            assertFalse(chats.unlocked.value)

            chats.unlock("1234")
            appLock.setState(LockState.Locked)
            runCurrent()
            assertFalse(chats.unlocked.value)
        }

    @Test
    fun `without a passcode nothing is locked, nothing can be, and removing it unlocks every chat for good`() =
        runTest {
            val passcodes = FakePasscodeRepository(passcode = "1234")
            val chats = repository(passcodes)
            chats.setLocked(setOf(1, 2), locked = true)

            passcodes.removePasscode()
            runCurrent()

            assertEquals(emptySet<Long>(), chats.lockedChatIds.first())
            assertEquals(emptySet<Long>(), storage.ids.value)
            assertTrue(chats.setLocked(setOf(3), locked = true) is Outcome.Failure)
            assertEquals(emptySet<Long>(), storage.ids.value)
        }

    @Test
    fun `logging out unlocks every chat for good`() =
        runTest {
            val chats = repository()
            chats.setLocked(setOf(1), locked = true)

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            runCurrent()

            assertEquals(emptySet<Long>(), storage.ids.value)
            assertEquals(emptySet<Long>(), chats.lockedChatIds.first())
        }

    @Test
    fun `biometrics open them only where biometric unlock is on`() =
        runTest {
            val passcodes = FakePasscodeRepository(PasscodeSettings(biometricUnlock = false), passcode = "1234")
            val chats = repository(passcodes)

            chats.unlockWithBiometrics()
            assertFalse(chats.unlocked.value)

            passcodes.setBiometricUnlock(true)
            chats.unlockWithBiometrics()
            assertTrue(chats.unlocked.value)
        }

    /** [LockedChatIdsRepository] in memory. */
    private class MemoryLockedChatIds : LockedChatIdsRepository {
        override val ids = MutableStateFlow<Set<Long>>(emptySet())

        override suspend fun setIds(ids: Set<Long>): Outcome<Unit> {
            this.ids.value = ids
            return Outcome.Success(Unit)
        }
    }
}
