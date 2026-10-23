package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncController
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The Contacts tab (`REQUIREMENTS.md` FR-P1) as [uiState]. Events come in through [onEvent]; opening a chat, the
 * invitation's share sheet and snackbars go out through [effects].
 *
 * - **Contacts** come from [ContactRepository.contacts], read once the screen collects [uiState] (not in the
 *   constructor, `docs/PERFORMANCE.md` §4), and follow TDLib's changes from then on. A failure shows its error until
 *   [ContactsEvent.Retry]; nothing is retried by itself. They stay while the screen collects, and 5 s after, so a
 *   rotation does not read them again.
 * - **Rows** are mapped on [dispatcher], with the minute ([WallClock.minutes]) for online dots and "last seen", into
 *   alphabetical sections of the user's language ([contactSections]); long lists get the fast-scroll index.
 * - **Search**: the query (kept in the [SavedStateHandle], so it survives process death) filters the contacts as the
 *   user types ([matches]); after a short pause TDLib's own search adds what it matches too
 *   ([ContactRepository.search], which finds names in other scripts), for that query only.
 * - **Actions**: a contact opens the private chat with them; deleting asks first (the question kept in the saved
 *   state), then says it is done; "Invite friends" shares Telegram's download link.
 *
 * - **Contacts sync** (`docs/adr/0011-contacts-sync.md`): its offer, its paused state or its status row above the
 *   contacts ([ContactSyncUi]); the contacts it found on the phone marked as such ([ContactItem.fromPhone]); the
 *   phone's contacts not on Telegram under "Invite to Telegram" ([InviteItem]), each invited by a message. Each time
 *   the tab shows ([ContactsEvent.Started]) it reads the permission again and syncs if due
 *   ([ContactSyncRepository.syncIfDue]). Turning it on and off goes through [contactSync], as in Settings.
 *
 * It takes the repositories, plus the saved state, clock, time patterns, dispatcher and language that tests replace,
 * hence `LongParameterList` is suppressed; and a handler per action of the tab, hence `TooManyFunctions`.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val contactSyncRepository: ContactSyncRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        contactRepository: ContactRepository,
        contactSyncRepository: ContactSyncRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle,
        contactRepository,
        contactSyncRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Turning contacts sync on and off, with its dialogs and Android's permission request ([ContactSyncHost]). */
    val contactSync = ContactSyncController(contactSyncRepository, viewModelScope)

    /** Changes on every retry, which lists the contacts again. */
    private val attempt = MutableStateFlow(0)

    private var statusFormatter: ContactStatusFormatter? = null

    private val effectChannel = Channel<ContactsEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a chat, sharing an invitation, and snackbars. */
    val effects: Flow<ContactsEffect> = effectChannel.receiveAsFlow()

    /** The contacts: null while they are listed, then what listing them gave, and every change after. */
    private val contacts: Flow<Outcome<ImmutableList<User>>?> =
        attempt.flatMapLatest {
            flow {
                emit(null)
                emitAll(contactRepository.contacts())
            }
        }

    private val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** What TDLib's search matched, for the query it searched; nothing for a blank one. */
    private val searchMatches: Flow<SearchMatches> =
        query.transformLatest { query ->
            emit(SearchMatches(query, emptySet()))
            if (query.isNotBlank()) {
                // Typing on cancels the wait, so TDLib is asked once the user pauses.
                delay(SEARCH_DELAY_MILLIS)
                emit(
                    SearchMatches(
                        query,
                        contactRepository
                            .search(query)
                            .getOrNull()
                            .orEmpty()
                            .toSet(),
                    ),
                )
            }
        }

    private val deleteDialogUserId: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_DELETE, NO_DIALOG)

    private val syncSources: Flow<SyncSources> =
        combine(
            contactSyncRepository.status,
            contactSyncRepository.settings,
            contactSyncRepository.phoneContacts,
            ::SyncSources,
        )

    private val dialogAndSync = combine(deleteDialogUserId, syncSources, ::Pair)

    val uiState: StateFlow<ContactsUiState> =
        combine(contacts, query, searchMatches, clock.minutes, dialogAndSync) { contacts, query, matches, now, rest ->
            toUiState(contacts, query, matches, now, rest.first, rest.second)
        }.flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ContactsUiState(query = query.value),
            )

    fun onEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.QueryChanged -> savedStateHandle[KEY_QUERY] = event.query
            is ContactsEvent.OpenChat -> openChat(event.userId)
            is ContactsEvent.Delete -> savedStateHandle[KEY_DELETE] = event.userId
            ContactsEvent.ConfirmDelete -> confirmDelete()
            ContactsEvent.DismissDialog -> savedStateHandle[KEY_DELETE] = NO_DIALOG
            ContactsEvent.Invite -> invite()
            ContactsEvent.Retry -> if (uiState.value.content is ContactsContent.Error) attempt.update { it + 1 }
            ContactsEvent.Started -> started()
            ContactsEvent.DismissSyncOffer -> viewModelScope.launch { contactSyncRepository.dismissPrompt() }
            is ContactsEvent.InvitePhoneContact -> invitePhoneContact(event.phoneNumber)
        }
    }

    private fun started() {
        contactSyncRepository.refreshPermission()
        // Its outcome shows in the sync row; nothing more to say here.
        viewModelScope.launch { contactSyncRepository.syncIfDue() }
    }

    private fun invitePhoneContact(phoneNumber: String) {
        viewModelScope.launch {
            effectChannel.send(ContactsEffect.InviteByMessage(phoneNumber, contactRepository.inviteLink()))
        }
    }

    private fun openChat(userId: Long) {
        viewModelScope.launch {
            when (val chat = contactRepository.privateChatId(userId)) {
                is Outcome.Success -> effectChannel.send(ContactsEffect.OpenChat(chat.value))
                is Outcome.Failure -> effectChannel.send(ContactsEffect.ShowError(chat.error))
            }
        }
    }

    private fun confirmDelete() {
        val dialog = uiState.value.deleteDialog ?: return
        savedStateHandle[KEY_DELETE] = NO_DIALOG
        viewModelScope.launch {
            when (val deleted = contactRepository.delete(dialog.userId)) {
                is Outcome.Success -> effectChannel.send(ContactsEffect.Deleted(dialog.name))
                is Outcome.Failure -> effectChannel.send(ContactsEffect.ShowError(deleted.error))
            }
        }
    }

    private fun invite() {
        viewModelScope.launch { effectChannel.send(ContactsEffect.ShareInvite(contactRepository.inviteLink())) }
    }

    /** Runs on the dispatcher: maps [contacts] to the list, filtered by [query], as of [now], with [sync]. */
    @Suppress("LongParameterList") // What combine hands it.
    private fun toUiState(
        contacts: Outcome<ImmutableList<User>>?,
        query: String,
        matches: SearchMatches,
        now: Instant,
        deleteUserId: Long,
        sync: SyncSources,
    ): ContactsUiState {
        val users = (contacts as? Outcome.Success)?.value
        val deleteDialog =
            users?.firstOrNull { it.id == deleteUserId }?.let { DeleteContactDialog(it.id, it.contactName()) }
        val syncUi = sync.toUi(statusFormatter(), now)
        val fromPhone =
            sync.phoneContacts
                ?.onTelegram
                ?.keys
                .orEmpty()
        val invites = sync.phoneContacts?.toInvites().orEmpty()
        val content =
            when {
                contacts == null -> ContactsContent.Loading
                contacts is Outcome.Failure -> ContactsContent.Error(contacts.error)
                users.isNullOrEmpty() && invites.isEmpty() && syncUi == ContactSyncUi.Hidden -> ContactsContent.Empty
                query.isBlank() -> contactsList(users.orEmpty(), now, fromPhone, invites)
                else -> results(users.orEmpty(), query, matches, now, fromPhone)
            }
        return ContactsUiState(content = content, query = query, deleteDialog = deleteDialog, sync = syncUi)
    }

    private fun contactsList(
        users: List<User>,
        now: Instant,
        fromPhone: Set<Long>,
        invites: List<InviteItem>,
    ): ContactsContent.Contacts {
        val formatter = statusFormatter()
        val sections = contactSections(users.map { it.toItem(formatter, now, fromPhone) }, locale())
        val showsIndex = users.size >= INDEX_MIN_CONTACTS && sections.size >= INDEX_MIN_SECTIONS
        return ContactsContent.Contacts(sections, showsIndex, invites.toImmutableList())
    }

    private fun results(
        users: List<User>,
        query: String,
        matches: SearchMatches,
        now: Instant,
        fromPhone: Set<Long>,
    ): ContactsContent.Results {
        val formatter = statusFormatter()
        val locale = locale()
        val tdlibMatches = if (matches.query == query) matches.userIds else emptySet()
        val found = users.filter { it.id in tdlibMatches || matches(it, query, locale) }
        return ContactsContent.Results(found.map { it.toItem(formatter, now, fromPhone) }.toImmutableList())
    }

    private fun User.toItem(
        formatter: ContactStatusFormatter,
        now: Instant,
        fromPhone: Set<Long>,
    ) = ContactItem(
        userId = id,
        name = contactName(),
        photo = profilePhoto,
        isOnline = type != UserType.Bot && status.isOnlineAt(now.epochSecond),
        status = formatter.status(this, now),
        fromPhone = id in fromPhone,
    )

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun statusFormatter(): ContactStatusFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return statusFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ContactStatusFormatter(zone, locale, is24HourClock, timePatterns).also { statusFormatter = it }
    }

    /** The contacts TDLib's search matched for [query]. */
    private class SearchMatches(
        val query: String,
        val userIds: Set<Long>,
    )

    internal companion object {
        const val KEY_QUERY = "contacts.query"
        const val KEY_DELETE = "contacts.delete"
        const val NO_DIALOG = 0L

        /** How long typing pauses before TDLib is asked: the local filter shows matches meanwhile. */
        const val SEARCH_DELAY_MILLIS = 300L

        /** The fewest contacts, and letters, for which the list shows its fast-scroll index. */
        const val INDEX_MIN_CONTACTS = 30
        const val INDEX_MIN_SECTIONS = 3

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
