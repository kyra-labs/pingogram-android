package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContact
import dev.kyralabs.pingogram.core.domain.model.PhoneContacts
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import java.text.Collator
import java.time.Duration
import javax.inject.Inject

/**
 * [ContactSyncRepository] on TDLib's import of the phone's contacts (`docs/adr/0011-contacts-sync.md`), the phone's
 * contacts from [DeviceContactsRepository] and the settings from [ContactSyncSettingsRepository].
 *
 * - **Syncing** sends every name and number with `changeImportedContacts`, which TDLib compares with the list it
 *   imported last (kept in its database): new numbers are imported, removed ones deleted, unchanged ones not sent
 *   again. Its answer gives each contact's user id, 0 for a number not on Telegram. The phone's contacts are read
 *   just before, and the answer kept in memory only ([phoneContacts]); one sync at a time, as TDLib requires.
 * - **When**: on [turnOn], on [syncNow], and on [syncIfDue] (the Contacts tab opening) at most once per process and
 *   not within [MIN_INTERVAL] of the last sync, which the settings keep. Otherwise [syncIfDue] matches the phone's
 *   numbers with those of the Telegram contacts on the device, sending nothing.
 * - **Turning off** sends `clearImportedContacts`, which deletes the imported numbers from Telegram (the contact list
 *   stays as it is), then clears the settings and forgets the phone's contacts.
 * - **The permission** is read again on [refreshPermission]; while it is missing, sync is paused
 *   ([ContactSyncStatus.NeedsPermission]) and nothing is read.
 * - **Logging out** (`authorizationStateLoggingOut`, or `Closed`) clears the settings and what is in memory, so the
 *   next account starts with sync off. It is a [TdlibUpdateCache] for that, subscribed before TDLib starts.
 *
 * The interface's six operations and the steps they share (syncing, sending, matching, forgetting, logging out) work on
 * one state, so the class keeps them together (`TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@AccountScoped
class TdlibContactSyncRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val settingsRepository: ContactSyncSettingsRepository,
        private val deviceContacts: DeviceContactsRepository,
        private val userRepository: UserRepository,
        private val clock: WallClock,
        @ApplicationScope private val scope: CoroutineScope,
    ) : ContactSyncRepository,
        TdlibUpdateCache {
        private val permission = MutableStateFlow(deviceContacts.canRead())
        private val syncing = MutableStateFlow(false)
        private val lastError = MutableStateFlow<DomainError?>(null)
        private val found = MutableStateFlow<PhoneContacts?>(null)
        private val mutex = Mutex()

        /** Whether the contacts synced in this process; [syncIfDue] does not sync again then. */
        @Volatile
        private var syncedInProcess = false

        init {
            // UNDISPATCHED: subscribed before the constructor returns, so no logout is missed.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                client.updates.collect { update -> if (update.logsOut()) onLoggedOut() }
            }
        }

        override val settings: Flow<ContactSyncSettings> = settingsRepository.settings

        override val status: Flow<ContactSyncStatus> =
            combine(settingsRepository.settings, permission, syncing, lastError) { settings, canRead, busy, error ->
                when {
                    !settings.enabled -> ContactSyncStatus.Off
                    !canRead -> ContactSyncStatus.NeedsPermission
                    else -> ContactSyncStatus.On(settings.lastSyncedAt, busy, error)
                }
            }.distinctUntilChanged()

        override val phoneContacts: Flow<PhoneContacts?> =
            combine(status, found) { status, found -> found.takeIf { status is ContactSyncStatus.On } }
                .distinctUntilChanged()

        override fun refreshPermission() {
            val canRead = deviceContacts.canRead()
            permission.value = canRead
            if (!canRead) found.value = null
        }

        override suspend fun turnOn(): Outcome<Unit> {
            refreshPermission()
            if (!permission.value) return Outcome.Failure(DomainError.Unauthorized)
            return when (val saved = settingsRepository.setEnabled(true)) {
                is Outcome.Success -> sync()
                is Outcome.Failure -> saved
            }
        }

        override suspend fun turnOff(): Outcome<Unit> =
            mutex.withLock {
                when (val cleared = client.request(TdApi.ClearImportedContacts())) {
                    is Outcome.Success -> {
                        forget()
                        settingsRepository.setEnabled(false)
                        settingsRepository.setLastSyncedAt(null)
                        // Turned off on purpose: the Contacts tab does not offer it again.
                        settingsRepository.setPromptDismissed(true)
                    }

                    is Outcome.Failure -> {
                        cleared
                    }
                }
            }

        override suspend fun syncIfDue(): Outcome<Unit> {
            refreshPermission()
            val settings = settingsRepository.settings.first()
            val lastSync = settings.lastSyncedAt
            val due =
                !syncedInProcess && (lastSync == null || Duration.between(lastSync, clock.now()) >= MIN_INTERVAL)
            return when {
                !settings.enabled || !permission.value -> Outcome.Success(Unit)
                due -> sync()
                found.value == null -> matchOnDevice()
                else -> Outcome.Success(Unit)
            }
        }

        override suspend fun syncNow(): Outcome<Unit> {
            refreshPermission()
            val enabled = settingsRepository.settings.first().enabled
            return when {
                !enabled -> Outcome.Success(Unit)
                !permission.value -> Outcome.Failure(DomainError.Unauthorized)
                else -> sync()
            }
        }

        override suspend fun dismissPrompt(): Outcome<Unit> = settingsRepository.setPromptDismissed(true)

        /** Reads the phone's contacts and sends them to Telegram; what it finds goes to [found]. */
        private suspend fun sync(): Outcome<Unit> =
            mutex.withLock {
                syncing.value = true
                val outcome =
                    when (val read = deviceContacts.read()) {
                        is Outcome.Success -> send(read.value)
                        is Outcome.Failure -> read
                    }
                syncing.value = false
                lastError.value = (outcome as? Outcome.Failure)?.error
                if (outcome is Outcome.Failure && outcome.error == DomainError.Unauthorized) refreshPermission()
                outcome
            }

        private suspend fun send(contacts: List<PhoneContact>): Outcome<Unit> {
            val imported = contacts.map { TdApi.ImportedContact(it.phoneNumber, it.name, "", emptyText()) }
            val answer = client.request(TdApi.ChangeImportedContacts(imported.toTypedArray()))
            if (answer is Outcome.Success) {
                val userIds = answer.value.userIds
                found.value = phoneContactsOf(contacts) { index, _ -> userIds.getOrElse(index) { 0L } }
                syncedInProcess = true
                settingsRepository.setLastSyncedAt(clock.now())
            }
            return answer.map {}
        }

        /** Matches the phone's numbers with the Telegram contacts the device knows, without asking Telegram. */
        private suspend fun matchOnDevice(): Outcome<Unit> =
            when (val read = deviceContacts.read()) {
                is Outcome.Success -> {
                    val byNumber = contactsByNumber(userRepository.users.first().values)
                    found.value = phoneContactsOf(read.value) { _, contact -> byNumber[contact.digits()] ?: 0L }
                    Outcome.Success(Unit)
                }

                is Outcome.Failure -> {
                    read
                }
            }

        private fun forget() {
            found.value = null
            lastError.value = null
            syncedInProcess = false
        }

        private fun onLoggedOut() {
            forget()
            scope.launch { settingsRepository.clear() }
        }

        private companion object {
            /** The least time between two syncs when the Contacts tab opens; "Sync now" syncs whenever asked. */
            val MIN_INTERVAL: Duration = Duration.ofHours(6)
        }
    }

