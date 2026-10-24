package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.usernameProblem
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Edit profile page (`REQUIREMENTS.md` FR-S1, Account): the user's photo, names, username, bio and birthday.
 *
 * - **Fields**: typed text is checked as it comes against Telegram's limits (names up to 64 characters, the first one
 *   required; the bio up to TDLib's `bio_length_max`). A new username is checked for its form at once and, after
 *   [USERNAME_CHECK_DELAY] without typing, with Telegram; a check still running when the username changes again is
 *   cancelled. What was typed survives process death through the [SavedStateHandle], as does an open confirmation.
 * - **Save** sends only what changed: the username first, as it is the likeliest to be refused, then the names, then
 *   the bio, then the birthday. Removing the username asks first. A failure stops there, keeps what was saved so far,
 *   and shows why; a refused username shows below its field too. Once everything is saved the page leaves
 *   ([EditProfileEffect.Saved]).
 * - **Birthday**: picked or removed in its dialog, saved with the rest; who may see it ([PrivacySetting.Birthdate]) is
 *   read once and followed through [PrivacyRepository.changes], so a change on its page shows here on return.
 * - **Back** with unsaved changes asks whether to discard them; while saving it waits.
 * - **Photo**: a picked image is copied into the upload cache, upright, without metadata and scaled down
 *   ([PickedPhotoRepository]), uploaded with its progress, and the copy deleted once TDLib answered. Leaving meanwhile
 *   stops waiting for TDLib, which carries on, so the copy stays until the upload cache's own cleaning. Removing the
 *   photo asks first. Either shows on the avatar as soon as TDLib reports the user changed, here and on Settings.
 *
 * It has a handler for each event of a page with four fields, a photo and three confirmations, hence
 * `TooManyFunctions` is suppressed.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditProfileViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val profileRepository: ProfileRepository,
        userRepository: UserRepository,
        private val photoRepository: PickedPhotoRepository,
        privacyRepository: PrivacyRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
        val uiState: StateFlow<EditProfileUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<EditProfileEffect>(Channel.BUFFERED)
        val effects: Flow<EditProfileEffect> = effectChannel.receiveAsFlow()

        /** The latest avatar, kept for the content shown once the profile loaded. */
        private var account: ProfileAccount? = null

        /** Who may see the birthday, kept as [account] is. */
        private var birthdatePrivacy: PrivacyRules? = null

        private var loadJob: Job? = null
        private var usernameCheckJob: Job? = null

        private val content: EditProfileUiState.Content?
            get() = uiState.value as? EditProfileUiState.Content

        init {
            userRepository.myUserId
                .flatMapLatest { id -> if (id == null) flowOf(null) else userRepository.user(id) }
                .map { user -> user?.toProfileAccount() }
                .distinctUntilChanged()
                .onEach { latest ->
                    account = latest
                    updateContent { it.copy(account = latest) }
                }.launchIn(viewModelScope)
            profileRepository.bioLengthMax
                .onEach { max -> updateContent { it.copy(bioLengthMax = max) } }
                .launchIn(viewModelScope)
            privacyRepository.changes
                .filter { it.setting == PrivacySetting.Birthdate }
                .onEach { showBirthdatePrivacy(it.rules) }
                .launchIn(viewModelScope)
            viewModelScope.launch {
                val rules = privacyRepository.rules(PrivacySetting.Birthdate)
                if (rules is Outcome.Success && birthdatePrivacy == null) showBirthdatePrivacy(rules.value)
            }
            load()
        }

        fun onEvent(event: EditProfileEvent) {
            when (event) {
                is EditProfileEvent.FirstNameChanged -> edit(KEY_FIRST_NAME, event.value) { copy(firstName = it) }

                is EditProfileEvent.LastNameChanged -> edit(KEY_LAST_NAME, event.value) { copy(lastName = it) }

                is EditProfileEvent.UsernameChanged -> editUsername(event.value)

                // Telegram's bio is one paragraph: TDLib turns line breaks into spaces, so they are typed as such.
                is EditProfileEvent.BioChanged -> edit(KEY_BIO, event.value.replace('\n', ' ')) { copy(bio = it) }

                is EditProfileEvent.BirthdateChanged -> editBirthdate(event.birthdate)

                EditProfileEvent.Save -> save()

                is EditProfileEvent.PhotoPicked -> setPhoto(event.uri)

                EditProfileEvent.RemovePhoto -> askToRemovePhoto()

                EditProfileEvent.ConfirmDialog -> confirmDialog()

                EditProfileEvent.DismissDialog -> setDialog(null)

                EditProfileEvent.Back -> back()

                EditProfileEvent.Retry -> if (uiState.value is EditProfileUiState.Error) load()
            }
        }

        private fun load() {
            loadJob?.cancel()
            mutableUiState.value = EditProfileUiState.Loading
            loadJob =
                viewModelScope.launch {
                    when (val loaded = profileRepository.editableProfile()) {
                        is Outcome.Success -> showContent(loaded.value.toFields())
                        is Outcome.Failure -> mutableUiState.value = EditProfileUiState.Error(loaded.error)
                    }
                }
        }

        /** Shows [saved], with what was typed before process death, if anything, in its fields. */
        private fun showContent(saved: ProfileFields) {
            val fields =
                ProfileFields(
                    firstName = savedStateHandle[KEY_FIRST_NAME] ?: saved.firstName,
                    lastName = savedStateHandle[KEY_LAST_NAME] ?: saved.lastName,
                    username = savedStateHandle[KEY_USERNAME] ?: saved.username,
                    bio = savedStateHandle[KEY_BIO] ?: saved.bio,
                    birthdate =
                        if (KEY_BIRTHDATE in savedStateHandle) {
                            savedStateHandle.get<IntArray>(KEY_BIRTHDATE)?.toBirthdate()
                        } else {
                            saved.birthdate
                        },
                )
            val dialog =
                savedStateHandle.get<String>(KEY_DIALOG)?.let { name ->
                    EditProfileDialog.entries.firstOrNull { it.name == name }
                }
            mutableUiState.value =
                EditProfileUiState.Content(
                    account = account,
                    saved = saved,
                    fields = fields,
                    bioLengthMax = profileRepository.bioLengthMax.value,
                    dialog = dialog,
                    birthdatePrivacy = birthdatePrivacy,
                )
            checkUsername(fields.username, afterTyping = false)
        }

        private inline fun edit(
            key: String,
            value: String,
            transform: ProfileFields.(String) -> ProfileFields,
        ) {
            val content = content ?: return
            if (content.isSaving) return
            savedStateHandle[key] = value
            mutableUiState.value = content.copy(fields = content.fields.transform(value))
        }

        private fun editBirthdate(birthdate: Birthdate?) {
            val content = content ?: return
            if (content.isSaving) return
            savedStateHandle[KEY_BIRTHDATE] = birthdate.toIntArray()
            mutableUiState.value = content.copy(fields = content.fields.copy(birthdate = birthdate))
        }

        private fun showBirthdatePrivacy(rules: PrivacyRules) {
            birthdatePrivacy = rules
            updateContent { it.copy(birthdatePrivacy = rules) }
        }

        private fun editUsername(value: String) {
            // The field shows the @ itself; one pasted with the username is dropped.
            val username = value.removePrefix("@")
            if (content?.isSaving != false) return
            edit(KEY_USERNAME, username) { copy(username = it) }
            checkUsername(username, afterTyping = true)
        }

        /**
         * Finds out what to say about [username]: nothing if it is TDLib's, "removed" if empty, its first problem if
         * it cannot be one, else what Telegram says, asked after a pause in typing ([afterTyping]).
         */
        private fun checkUsername(
            username: String,
            afterTyping: Boolean,
        ) {
            usernameCheckJob?.cancel()
            val content = content ?: return
            val status =
                when {
                    username == content.saved.username -> UsernameStatus.Unchanged
                    username.isEmpty() -> UsernameStatus.Removed
                    else -> usernameProblem(username)?.let(UsernameStatus::Problem) ?: UsernameStatus.Checking
                }
            updateContent { it.copy(usernameStatus = status) }
            if (status != UsernameStatus.Checking) return
            usernameCheckJob =
                viewModelScope.launch {
                    if (afterTyping) delay(USERNAME_CHECK_DELAY)
                    val checked =
                        when (val result = profileRepository.checkUsername(username)) {
                            is Outcome.Success -> UsernameStatus.Checked(result.value)
                            is Outcome.Failure -> UsernameStatus.CheckFailed
                        }
                    updateContent { if (it.fields.username == username) it.copy(usernameStatus = checked) else it }
                }
        }

        private fun save() {
            val content = content ?: return
            if (!content.canSave) return
            if (content.usernameChanged && content.fields.username.isEmpty()) {
                setDialog(EditProfileDialog.RemoveUsername)
            } else {
                saveChanges()
            }
        }

        /** Saves what changed, one request after another; see the class documentation. */
        private fun saveChanges() {
            val content = content ?: return
            if (!content.canSave) return
            savedStateHandle[KEY_DIALOG] = null
            mutableUiState.value = content.copy(isSaving = true, dialog = null)
            val fields = content.fields
            val changes =
                listOfNotNull(
                    ProfileChange.Username.takeIf { content.usernameChanged },
                    ProfileChange.Name.takeIf { content.nameChanged },
                    ProfileChange.Bio.takeIf { content.bioChanged },
                    ProfileChange.Birthdate.takeIf { content.birthdateChanged },
                )
            viewModelScope.launch {
                for (change in changes) {
                    val outcome =
                        when (change) {
                            ProfileChange.Username -> {
                                profileRepository.setUsername(fields.username)
                            }

                            ProfileChange.Name -> {
                                profileRepository.setName(
                                    fields.firstName.trim(),
                                    fields.lastName.trim(),
                                )
                            }

                            ProfileChange.Bio -> {
                                profileRepository.setBio(fields.bio.trim())
                            }

                            ProfileChange.Birthdate -> {
                                profileRepository.setBirthdate(fields.birthdate)
                            }
                        }
                    when (outcome) {
                        is Outcome.Success -> {
                            updateContent { it.withSaved(change, fields) }
                        }

                        is Outcome.Failure -> {
                            failSaving(change, outcome.error)
                            return@launch
                        }
                    }
                }
                updateContent { it.copy(isSaving = false) }
                effectChannel.trySend(EditProfileEffect.Saved)
            }
        }

        private fun failSaving(
            change: ProfileChange,
            error: DomainError,
        ) {
            val refusedUsername =
                when (error) {
                    DomainError.UsernameTaken -> UsernameCheck.Taken
                    DomainError.UsernameInvalid -> UsernameCheck.Invalid
                    DomainError.UsernameForSale -> UsernameCheck.ForSale
                    else -> null
                }
            updateContent { content ->
                val status =
                    if (change == ProfileChange.Username && refusedUsername != null) {
                        UsernameStatus.Checked(refusedUsername)
                    } else {
                        content.usernameStatus
                    }
                content.copy(isSaving = false, usernameStatus = status)
            }
            if (error != DomainError.Silent) effectChannel.trySend(EditProfileEffect.ShowError(error))
        }

        private fun back() {
            val content = content
            if (content?.isSaving == true) return
            if (content?.hasChanges == true) {
                setDialog(EditProfileDialog.Discard)
            } else {
                effectChannel.trySend(EditProfileEffect.Leave)
            }
        }

        private fun confirmDialog() {
            when (content?.dialog) {
                EditProfileDialog.Discard -> {
                    setDialog(null)
                    effectChannel.trySend(EditProfileEffect.Leave)
                }

                EditProfileDialog.RemovePhoto -> {
                    removePhoto()
                }

                EditProfileDialog.RemoveUsername -> {
                    saveChanges()
                }

                null -> {
                    return
                }
            }
        }

        private fun askToRemovePhoto() {
            val content = content ?: return
            if (content.account?.photo != null && content.photoTask == PhotoTask.None) {
                setDialog(EditProfileDialog.RemovePhoto)
            }
        }

        private fun removePhoto() {
            setDialog(null)
            val content = content ?: return
            if (content.photoTask != PhotoTask.None) return
            mutableUiState.value = content.copy(photoTask = PhotoTask.Removing)
            viewModelScope.launch {
                val outcome = profileRepository.deleteProfilePhoto()
                finishPhotoTask(outcome)
            }
        }

        private fun setPhoto(uri: String) {
            val content = content ?: return
            if (content.photoTask != PhotoTask.None) return
            mutableUiState.value = content.copy(photoTask = PhotoTask.Preparing)
            viewModelScope.launch {
                val path =
                    when (val prepared = photoRepository.preparePhoto(uri)) {
                        is Outcome.Success -> prepared.value
                        is Outcome.Failure -> return@launch finishPhotoTask(prepared)
                    }
                updateContent { it.copy(photoTask = PhotoTask.Uploading(progress = null)) }
                val outcome =
                    profileRepository.setProfilePhoto(path) { progress ->
                        updateContent { it.copy(photoTask = PhotoTask.Uploading(progress)) }
                    }
                // Not when cancelled: TDLib may still be reading it.
                photoRepository.discardPhoto(path)
                finishPhotoTask(outcome)
            }
        }

        private fun finishPhotoTask(outcome: Outcome<Unit>) {
            updateContent { it.copy(photoTask = PhotoTask.None) }
            val error = (outcome as? Outcome.Failure)?.error ?: return
            if (error != DomainError.Silent) effectChannel.trySend(EditProfileEffect.ShowError(error))
        }

        private fun setDialog(dialog: EditProfileDialog?) {
            if (content == null) return
            savedStateHandle[KEY_DIALOG] = dialog?.name
            updateContent { it.copy(dialog = dialog) }
        }

        private inline fun updateContent(
            crossinline transform: (EditProfileUiState.Content) -> EditProfileUiState.Content,
        ) {
            mutableUiState.update { state -> if (state is EditProfileUiState.Content) transform(state) else state }
        }

        companion object {
            /** How long typing pauses before a new username is checked with Telegram. */
            val USERNAME_CHECK_DELAY = 500.milliseconds

            private const val KEY_FIRST_NAME = "editProfile.firstName"
            private const val KEY_LAST_NAME = "editProfile.lastName"
            private const val KEY_USERNAME = "editProfile.username"
            private const val KEY_BIO = "editProfile.bio"
            private const val KEY_BIRTHDATE = "editProfile.birthdate"
            private const val KEY_DIALOG = "editProfile.dialog"
        }
    }

