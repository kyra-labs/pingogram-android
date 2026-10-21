package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContact
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [TdlibContactSyncRepository] on a [FakeTdlibClient], a fake address book and fake settings: off by default, turning
 * on (with and without the permission), what changeImportedContacts sends and finds, when the Contacts tab's opening
 * syncs, "Sync now", errors, the permission taken back, turning off, and logging out.
 */
class TdlibContactSyncRepositoryTest {
    private val client = FakeTdlibClient()
    private val device = FakeDeviceContacts()
    private val settings = FakeSyncSettings()
    private val clock = TestClock(NOW)

    private fun TestScope.repository(): TdlibContactSyncRepository {
        val users = TdlibUserRepository(client, backgroundScope)
        return TdlibContactSyncRepository(client, settings, device, users, clock, backgroundScope)
    }

    /** TDLib finds the numbers of [onTelegram] (number to user id) on Telegram, and none of the others. */
    private fun answerImport(onTelegram: Map<String, Long> = mapOf(ADA.phoneNumber to ADA_ID)) {
        client.on(TdApi.ChangeImportedContacts::class) { request ->
            val ids = request.contacts.map { onTelegram[it.phoneNumber] ?: 0L }.toLongArray()
            TdResult.Success(TdApi.ImportedContacts(ids, IntArray(ids.size)))
        }
    }

    private fun imports() = client.sentOf<TdApi.ChangeImportedContacts>()

    // Off

    @Test
    fun `sync is off by default and reads nothing`() =
        runTest {
            val repository = repository()

            assertEquals(ContactSyncStatus.Off, repository.status.first())
            assertEquals(Outcome.Success(Unit), repository.syncIfDue())
            assertEquals(Outcome.Success(Unit), repository.syncNow())

            assertNull(repository.phoneContacts.first())
            assertEquals(0, device.reads)
            assertTrue(client.sent.isEmpty())
        }

    // Turning on

    @Test
    fun `turning on without the permission fails and stays off`() =
        runTest {
            device.canRead = false
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.turnOn())

            assertEquals(ContactSyncStatus.Off, repository.status.first())
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `turning on sends every name and number, and splits the phone's contacts by who is on Telegram`() =
        runTest {
            answerImport()
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.turnOn())

