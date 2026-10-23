package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContact
import dev.kyralabs.pingogram.core.domain.model.PhoneContacts
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncDialog
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEffect
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
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
import java.time.Instant
import java.util.Locale

/**
 * [ContactsViewModel] on a [FakeContactRepository] (`REQUIREMENTS.md` FR-P1): the contacts listed once the screen
 * collects them, their sections and rows, changes and the minute, search (local and TDLib's), no contacts, each error
 * and its retry, opening a chat, deleting with its question, inviting, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {
    private val repository = FakeContactRepository()
    private val sync = FakeContactSyncRepository()
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        ContactsViewModel(
            savedStateHandle = handle,
            contactRepository = repository,
            contactSyncRepository = sync,
            clock = clock,
            timePatterns = FakeTimePatterns(),
            dispatcher = StandardTestDispatcher(testScheduler),
            locale = { Locale.US },
        )

    /** A view model whose state is collected, as by the screen. */
    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ContactsViewModel {
        val viewModel = createViewModel(handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: ContactsViewModel,
        event: ContactsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    /** The effects [viewModel] sends from now on, collected as the screen does. */
    private fun TestScope.effectsOf(viewModel: ContactsViewModel): List<ContactsEffect> {
        val effects = mutableListOf<ContactsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun setContacts(vararg contacts: User) {
        repository.contactList.value = contacts.toList().toImmutableList()
    }

    private val ContactsViewModel.sections get() = (uiState.value.content as ContactsContent.Contacts).sections

    private val ContactsViewModel.results get() = (uiState.value.content as ContactsContent.Results).contacts

    // Loading

    @Test
    fun `the contacts are listed once the screen collects them, not before`() =
        runTest {
            val viewModel = createViewModel()
            runCurrent()

            assertEquals(ContactsContent.Loading, viewModel.uiState.value.content)
            assertTrue(repository.calls.isEmpty())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertEquals(listOf("contacts"), repository.calls)
        }

    @Test
    fun `it is loading while the contacts are on their way`() =
        runTest {
            val listed = CompletableDeferred<Unit>()
            repository.beforeList = { listed.await() }
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            assertEquals(ContactsContent.Loading, viewModel.uiState.value.content)
            listed.complete(Unit)
            runCurrent()
            assertEquals(listOf("A"), viewModel.sections.map { it.letter })
        }

    // Content

    @Test
    fun `contacts come in sections of their first letters, with name, online dot and status`() =
        runTest {
            setContacts(
                contact(2, "Ada", "Lovelace", status = UserStatus.Online(NOW.epochSecond + 60)),
                contact(3, "alan", status = UserStatus.Offline(NOW.epochSecond - 5 * 60)),
                contact(4, "Émile", status = UserStatus.Recently),
                contact(5, "Grace", status = UserStatus.Offline(NOW.epochSecond - 2 * 3_600)),
                contact(6, "42 Club"),
            )

            val sections = viewModel().sections

            assertEquals(listOf("A", "E", "G", "#"), sections.map { it.letter })
            assertEquals(listOf(2L, 3L), sections[0].contacts.map { it.userId })
            val ada = sections[0].contacts[0]
            assertEquals("Ada Lovelace", ada.name)
            assertTrue(ada.isOnline)
            assertEquals(UiText.resource(R.string.contacts_status_online), ada.status)
            val alan = sections[0].contacts[1]
            assertFalse(alan.isOnline)
            assertEquals(UiText.plural(R.plurals.contacts_status_minutes, 5, 5), alan.status)
            assertEquals(UiText.resource(R.string.contacts_status_recently), sections[1].contacts[0].status)
            assertEquals(UiText.resource(R.string.contacts_status_today, "10:00 AM"), sections[2].contacts[0].status)
            assertEquals(UiText.resource(R.string.contacts_status_long_ago), sections[3].contacts[0].status)
        }

    @Test
    fun `a bot says so and never shows online`() =
        runTest {
            setContacts(contact(2, "Helper", status = UserStatus.Online(NOW.epochSecond + 60), type = UserType.Bot))

            val bot =
                viewModel()
                    .sections
                    .single()
                    .contacts
                    .single()

            assertFalse(bot.isOnline)
            assertEquals(UiText.resource(R.string.contacts_status_bot), bot.status)
        }

    @Test
    fun `a long list shows the fast-scroll index, a short one does not`() =
        runTest {
            setContacts(*Array(ContactsViewModel.INDEX_MIN_CONTACTS) { contact(it + 1L, "${'A' + it % 3}name $it") })
            val viewModel = viewModel()
            assertTrue((viewModel.uiState.value.content as ContactsContent.Contacts).showsIndex)

            setContacts(contact(1, "Ada"), contact(2, "Bea"), contact(3, "Cy"))
            runCurrent()
            assertFalse((viewModel.uiState.value.content as ContactsContent.Contacts).showsIndex)
        }

    @Test
    fun `the list follows the contacts as they change`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            setContacts(contact(2, "Ada"), contact(3, "Bea"))
            runCurrent()

            assertEquals(listOf("A", "B"), viewModel.sections.map { it.letter })
        }

    @Test
    fun `statuses follow the minute`() =
        runTest {
            setContacts(contact(2, "Ada", status = UserStatus.Online(NOW.epochSecond + 30)))
            val viewModel = viewModel()
            assertTrue(
                viewModel.sections
                    .single()
                    .contacts
                    .single()
                    .isOnline,
            )

            clock.setTime(NOW.plusSeconds(60))
            runCurrent()

            val ada =
                viewModel.sections
                    .single()
                    .contacts
                    .single()
            assertFalse(ada.isOnline)
            assertEquals(UiText.resource(R.string.contacts_status_just_now), ada.status)
        }

    @Test
    fun `without contacts, and with contacts sync not offered, the list is empty`() =
        runTest {
            sync.settings.value = ContactSyncSettings(promptDismissed = true)

            assertEquals(ContactsContent.Empty, viewModel().uiState.value.content)
        }

    @Test
    fun `without contacts, the offer of contacts sync still shows`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(ContactSyncUi.Offer, state.sync)
            assertTrue((state.content as ContactsContent.Contacts).sections.isEmpty())
        }

    // Errors

    @Test
    fun `a failure to list the contacts shows its error, flood waits, network and unauthorized alike`() =
        runTest {
            listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized).forEach { error ->
                repository.listFailure = error

                assertEquals(ContactsContent.Error(error), viewModel().uiState.value.content)
            }
        }

    @Test
    fun `try again lists the contacts again`() =
        runTest {
            repository.listFailure = DomainError.Network
            val viewModel = viewModel()

            repository.listFailure = null
            setContacts(contact(2, "Ada"))
            send(viewModel, ContactsEvent.Retry)

            assertEquals(listOf("contacts", "contacts"), repository.calls)
            assertEquals(listOf("A"), viewModel.sections.map { it.letter })
        }

    @Test
    fun `try again does nothing while the list is fine`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.Retry)

            assertEquals(listOf("contacts"), repository.calls)
        }

    // Search

    @Test
    fun `typing filters the contacts at once, by words of the name, ignoring case and accents`() =
        runTest {
            setContacts(contact(2, "Ada", "Lovelace"), contact(3, "Émile", "Zola"), contact(4, "Grace", "Hopper"))
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.QueryChanged("emi"))
            assertEquals(listOf(3L), viewModel.results.map { it.userId })

            send(viewModel, ContactsEvent.QueryChanged("hop gr"))
            assertEquals(listOf(4L), viewModel.results.map { it.userId })
            assertEquals("hop gr", viewModel.uiState.value.query)
        }

    @Test
    fun `usernames and phone numbers match too`() =
        runTest {
            setContacts(
                contact(2, "Ada", usernames = listOf("countess")),
                contact(3, "Bea", phoneNumber = "447911123456"),
            )
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.QueryChanged("@count"))
            assertEquals(listOf(2L), viewModel.results.map { it.userId })

            send(viewModel, ContactsEvent.QueryChanged("7911 123"))
            assertEquals(listOf(3L), viewModel.results.map { it.userId })
        }

    @Test
    fun `after a pause TDLib's matches join the results, in the list's order`() =
        runTest {
            setContacts(contact(2, "Ada"), contact(3, "Дмитрий"), contact(4, "Alan"))
            repository.onSearch = { Outcome.Success(persistentListOf(3L)) }
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.QueryChanged("a"))
            assertEquals(listOf(2L, 4L), viewModel.results.map { it.userId })
            assertFalse(repository.calls.contains("search:a"))

            advanceTimeBy(ContactsViewModel.SEARCH_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(listOf(2L, 3L, 4L), viewModel.results.map { it.userId })
            assertTrue(repository.calls.contains("search:a"))
        }

    @Test
    fun `typing on asks TDLib only once the user pauses`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.QueryChanged("a"))
            advanceTimeBy(ContactsViewModel.SEARCH_DELAY_MILLIS / 2)
            send(viewModel, ContactsEvent.QueryChanged("ad"))
            advanceTimeBy(ContactsViewModel.SEARCH_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(listOf("contacts", "search:ad"), repository.calls)
        }

    @Test
    fun `nothing matching shows no results, and clearing the query brings the sections back`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.QueryChanged("zebra"))
            assertTrue(viewModel.results.isEmpty())

            send(viewModel, ContactsEvent.QueryChanged(""))
            assertEquals(listOf("A"), viewModel.sections.map { it.letter })
        }

    @Test
    fun `the query survives process death`() =
        runTest {
            setContacts(contact(2, "Ada"), contact(3, "Bea"))
            val handle = SavedStateHandle()
            send(viewModel(handle), ContactsEvent.QueryChanged("be"))

            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

            assertEquals("be", restored.uiState.value.query)
            assertEquals(listOf(3L), restored.results.map { it.userId })
        }

    // Actions

    @Test
    fun `a contact opens the private chat with them`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.OpenChat(2))

            assertEquals(listOf(ContactsEffect.OpenChat(20)), effects)
        }

    @Test
    fun `a chat that cannot be opened says why`() =
        runTest {
            repository.onPrivateChatId = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.OpenChat(2))

            assertEquals(listOf(ContactsEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `deleting asks first, then deletes and says so`() =
        runTest {
            setContacts(contact(2, "Ada", "Lovelace"))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.Delete(2))
            assertEquals(DeleteContactDialog(2, "Ada Lovelace"), viewModel.uiState.value.deleteDialog)
            assertFalse(repository.calls.any { it.startsWith("delete") })

            send(viewModel, ContactsEvent.ConfirmDelete)

            assertNull(viewModel.uiState.value.deleteDialog)
            assertTrue(repository.calls.contains("delete:2"))
            assertEquals(listOf(ContactsEffect.Deleted("Ada Lovelace")), effects)
        }

    @Test
    fun `not confirming deletes nothing`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.Delete(2))
            send(viewModel, ContactsEvent.DismissDialog)
            send(viewModel, ContactsEvent.ConfirmDelete)

            assertNull(viewModel.uiState.value.deleteDialog)
            assertFalse(repository.calls.any { it.startsWith("delete") })
        }

    @Test
    fun `a failed delete says why`() =
        runTest {
            setContacts(contact(2, "Ada"))
            repository.onDelete = { Outcome.Failure(DomainError.FloodWait(5)) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.Delete(2))
            send(viewModel, ContactsEvent.ConfirmDelete)

            assertEquals(listOf(ContactsEffect.ShowError(DomainError.FloodWait(5))), effects)
        }

    @Test
    fun `the question survives process death, and goes once the contact is deleted elsewhere`() =
        runTest {
            setContacts(contact(2, "Ada"))
            val handle = SavedStateHandle()
            send(viewModel(handle), ContactsEvent.Delete(2))

            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            assertEquals(
                2L,
                restored.uiState.value.deleteDialog
                    ?.userId,
            )

            setContacts()
            runCurrent()
            assertNull(restored.uiState.value.deleteDialog)
        }

    @Test
    fun `inviting shares Telegram's link`() =
        runTest {
            repository.link = "https://t.me/invite"
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.Invite)

            assertEquals(listOf(ContactsEffect.ShareInvite("https://t.me/invite")), effects)
        }

    // Contacts sync

    @Test
    fun `the tab showing reads the permission again and syncs if due`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ContactsEvent.Started)

            assertEquals(listOf("refreshPermission", "syncIfDue"), sync.calls)
        }

    @Test
    fun `sync's offer shows until dismissed`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ContactSyncUi.Offer, viewModel.uiState.value.sync)

            send(viewModel, ContactsEvent.DismissSyncOffer)
            assertEquals(listOf("dismissPrompt"), sync.calls)

            sync.settings.value = ContactSyncSettings(promptDismissed = true)
            runCurrent()
            assertEquals(ContactSyncUi.Hidden, viewModel.uiState.value.sync)
        }

    @Test
    fun `while on, the row says when it last synced, or that it syncs, or what failed`() =
        runTest {
            sync.status.value = ContactSyncStatus.On(lastSyncedAt = NOW.minusSeconds(5 * 60))
            val viewModel = viewModel()
            assertEquals(
                ContactSyncUi.On(
                    isSyncing = false,
                    lastSynced = UiText.plural(R.plurals.contacts_sync_last_minutes, 5, 5),
                    error = null,
                ),
                viewModel.uiState.value.sync,
            )

            sync.status.value = ContactSyncStatus.On(lastSyncedAt = null, isSyncing = true)
            runCurrent()
            assertEquals(
                ContactSyncUi.On(isSyncing = true, lastSynced = null, error = null),
                viewModel.uiState.value.sync,
            )

            sync.status.value = ContactSyncStatus.On(lastSyncedAt = null, error = DomainError.Network)
            runCurrent()
            assertEquals(DomainError.Network, (viewModel.uiState.value.sync as ContactSyncUi.On).error)
        }

    @Test
    fun `a permission taken back shows the paused card`() =
        runTest {
            sync.status.value = ContactSyncStatus.NeedsPermission

            assertEquals(ContactSyncUi.NeedsPermission, viewModel().uiState.value.sync)
        }

    @Test
    fun `contacts found on the phone are marked, and the others are offered an invitation`() =
        runTest {
            setContacts(contact(2, "Ada"), contact(3, "Bea"))
            sync.status.value = ContactSyncStatus.On(lastSyncedAt = NOW)
            sync.phoneContacts.value =
                PhoneContacts(
                    onTelegram = persistentMapOf(2L to PhoneContact("1:+44", "Ada L", "+447911123456")),
                    notOnTelegram = persistentListOf(PhoneContact("2:+1", "Zed", "+15550100")),
                )

            val content = viewModel().uiState.value.content as ContactsContent.Contacts

            val rows = content.sections.flatMap { it.contacts }
            assertEquals(listOf(true, false), rows.map { it.fromPhone })
            assertEquals(listOf(InviteItem("2:+1", "Zed", "+15550100")), content.invites)
        }

    @Test
    fun `inviting a phone's contact opens a message with Telegram's link`() =
        runTest {
            repository.link = "https://t.me/dl"
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ContactsEvent.InvitePhoneContact("+15550100"))

            assertEquals(listOf(ContactsEffect.InviteByMessage("+15550100", "https://t.me/dl")), effects)
        }

    @Test
    fun `turning sync on explains first, then asks Android, then turns it on`() =
        runTest {
            val viewModel = viewModel()
            val effects = syncEffectsOf(viewModel)

            viewModel.contactSync.onEvent(ContactSyncEvent.TurnOn)
            assertEquals(ContactSyncDialog.Rationale, viewModel.contactSync.controls.value.dialog)
            assertTrue(effects.isEmpty())

            viewModel.contactSync.onEvent(ContactSyncEvent.Continue)
            runCurrent()
            assertNull(viewModel.contactSync.controls.value.dialog)
            assertEquals(listOf<ContactSyncEffect>(ContactSyncEffect.RequestPermission), effects)

            viewModel.contactSync.onEvent(ContactSyncEvent.PermissionResult(granted = true, canAskAgain = false))
            runCurrent()
            assertEquals(listOf("turnOn"), sync.calls)
        }

    @Test
    fun `a refusal for good offers Android's settings instead of asking again`() =
        runTest {
            val viewModel = viewModel()
            val effects = syncEffectsOf(viewModel)

            viewModel.contactSync.onEvent(ContactSyncEvent.PermissionResult(granted = false, canAskAgain = false))
            assertTrue(viewModel.contactSync.controls.value.permanentlyDenied)
            viewModel.contactSync.onEvent(ContactSyncEvent.Allow)
            runCurrent()

            assertEquals(listOf<ContactSyncEffect>(ContactSyncEffect.OpenAppSettings), effects)
            assertTrue(sync.calls.isEmpty())
        }

    @Test
    fun `a refusal that Android would ask again leaves sync off`() =
        runTest {
            val viewModel = viewModel()

            viewModel.contactSync.onEvent(ContactSyncEvent.PermissionResult(granted = false, canAskAgain = true))
            runCurrent()

            assertFalse(viewModel.contactSync.controls.value.permanentlyDenied)
            assertTrue(sync.calls.isEmpty())
        }

    @Test
    fun `turning sync off asks first, then says how to take the permission back`() =
        runTest {
            val viewModel = viewModel()
            val effects = syncEffectsOf(viewModel)

            viewModel.contactSync.onEvent(ContactSyncEvent.TurnOff)
            assertEquals(ContactSyncDialog.TurnOff, viewModel.contactSync.controls.value.dialog)
            viewModel.contactSync.onEvent(ContactSyncEvent.ConfirmTurnOff)
            runCurrent()

            assertEquals(listOf("turnOff"), sync.calls)
            assertEquals(listOf<ContactSyncEffect>(ContactSyncEffect.TurnedOff), effects)
        }

    @Test
    fun `sync's failures are snackbars, flood waits, network and unauthorized alike`() =
        runTest {
            val viewModel = viewModel()
            val effects = syncEffectsOf(viewModel)

            listOf(DomainError.FloodWait(5), DomainError.Network, DomainError.Unauthorized).forEach { error ->
                sync.outcome = Outcome.Failure(error)
                viewModel.contactSync.onEvent(ContactSyncEvent.SyncNow)
                runCurrent()
            }

            assertEquals(
                listOf(DomainError.FloodWait(5), DomainError.Network, DomainError.Unauthorized).map {
                    ContactSyncEffect.ShowError(it)
                },
                effects,
            )
        }

    private fun TestScope.syncEffectsOf(viewModel: ContactsViewModel): List<ContactSyncEffect> {
        val effects = mutableListOf<ContactSyncEffect>()
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { viewModel.contactSync.effects.toList(effects) }
        return effects
    }

    private companion object {
        /** Noon on a Wednesday, UTC. */
        val NOW: Instant = Instant.parse("2026-03-18T12:00:00Z")
    }
}
