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
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.navigation.EditChatInfoRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the page that edits a group's or channel's title and description shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface EditChatInfoUiState {
    data object Loading : EditChatInfoUiState

    data class Error(
        val error: DomainError,
    ) : EditChatInfoUiState

    /**
     * @property titleError why Telegram refused the title; null while it did not.
     * @property descriptionError why Telegram refused the description; null while it did not.
     * @property isSaving the changes were sent, and TDLib has not answered yet.
     */
    data class Content(
        val kind: GroupKind,
        val title: String,
        val description: String,
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
}

sealed interface EditChatInfoEffect {
    /** Telegram has the changes: the page closes. */
    data object Saved : EditChatInfoEffect

    data class ShowError(
        val error: DomainError,
    ) : EditChatInfoEffect
}

/**
 * Editing the title and description of group or channel [chatId] (`REQUIREMENTS.md` FR-P3): both start as TDLib has
 * them, what the user typed is kept in the [SavedStateHandle], and saving sends only what changed, the title first
 * ([GroupAdministrationRepository.setTitle], then `setDescription`), stopping at the first refusal. A title or
 * description Telegram refuses shows under its field; other errors are snackbars.
 */
@HiltViewModel
class EditChatInfoViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
    ) : this(
        savedStateHandle.toRoute<EditChatInfoRoute>().chatId,
        savedStateHandle,
        chatRepository,
        groupInfoRepository,
        administrationRepository,
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
            )
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
    }
}

/** The group with its full info, or why either could not be read: what the page starts from. */
private fun Outcome<GroupInfo>.isSettled(): Boolean =
    this !is Outcome.Success || value.details != null || value.detailsError != null
