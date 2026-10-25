package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStorePasscodeRepository] on a real Preferences DataStore in a temporary file. */
class DataStorePasscodeRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository(dataStore: DataStore<Preferences> = dataStore()) =
        DataStorePasscodeRepository(
            dataStore,
            StandardTestDispatcher(testScheduler),
            PasscodeHasher(iterations = 1_000),
        )

    @Test
    fun `reads no passcode and the defaults before anything is saved`() =
        runTest {
            assertEquals(PasscodeSettings(), repository().settings.first())
        }

    @Test
    fun `sets a passcode that only it unlocks`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setPasscode("2580", PasscodeType.Pin))

            assertEquals(PasscodeSettings(isSet = true, type = PasscodeType.Pin), repository.settings.first())
            assertEquals(Outcome.Success(true), repository.checkPasscode("2580"))
            assertEquals(Outcome.Success(false), repository.checkPasscode("0852"))
        }

    @Test
    fun `never saves the passcode itself`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            repository(dataStore(firstProcess)).setPasscode("secret passcode", PasscodeType.Password)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertFalse("secret passcode" in file.readBytes().toString(Charsets.ISO_8859_1))
        }

    @Test
    fun `counts wrong passcodes until the right one`() =
        runTest {
            val repository = repository()
            repository.setPasscode("1234", PasscodeType.Pin)

            repository.checkPasscode("0000")
            repository.checkPasscode("1111")
            assertEquals(2, repository.settings.first().failedAttempts)

            repository.checkPasscode("1234")
            assertEquals(0, repository.settings.first().failedAttempts)
        }

    @Test
    fun `a new passcode replaces the old one and forgets the failed attempts`() =
        runTest {
            val repository = repository()
            repository.setPasscode("1234", PasscodeType.Pin)
            repository.checkPasscode("0000")

            repository.setPasscode("long password", PasscodeType.Password)

            assertEquals(Outcome.Success(false), repository.checkPasscode("1234"))
            assertEquals(Outcome.Success(true), repository.checkPasscode("long password"))
            assertEquals(PasscodeSettings(isSet = true, type = PasscodeType.Password), repository.settings.first())
        }

    @Test
    fun `refuses a passcode its type does not accept`() =
        runTest {
            val repository = repository()

            assertThrows<IllegalArgumentException> { repository.setPasscode("12a4", PasscodeType.Pin) }
            assertThrows<IllegalArgumentException> { repository.setPasscode("123", PasscodeType.Password) }
        }

    @Test
    fun `without a passcode nothing matches and nothing is counted`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Success(false), repository.checkPasscode(""))
            assertEquals(0, repository.settings.first().failedAttempts)
        }

    @Test
    fun `removing the passcode turns the lock off and keeps the other settings`() =
        runTest {
            val repository = repository()
            repository.setPasscode("1234", PasscodeType.Pin)
            repository.setAutoLock(AutoLock.OneHour)
            repository.checkPasscode("0000")

            repository.removePasscode()

            assertEquals(PasscodeSettings(autoLock = AutoLock.OneHour), repository.settings.first())
            assertEquals(Outcome.Success(false), repository.checkPasscode("1234"))
        }

    @Test
    fun `emits each setting as it is saved`() =
        runTest {
            val repository = repository()

            repository.settings.test {
                assertEquals(PasscodeSettings(), awaitItem())

                repository.setAutoLock(AutoLock.Immediately)
                assertEquals(PasscodeSettings(autoLock = AutoLock.Immediately), awaitItem())

                repository.setBiometricUnlock(false)
                assertEquals(PasscodeSettings(autoLock = AutoLock.Immediately, biometricUnlock = false), awaitItem())

                repository.setHideContent(false)
                assertEquals(
                    PasscodeSettings(autoLock = AutoLock.Immediately, biometricUnlock = false, hideContent = false),
                    awaitItem(),
                )

                repository.setDeviceCredentialUnlock(true)
                assertEquals(
                    PasscodeSettings(
                        autoLock = AutoLock.Immediately,
                        biometricUnlock = false,
                        deviceCredentialUnlock = true,
                        hideContent = false,
                    ),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `the passcode and the failed attempts survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            repository(dataStore(firstProcess)).apply {
                setPasscode("1234", PasscodeType.Pin)
                checkPasscode("4321")
            }
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val restarted = repository()

            assertEquals(PasscodeSettings(isSet = true, failedAttempts = 1), restarted.settings.first())
            assertEquals(Outcome.Success(true), restarted.checkPasscode("1234"))
        }

    @Test
    fun `settings that cannot be read count as no passcode, and checking or saving fails`() =
        runTest {
            val repository = repository(FailingDataStore())

            assertEquals(PasscodeSettings(), repository.settings.first())
            assertTrue((repository.checkPasscode("1234") as Outcome.Failure).error is DomainError.Unknown)
            assertTrue((repository.setAutoLock(AutoLock.OneHour) as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}
