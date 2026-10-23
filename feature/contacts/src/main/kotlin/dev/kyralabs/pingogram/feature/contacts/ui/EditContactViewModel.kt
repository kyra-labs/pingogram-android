package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.navigation.EditContactRoute
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
 * Editing Telegram user [userId] as a contact, or adding them to the contacts (`REQUIREMENTS.md` FR-P1), as [uiState]:
 * the route's user (`EditContactRoute`), opened from the contacts list or from the user's profile.
 *
 * - **Loading**: [ContactRepository.editableContact], after the screen's first frame; a failure shows its error until
 *   [EditContactEvent.Retry]. The fields start with the contact's names (for a user who is no contact yet, the user's
 *   own), and "Share my phone number" off, offered only where the user cannot see the number yet.
 * - **Fields** are kept in the [SavedStateHandle] as they are typed, and so is the delete question, so they survive
 *   process death; names at most Telegram's 64 characters.
 * - **Saving** needs a first name ([ContactRepository.save]); **deleting** asks first ([ContactRepository.delete]).
 *   Either closes the screen when done ([EditContactEffect.Done]). Wrong names show at the field; `FLOOD_WAIT` counts
 *   down while "Save" waits; anything else is a snackbar.
 */
@Suppress("TooManyFunctions") // A handler per field and action of the form, and the requests they run through.
@HiltViewModel
class EditContactViewModel internal constructor(
    private val userId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        contactRepository: ContactRepository,
    ) : this(savedStateHandle.toRoute<EditContactRoute>().userId, savedStateHandle, contactRepository)

    private val mutableUiState = MutableStateFlow<EditContactUiState>(EditContactUiState.Loading)
    val uiState: StateFlow<EditContactUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<EditContactEffect>(Channel.BUFFERED)
    val effects: Flow<EditContactEffect> = effectChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var requestJob: Job? = null
    private var floodWaitJob: Job? = null

    private val content get() = uiState.value as? EditContactUiState.Content

    init {
        load()
    }

    fun onEvent(event: EditContactEvent) {
        when (event) {
            is EditContactEvent.FirstNameChanged -> {
                editName(KEY_FIRST_NAME, event.value) { copy(firstName = it) }
            }

            is EditContactEvent.LastNameChanged -> {
                editName(KEY_LAST_NAME, event.value) { copy(lastName = it) }
            }

            is EditContactEvent.SharePhoneNumberChanged -> {
                setSharePhoneNumber(event.share)
            }

            EditContactEvent.Save -> {
                if (content?.canSave == true) save()
            }

            EditContactEvent.Delete -> {
                if (content?.isContact == true &&
                    content?.isWorking == false
                ) {
                    setDeleteDialog(true)
                }
            }

            EditContactEvent.ConfirmDelete -> {
                delete()
            }

            EditContactEvent.DismissDialog -> {
                setDeleteDialog(false)
            }

            EditContactEvent.Retry -> {
                if (uiState.value is EditContactUiState.Error) load()
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        mutableUiState.value = EditContactUiState.Loading
        // Dispatched: after the screen's first frame (docs/PERFORMANCE.md §4).
        loadJob =
            viewModelScope.launch {
                when (val loaded = contactRepository.editableContact(userId)) {
                    is Outcome.Success -> mutableUiState.value = loaded.value.toContent()
                    is Outcome.Failure -> mutableUiState.value = EditContactUiState.Error(loaded.error)
                }
            }
    }

    /** The screen for [this] contact, with what was typed before process death, if anything, in its fields. */
    private fun EditableContact.toContent(): EditContactUiState.Content =
        EditContactUiState.Content(
            userId = user.id,
            name = user.contactName(),
            photo = user.profilePhoto,
            phoneNumber = user.phoneNumber,
            isContact = user.isContact,
            canSharePhoneNumber = canSharePhoneNumber,
            firstName = savedStateHandle.get<String>(KEY_FIRST_NAME) ?: user.firstName,
            lastName = savedStateHandle.get<String>(KEY_LAST_NAME) ?: user.lastName,
            sharePhoneNumber = canSharePhoneNumber && savedStateHandle.get<Boolean>(KEY_SHARE) == true,
            showsDeleteDialog = user.isContact && savedStateHandle.get<Boolean>(KEY_DELETE) == true,
        )

    private inline fun editName(
        key: String,
        value: String,
        transform: EditContactUiState.Content.(String) -> EditContactUiState.Content,
    ) {
        val content = content ?: return
        if (content.isWorking) return
        val name = value.limitedToCodePoints(ProfileLimits.NAME_LENGTH_MAX)
        savedStateHandle[key] = name
        mutableUiState.value = content.transform(name).copy(fieldError = null)
    }

    private fun setSharePhoneNumber(share: Boolean) {
        val content = content ?: return
        if (content.isWorking || !content.canSharePhoneNumber) return
        savedStateHandle[KEY_SHARE] = share
        mutableUiState.value = content.copy(sharePhoneNumber = share)
    }

    private fun setDeleteDialog(shown: Boolean) {
        val content = content ?: return
        savedStateHandle[KEY_DELETE] = shown
        mutableUiState.value = content.copy(showsDeleteDialog = shown)
    }

    private fun save() {
        val content = content ?: return
        request { contactRepository.save(userId, content.firstName, content.lastName, content.sharePhoneNumber) }
    }

    private fun delete() {
        val content = content ?: return
        if (!content.showsDeleteDialog) return
        setDeleteDialog(false)
        request { contactRepository.delete(userId) }
    }

    private fun request(action: suspend () -> Outcome<Unit>) {
        requestJob?.cancel()
        mutableUiState.update { (it as? EditContactUiState.Content)?.copy(isWorking = true, fieldError = null) ?: it }
        requestJob =
            viewModelScope.launch {
                val outcome = action()
                mutableUiState.update { (it as? EditContactUiState.Content)?.copy(isWorking = false) ?: it }
                when (outcome) {
                    is Outcome.Success -> effectChannel.send(EditContactEffect.Done)
                    is Outcome.Failure -> fail(outcome.error)
                }
            }
    }

    private fun fail(error: DomainError) {
        when (error) {
            is DomainError.FloodWait -> {
                startFloodWait(error.seconds)
            }

            DomainError.FirstNameInvalid, DomainError.LastNameInvalid -> {
                mutableUiState.update {
                    (it as? EditContactUiState.Content)?.copy(
                        fieldError = ContactFieldError(ContactField.FirstName, error),
                    )
                        ?: it
                }
            }

            DomainError.Silent -> {
                // td_api.tl: a 406 is shown nowhere.
            }

            else -> {
                effectChannel.trySend(EditContactEffect.ShowError(error))
            }
        }
    }

    private fun startFloodWait(seconds: Long) {
        floodWaitJob?.cancel()
        floodWaitJob =
            viewModelScope.launch {
                for (left in seconds downTo 1) {
                    mutableUiState.update {
                        (it as? EditContactUiState.Content)?.copy(
                            floodWaitSeconds = left,
                            floodWaitTotalSeconds = seconds,
                        )
                            ?: it
                    }
                    delay(1.seconds)
                }
                mutableUiState.update { (it as? EditContactUiState.Content)?.copy(floodWaitSeconds = null) ?: it }
            }
    }

    private companion object {
        const val KEY_FIRST_NAME = "editContact.firstName"
        const val KEY_LAST_NAME = "editContact.lastName"
        const val KEY_SHARE = "editContact.share"
        const val KEY_DELETE = "editContact.delete"
    }
}