            val sent = imports().single().contacts
            assertEquals(listOf(ADA.phoneNumber, BOB.phoneNumber), sent.map { it.phoneNumber })
            assertEquals(listOf("Ada Lovelace", "Bob"), sent.map { it.firstName })
            assertEquals(listOf("", ""), sent.map { it.lastName })
            val found = repository.phoneContacts.first()!!
            assertEquals(mapOf(ADA_ID to ADA), found.onTelegram)
            assertEquals(listOf(BOB), found.notOnTelegram)
            assertEquals(ContactSyncStatus.On(lastSyncedAt = NOW), repository.status.first())
            assertTrue(settings.value.enabled)
        }

    @Test
    fun `a failed sync shows its error and keeps sync on`() =
        runTest {
            val flood = TdResult.Failure(TdError(429, "Too Many Requests: retry after 9"))
            client.on(TdApi.ChangeImportedContacts::class, flood)
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.FloodWait(9)), repository.turnOn())

            assertEquals(
                ContactSyncStatus.On(lastSyncedAt = null, error = DomainError.FloodWait(9)),
                repository.status.first(),
            )
        }

    // When it syncs

    @Test
    fun `opening the Contacts tab syncs at most once per process`() =
        runTest {
            answerImport()
            settings.value = ContactSyncSettings(enabled = true)
            val repository = repository()

            repository.syncIfDue()
            repository.syncIfDue()

            assertEquals(1, imports().size)
        }

    @Test
    fun `within a while of the last sync, opening it only matches numbers on the device`() =
        runTest {
            answerImport()
            settings.value = ContactSyncSettings(enabled = true, lastSyncedAt = NOW.minus(Duration.ofHours(1)))
            val repository = repository()
            client.emit(updateUser(user(id = ADA_ID, phoneNumber = "447911123456", isContact = true)))

            assertEquals(Outcome.Success(Unit), repository.syncIfDue())

            assertTrue(imports().isEmpty())
            val found = repository.phoneContacts.first()!!
            assertEquals(setOf(ADA_ID), found.onTelegram.keys)
            assertEquals(listOf(BOB), found.notOnTelegram)
        }

    @Test
    fun `after a while, opening it syncs again`() =
        runTest {
            answerImport()
            settings.value = ContactSyncSettings(enabled = true, lastSyncedAt = NOW.minus(Duration.ofHours(7)))
            val repository = repository()

            repository.syncIfDue()

            assertEquals(1, imports().size)
        }

    @Test
    fun `sync now syncs whenever asked`() =
        runTest {
            answerImport()
            settings.value = ContactSyncSettings(enabled = true)
            val repository = repository()

            repository.syncNow()
            clock.now = NOW.plusSeconds(60)
            repository.syncNow()

            assertEquals(2, imports().size)
            assertEquals(NOW.plusSeconds(60), settings.value.lastSyncedAt)
        }

    // The permission

    @Test
    fun `a permission taken back pauses sync and forgets the phone's contacts`() =
        runTest {
            answerImport()
            val repository = repository()
            repository.turnOn()

            device.canRead = false
            repository.refreshPermission()

            assertEquals(ContactSyncStatus.NeedsPermission, repository.status.first())
            assertNull(repository.phoneContacts.first())
            assertEquals(Outcome.Success(Unit), repository.syncIfDue())
            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.syncNow())
            assertEquals(1, imports().size)
        }

    // Turning off

    @Test
    fun `turning off deletes the imported contacts from Telegram and clears what sync kept`() =
        runTest {
            answerImport()
            client.on(TdApi.ClearImportedContacts::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()
            repository.turnOn()

            assertEquals(Outcome.Success(Unit), repository.turnOff())

            assertEquals(1, client.sentOf<TdApi.ClearImportedContacts>().size)
            assertEquals(ContactSyncSettings(promptDismissed = true), settings.value)
            assertEquals(ContactSyncStatus.Off, repository.status.first())
            assertNull(repository.phoneContacts.first())
        }

    @Test
    fun `sync stays on when Telegram cannot be told`() =
        runTest {
            answerImport()
            client.on(TdApi.ClearImportedContacts::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            val repository = repository()
            repository.turnOn()

            assertEquals(Outcome.Failure(DomainError.Network), repository.turnOff())

            assertTrue(settings.value.enabled)
        }

    @Test
    fun `logging out clears sync's settings`() =
        runTest {
            answerImport()
            val repository = repository()
            repository.turnOn()
            repository.dismissPrompt()

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            runCurrent()

            assertEquals(ContactSyncSettings(), settings.value)
            assertNull(repository.phoneContacts.first())
        }

    private class FakeDeviceContacts : DeviceContactsRepository {
        var canRead = true
        var contacts = listOf(ADA, BOB)
        var reads = 0

        override fun canRead(): Boolean = canRead

        override suspend fun read(): Outcome<List<PhoneContact>> {
            reads++
            return if (canRead) Outcome.Success(contacts) else Outcome.Failure(DomainError.Unauthorized)
        }
    }

    private class FakeSyncSettings : ContactSyncSettingsRepository {
        private val state = MutableStateFlow(ContactSyncSettings())
        var value: ContactSyncSettings
            get() = state.value
            set(value) {
                state.value = value
            }

        override val settings: Flow<ContactSyncSettings> = state

        override suspend fun setEnabled(enabled: Boolean) = save { copy(enabled = enabled) }

        override suspend fun setLastSyncedAt(time: Instant?) = save { copy(lastSyncedAt = time) }

        override suspend fun setPromptDismissed(dismissed: Boolean) = save { copy(promptDismissed = dismissed) }

        override suspend fun clear() = save { ContactSyncSettings() }

        private fun save(change: ContactSyncSettings.() -> ContactSyncSettings): Outcome<Unit> {
            state.update(change)
            return Outcome.Success(Unit)
        }
    }

    private class TestClock(
        var now: Instant,
    ) : WallClock {
        override fun now(): Instant = now

        override fun zone(): ZoneId = ZoneOffset.UTC

        override val minutes = MutableStateFlow(now)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-21T12:00:00Z")
        const val ADA_ID = 7L
        val ADA = PhoneContact("1:+447911123456", "Ada Lovelace", "+447911123456")
        val BOB = PhoneContact("2:+15550100", "Bob", "+15550100")
    }
}
