package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.navigation.EditChatInfoRoute
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the page that edits a group's or channel's photo, title and description shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface EditChatInfoUiState {
    data object Loading : EditChatInfoUiState

    data class Error(
        val error: DomainError,
    ) : EditChatInfoUiState

    /**
     * @property avatarId whose placeholder colors the photo has without a picture: the chat's id.
     * @property photo the chat's photo as TDLib has it now; null for none.
     * @property photoTask what is being done with the photo, apart from saving the texts.
     * @property confirmingPhotoRemoval the question whether to remove the photo shows.
     * @property titleError why Telegram refused the title; null while it did not.
     * @property descriptionError why Telegram refused the description; null while it did not.
     * @property isSaving the changes were sent, and TDLib has not answered yet.
     */
    data class Content(
        val kind: GroupKind,
        val title: String,
        val description: String,
        val avatarId: Long = 0,
        val photo: ProfilePhoto? = null,
        val photoTask: PhotoTask = PhotoTask.None,
        val confirmingPhotoRemoval: Boolean = false,
        val titleError: DomainError? = null,
        val descriptionError: DomainError? = null,
        val isSaving: Boolean = false,
    ) : EditChatInfoUiState {
        val canSave: Boolean get() = title.isNotBlank() && !isSaving

        override fun toString() = "Content(kind=$kind, isSaving=$isSaving, …)"
    }
}

sealed interface EditChatInfoEvent {
    data object Retry : EditChatInfoEvent

    data class TitleChanged(
        val title: String,
    ) : EditChatInfoEvent {
        override fun toString() = "TitleChanged(…)"
    }

    data class DescriptionChanged(
        val description: String,
    ) : EditChatInfoEvent {
        override fun toString() = "DescriptionChanged(…)"
    }

    data object Save : EditChatInfoEvent

    /** The photo picker gave the image at [uri] (a content URI, as a string). */
    data class PhotoPicked(
        val uri: String,
    ) : EditChatInfoEvent {
        override fun toString() = "PhotoPicked(…)"
    }

    /** "Remove photo": asks first. */
    data object RemovePhoto : EditChatInfoEvent

    data object ConfirmPhotoRemoval : EditChatInfoEvent

    data object DismissPhotoRemoval : EditChatInfoEvent
}

sealed interface EditChatInfoEffect {
    /** Telegram has the changes: the page closes. */
    data object Saved : EditChatInfoEffect

    data class ShowError(
        val error: DomainError,
    ) : EditChatInfoEffect
}

/**
 * Editing the photo, title and description of group or channel [chatId] (`REQUIREMENTS.md` FR-P3), which the page's
 * row on the info page offers where the user may change the chat's info:
 *
 * - **Title and description** start as TDLib has them, what the user typed is kept in the [SavedStateHandle], and
 *   saving sends only what changed, the title first ([GroupAdministrationRepository.setTitle], then
 *   `setDescription`), stopping at the first refusal. A title or description Telegram refuses shows under its field;
 *   other errors are snackbars.
 * - **Photo**, apart from saving: a picked image is prepared by [photoRepository] (an upright JPEG without metadata),
 *   uploaded with its progress ([GroupAdministrationRepository.setPhoto]) and the copy discarded once TDLib answered;
 *   removing asks first (kept in the [SavedStateHandle]). The photo shown follows the chat ([ChatRepository.chat]),
 *   so it changes as soon as TDLib reports the new one, here and on the info page. Leaving meanwhile stops waiting,
 *   TDLib carries on.
 */
