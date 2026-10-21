package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Adding a contact (`REQUIREMENTS.md` FR-P1) by phone number or by username, as [uiState].
 *
 * - **Fields** are kept in the [SavedStateHandle] as they are typed, so they survive process death; names at most
 *   Telegram's 64 characters. The phone number field starts with the calling code of the user's own number ("+44 "),
 *   read after the screen first showed, and says which country the number belongs to as its digits tell
 *   ([CountryRepository.phoneNumberInfo]), after a short pause in typing.
 * - **Adding by phone number** needs a first name and the number with its country code (7 to 15 digits, E.164); it is
 *   sent as `+` and its digits. A number no one uses Telegram with turns the screen into an invitation
 *   ([NewContactUiState.notOnTelegram]), shared through the share sheet ([NewContactEffect.ShareInvite]).
 * - **Adding by username** takes `@name` or a `t.me/name` link; without names the contact keeps the user's own.
 * - **Errors**: what concerns a field shows at it ([ContactFieldError]); `FLOOD_WAIT` counts down while "Add" waits;
 *   anything else is a snackbar. Once added, the screen closes ([NewContactEffect.Added]).
 */
@Suppress("TooManyFunctions") // A handler per field and action of the form, and the requests they run through.
@HiltViewModel
class NewContactViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val contactRepository: ContactRepository,
        private val countryRepository: CountryRepository,
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(
                NewContactUiState(
                    method =
                        savedStateHandle.get<String>(KEY_METHOD)?.let { name ->
                            ContactMethod.entries.firstOrNull { it.name == name }
                        } ?: ContactMethod.PhoneNumber,
                    firstName = savedStateHandle[KEY_FIRST_NAME] ?: "",
                    lastName = savedStateHandle[KEY_LAST_NAME] ?: "",
                    phoneNumber = savedStateHandle[KEY_PHONE_NUMBER] ?: "",
                    username = savedStateHandle[KEY_USERNAME] ?: "",
                ),
            )
        val uiState: StateFlow<NewContactUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<NewContactEffect>(Channel.BUFFERED)
        val effects: Flow<NewContactEffect> = effectChannel.receiveAsFlow()

        private var requestJob: Job? = null
        private var floodWaitJob: Job? = null
        private var countryJob: Job? = null

        init {
            // Dispatched: after the screen's first frame (docs/PERFORMANCE.md §4).
            viewModelScope.launch {
                if (uiState.value.phoneNumber.isEmpty()) prefillCallingCode()
                lookUpCountry(uiState.value.phoneNumber, pause = false)
            }
        }

        fun onEvent(event: NewContactEvent) {
            when (event) {
                is NewContactEvent.MethodChanged -> edit(KEY_METHOD, event.method.name) { copy(method = event.method) }
                is NewContactEvent.FirstNameChanged -> editName(KEY_FIRST_NAME, event.value) { copy(firstName = it) }
                is NewContactEvent.LastNameChanged -> editName(KEY_LAST_NAME, event.value) { copy(lastName = it) }
                is NewContactEvent.PhoneNumberChanged -> editPhoneNumber(event.value)
                is NewContactEvent.UsernameChanged -> edit(KEY_USERNAME, event.value) { copy(username = event.value) }
                NewContactEvent.Submit -> if (uiState.value.canSubmit) submit()
                NewContactEvent.Invite -> invite()
                NewContactEvent.ChangeNumber -> mutableUiState.update { it.copy(notOnTelegram = false) }
            }
        }

        private inline fun edit(
            key: String,
            value: String,
            transform: NewContactUiState.() -> NewContactUiState,
        ) {
            if (uiState.value.isWorking) return
            savedStateHandle[key] = value
            mutableUiState.update { it.transform().copy(fieldError = null) }
        }

        private inline fun editName(
            key: String,
            value: String,
            crossinline transform: NewContactUiState.(String) -> NewContactUiState,
        ) {
            val name = value.limitedToCodePoints(ProfileLimits.NAME_LENGTH_MAX)
            edit(key, name) { transform(name) }
        }

        private fun editPhoneNumber(value: String) {
            // Digits and what people type between them; the rest (letters pasted along) is left out.
            val number = value.filter { it.isDigit() || it in PHONE_CHARACTERS }
            edit(KEY_PHONE_NUMBER, number) { copy(phoneNumber = number) }
            countryJob?.cancel()
            countryJob = viewModelScope.launch { lookUpCountry(number, pause = true) }
        }

        private fun submit() {
            val state = uiState.value
            when (state.method) {
                ContactMethod.PhoneNumber -> submitPhoneNumber(state)
                ContactMethod.Username -> submitUsername(state)
            }
        }

        private fun submitPhoneNumber(state: NewContactUiState) {
            val digits = state.phoneNumber.filter(Char::isDigit)
            if (digits.length !in E164_DIGITS) {
                reject(ContactFieldError(ContactField.PhoneNumber))
                return
            }
            request(
                action = { contactRepository.addByPhoneNumber("+$digits", state.firstName, state.lastName) },
                onSuccess = { result ->
                    when (result) {
                        is AddContactResult.Added -> effectChannel.trySend(NewContactEffect.Added(result.userId))
                        AddContactResult.NotOnTelegram -> mutableUiState.update { it.copy(notOnTelegram = true) }
                    }
                },
            )
        }

        private fun submitUsername(state: NewContactUiState) {
            val username = state.username.toUsername()
            if (username.isEmpty()) {
                reject(ContactFieldError(ContactField.Username, DomainError.NotFound))
                return
            }
            request(
                action = { contactRepository.addByUsername(username, state.firstName, state.lastName) },
                onSuccess = { userId -> effectChannel.trySend(NewContactEffect.Added(userId)) },
            )
        }

        private fun <T> request(
            action: suspend () -> Outcome<T>,
            onSuccess: (T) -> Unit,
        ) {
            requestJob?.cancel()
            mutableUiState.update { it.copy(isWorking = true, fieldError = null) }
            requestJob =
                viewModelScope.launch {
                    val outcome = action()
                    mutableUiState.update { it.copy(isWorking = false) }
                    when (outcome) {
                        is Outcome.Success -> onSuccess(outcome.value)
                        is Outcome.Failure -> fail(outcome.error)
                    }
                }
        }

        private fun fail(error: DomainError) {
            val field =
                when (uiState.value.method) {
                    ContactMethod.PhoneNumber -> ContactField.PhoneNumber.takeIf { error in PHONE_ERRORS }
                    ContactMethod.Username -> ContactField.Username.takeIf { error in USERNAME_ERRORS }
                }
            when {
                error is DomainError.FloodWait -> startFloodWait(error.seconds)
                error in NAME_ERRORS -> reject(ContactFieldError(ContactField.FirstName, error))
                field != null -> reject(ContactFieldError(field, error))
                error == DomainError.Silent -> Unit
                else -> effectChannel.trySend(NewContactEffect.ShowError(error))
            }
        }

        private fun reject(error: ContactFieldError) = mutableUiState.update { it.copy(fieldError = error) }

        private fun startFloodWait(seconds: Long) {
            floodWaitJob?.cancel()
            floodWaitJob =
                viewModelScope.launch {
                    for (left in seconds downTo 1) {
                        mutableUiState.update { it.copy(floodWaitSeconds = left, floodWaitTotalSeconds = seconds) }
                        delay(1.seconds)
                    }
                    mutableUiState.update { it.copy(floodWaitSeconds = null) }
                }
        }

        private fun invite() {
            viewModelScope.launch { effectChannel.send(NewContactEffect.ShareInvite(contactRepository.inviteLink())) }
        }

        /** Starts the phone number with the calling code of the user's own number, as most contacts share it. */
        private suspend fun prefillCallingCode() {
            val code = myCallingCode()
            // Unless the user started typing meanwhile.
            if (code == null || uiState.value.phoneNumber.isNotEmpty()) return
            val prefix = "+$code "
            savedStateHandle[KEY_PHONE_NUMBER] = prefix
            mutableUiState.update { it.copy(phoneNumber = prefix) }
        }

        /** The calling code of the user's own phone number, if Telegram shows it and TDLib knows its country. */
        private suspend fun myCallingCode(): String? {
            val myNumber =
                userRepository.myUserId.value
                    ?.let { userRepository.getUser(it).getOrNull() }
                    ?.phoneNumber
                    ?.ifBlank { null }
            return myNumber?.let {
                countryRepository
                    .phoneNumberInfo(it)
                    .getOrNull()
                    ?.callingCode
                    ?.ifBlank { null }
            }
        }

        /** Shows the country of [number], after a pause in typing if [pause]; none for too few digits. */
        private suspend fun lookUpCountry(
            number: String,
            pause: Boolean,
        ) {
            val digits = number.filter(Char::isDigit)
            if (digits.isEmpty()) {
                mutableUiState.update { it.copy(country = null) }
                return
            }
            if (pause) delay(COUNTRY_DELAY_MILLIS)
            val country = countryRepository.phoneNumberInfo(digits).getOrNull()?.country
            mutableUiState.update {
                it.copy(country = country?.let { found -> "${found.flagEmoji} ${found.name}".trim() })
            }
        }

        private companion object {
            const val KEY_METHOD = "newContact.method"
            const val KEY_FIRST_NAME = "newContact.firstName"
            const val KEY_LAST_NAME = "newContact.lastName"
            const val KEY_PHONE_NUMBER = "newContact.phoneNumber"
            const val KEY_USERNAME = "newContact.username"

            /** What people type between the digits of a phone number, kept as typed. */
            const val PHONE_CHARACTERS = "+ -()"

            /** E.164: at most 15 digits with the country code; fewer than 7 is no number anywhere. */
            val E164_DIGITS = 7..15

            const val COUNTRY_DELAY_MILLIS = 300L

            val NAME_ERRORS = setOf(DomainError.FirstNameInvalid, DomainError.LastNameInvalid)
            val PHONE_ERRORS = setOf(DomainError.PhoneNumberInvalid, DomainError.InputRejected)
            val USERNAME_ERRORS = setOf(DomainError.NotFound, DomainError.UsernameInvalid)
        }
    }

/** A username from what was typed: without `@`, or the name of a `t.me/name` link. */
internal fun String.toUsername(): String =
    trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removePrefix("t.me/")
        .removePrefix("telegram.me/")
        .removePrefix("@")
        .substringBefore('/')
        .substringBefore('?')
        .trim()

/** At most [max] Unicode characters (code points) of this text, as TDLib counts a name. */
internal fun String.limitedToCodePoints(max: Int): String =
    if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max))