/** [contacts] split by the user id [userIdOf] finds for each (0 for none), the others sorted by name. */
private inline fun phoneContactsOf(
    contacts: List<PhoneContact>,
    userIdOf: (index: Int, contact: PhoneContact) -> Long,
): PhoneContacts {
    val onTelegram = LinkedHashMap<Long, PhoneContact>()
    val notOnTelegram = mutableListOf<PhoneContact>()
    contacts.forEachIndexed { index, contact ->
        val userId = userIdOf(index, contact)
        if (userId == 0L) notOnTelegram += contact else onTelegram.putIfAbsent(userId, contact)
    }
    val collator = Collator.getInstance()
    return PhoneContacts(
        onTelegram = onTelegram.toImmutableMap(),
        notOnTelegram = notOnTelegram.sortedWith { a, b -> collator.compare(a.name, b.name) }.toImmutableList(),
    )
}

/** The Telegram contacts among [users] whose phone number the device knows, by its digits. */
private fun contactsByNumber(users: Collection<User>): Map<String, Long> =
    users
        .filter { it.isContact && it.phoneNumber.isNotBlank() }
        .associate { it.phoneNumber.filter(Char::isDigit) to it.id }

private fun emptyText() = TdApi.FormattedText("", emptyArray())

private fun PhoneContact.digits(): String = phoneNumber.filter(Char::isDigit)

private fun TdApi.Update.logsOut(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        authorizationState.let { it is TdApi.AuthorizationStateLoggingOut || it is TdApi.AuthorizationStateClosed }