/** What one request of Save changes. */
private enum class ProfileChange {
    Username,
    Name,
    Bio,
    Birthdate,
}

/** This content after [change] of [fields] was saved: TDLib has it as sent. */
private fun EditProfileUiState.Content.withSaved(
    change: ProfileChange,
    fields: ProfileFields,
): EditProfileUiState.Content =
    when (change) {
        ProfileChange.Username -> {
            copy(saved = saved.copy(username = fields.username), usernameStatus = UsernameStatus.Unchanged)
        }

        ProfileChange.Name -> {
            copy(saved = saved.copy(firstName = fields.firstName.trim(), lastName = fields.lastName.trim()))
        }

        ProfileChange.Bio -> {
            copy(saved = saved.copy(bio = fields.bio.trim()))
        }

        ProfileChange.Birthdate -> {
            copy(saved = saved.copy(birthdate = fields.birthdate))
        }
    }

private fun EditableProfile.toFields() =
    ProfileFields(firstName = firstName, lastName = lastName, username = username, bio = bio, birthdate = birthdate)

/** A birthday for the saved state: day, month and year (0 for none); empty for no birthday. */
private fun Birthdate?.toIntArray(): IntArray = if (this == null) IntArray(0) else intArrayOf(day, month, year ?: 0)

/** The birthday [toIntArray] saved; null for an empty array, which stands for none. */
private fun IntArray.toBirthdate(): Birthdate? =
    if (size == BIRTHDATE_FIELDS) Birthdate(this[0], this[1], this[2].takeIf { it > 0 }) else null

private const val BIRTHDATE_FIELDS = 3

private fun User.toProfileAccount() =
    ProfileAccount(
        userId = id,
        name = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" "),
        photo = profilePhoto,
        phoneNumber = phoneNumber,
    )