@Suppress("LongParameterList", "TooManyFunctions") // The route, four repositories; one handler per event.
@HiltViewModel
class EditChatInfoViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
    private val photoRepository: PickedPhotoRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
        photoRepository: PickedPhotoRepository,
    ) : this(
        savedStateHandle.toRoute<EditChatInfoRoute>().chatId,
        savedStateHandle,
        chatRepository,
        groupInfoRepository,
        administrationRepository,
        photoRepository,
    )

    private val mutableUiState = MutableStateFlow<EditChatInfoUiState>(EditChatInfoUiState.Loading)
    val uiState: StateFlow<EditChatInfoUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<EditChatInfoEffect>(Channel.BUFFERED)
    val effects: Flow<EditChatInfoEffect> = effectChannel.receiveAsFlow()

    /** The title and description as TDLib had them, which saving compares with. */
    private var original: Pair<String, String>? = null

    init {
        viewModelScope.launch(Dispatchers.Main) { load() }
    }

    fun onEvent(event: EditChatInfoEvent) {
        when (event) {
            EditChatInfoEvent.Retry -> {
                retry()
            }

            is EditChatInfoEvent.TitleChanged -> {
                edit { it.copy(title = event.title, titleError = null) }
            }

            is EditChatInfoEvent.DescriptionChanged -> {
                edit { it.copy(description = event.description, descriptionError = null) }
            }

            EditChatInfoEvent.Save -> {
                save()
            }

            is EditChatInfoEvent.PhotoPicked -> {
                setPhoto(event.uri)
            }

            EditChatInfoEvent.RemovePhoto -> {
                askPhotoRemoval(true)
            }

            EditChatInfoEvent.DismissPhotoRemoval -> {
                askPhotoRemoval(false)
            }

            EditChatInfoEvent.ConfirmPhotoRemoval -> {
                removePhoto()
            }
        }
    }

    private fun retry() {
        if (mutableUiState.value is EditChatInfoUiState.Error) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableUiState.value = EditChatInfoUiState.Loading
        val (chat, group) =
            coroutineScope {
                val chat = async { chatRepository.getChat(chatId) }
                val group = async { groupInfoRepository.groupInfo(chatId).first { it.isSettled() } }
                chat.await() to group.await()
            }
        if (chat is Outcome.Failure) {
            mutableUiState.value = EditChatInfoUiState.Error(chat.error)
            return
        }
        if (group is Outcome.Failure) {
            mutableUiState.value = EditChatInfoUiState.Error(group.error)
            return
        }
        val title = (chat as Outcome.Success).value.title
        val info = (group as Outcome.Success).value
        val description =
            info.details
                ?.description
                ?.text
                .orEmpty()
        original = title to description
        mutableUiState.value =
            EditChatInfoUiState.Content(
                kind = info.kind,
                title = savedStateHandle.get<String>(KEY_TITLE) ?: title,
                description = savedStateHandle.get<String>(KEY_DESCRIPTION) ?: description,
                avatarId = chatId,
                photo = chat.value.photo,
                confirmingPhotoRemoval = savedStateHandle.get<Boolean>(KEY_REMOVING_PHOTO) == true,
            )
        viewModelScope.launch {
            chatRepository.chat(chatId).filterNotNull().collect { changed ->
                updateContent { it.copy(photo = changed.photo) }
            }
        }
    }

    private fun setPhoto(uri: String) {
        val content = mutableUiState.value as? EditChatInfoUiState.Content ?: return
        if (content.photoTask != PhotoTask.None) return
        updateContent { it.copy(photoTask = PhotoTask.Preparing) }
        viewModelScope.launch {
            val path =
                when (val prepared = photoRepository.preparePhoto(uri)) {
                    is Outcome.Success -> prepared.value
                    is Outcome.Failure -> return@launch finishPhotoTask(prepared)
                }
            updateContent { it.copy(photoTask = PhotoTask.Uploading(progress = null)) }
            val outcome =
                administrationRepository.setPhoto(chatId, path) { progress ->
                    updateContent { it.copy(photoTask = PhotoTask.Uploading(progress)) }
                }
            // Not when cancelled: TDLib may still be reading it.
            photoRepository.discardPhoto(path)
            finishPhotoTask(outcome)
        }
    }

    private fun askPhotoRemoval(ask: Boolean) {
        val content = mutableUiState.value as? EditChatInfoUiState.Content ?: return
        if (ask && (content.photo == null || content.photoTask != PhotoTask.None)) return
        savedStateHandle[KEY_REMOVING_PHOTO] = ask
        updateContent { it.copy(confirmingPhotoRemoval = ask) }
    }

    private fun removePhoto() {
        val content = mutableUiState.value as? EditChatInfoUiState.Content ?: return
        askPhotoRemoval(false)
        if (content.photoTask != PhotoTask.None) return
        updateContent { it.copy(photoTask = PhotoTask.Removing) }
        viewModelScope.launch { finishPhotoTask(administrationRepository.removePhoto(chatId)) }
    }

    private fun finishPhotoTask(outcome: Outcome<Unit>) {
        updateContent { it.copy(photoTask = PhotoTask.None) }
        val error = (outcome as? Outcome.Failure)?.error ?: return
        if (error != DomainError.Silent) effectChannel.trySend(EditChatInfoEffect.ShowError(error))
    }

    private inline fun updateContent(change: (EditChatInfoUiState.Content) -> EditChatInfoUiState.Content) {
        mutableUiState.update { state -> (state as? EditChatInfoUiState.Content)?.let(change) ?: state }
    }

    private inline fun edit(change: (EditChatInfoUiState.Content) -> EditChatInfoUiState.Content) {
        val current = mutableUiState.value as? EditChatInfoUiState.Content ?: return
        if (current.isSaving) return
        val changed = change(current)
        savedStateHandle[KEY_TITLE] = changed.title
        savedStateHandle[KEY_DESCRIPTION] = changed.description
        mutableUiState.value = changed
    }

    private fun save() {
        val current = mutableUiState.value as? EditChatInfoUiState.Content
        val saved = original
        if (current == null || saved == null || !current.canSave) return
        val (title, description) = saved
        mutableUiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            if (current.title.trim() != title) {
                val renamed = administrationRepository.setTitle(chatId, current.title)
                if (renamed is Outcome.Failure) return@launch fail(renamed.error, onTitle = true)
            }
            if (current.description.trim() != description.trim()) {
                val described = administrationRepository.setDescription(chatId, current.description)
                if (described is Outcome.Failure) return@launch fail(described.error, onTitle = false)
            }
            mutableUiState.update { (it as? EditChatInfoUiState.Content)?.copy(isSaving = false) ?: it }
            effectChannel.trySend(EditChatInfoEffect.Saved)
        }
    }

    /** The field's own error under [onTitle]'s field where it is about it, a snackbar otherwise. */
    private fun fail(
        error: DomainError,
        onTitle: Boolean,
    ) {
        mutableUiState.update { state ->
            val content = state as? EditChatInfoUiState.Content ?: return@update state
            when (error) {
                DomainError.TitleInvalid -> content.copy(isSaving = false, titleError = error)
                DomainError.DescriptionTooLong -> content.copy(isSaving = false, descriptionError = error)
                else -> content.copy(isSaving = false)
            }
        }
        val shownInField = error == DomainError.TitleInvalid || error == DomainError.DescriptionTooLong
        if (!shownInField && error != DomainError.Silent) effectChannel.trySend(EditChatInfoEffect.ShowError(error))
        // The title went through before a refused description: compare with it from now on.
        val sent = (mutableUiState.value as? EditChatInfoUiState.Content)?.title?.trim()
        if (!onTitle && sent != null) original = original?.copy(first = sent)
    }

    private companion object {
        const val KEY_TITLE = "editChatInfo.title"
        const val KEY_DESCRIPTION = "editChatInfo.description"
        const val KEY_REMOVING_PHOTO = "editChatInfo.removingPhoto"
    }
}

/** The group with its full info, or why either could not be read: what the page starts from. */
private fun Outcome<GroupInfo>.isSettled(): Boolean =
    this !is Outcome.Success || value.details != null || value.detailsError != null
