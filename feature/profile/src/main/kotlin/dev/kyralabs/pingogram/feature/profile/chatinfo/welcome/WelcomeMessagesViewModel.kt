package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeLimits
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.model.WelcomePhoto
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.WelcomeMessageRepository
import dev.kyralabs.pingogram.core.navigation.WelcomeMessagesRoute
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A group's welcome messages (`docs/DESIGN_SYSTEM.md` §6 "Welcome message setup", the owner's board 20) as [uiState]:
 * `WelcomeMessageRepository.messages`, which follows every change Telegram reports, and its limit.
 *
 * - **The switch** is on while the group has a welcome message: turning it on opens the editor for a first one,
 *   turning it off asks, then deletes them all.
 * - **The editor** adds or edits a text, or a photo with a caption. What is typed, the photo picked (its copy's path)
 *   and which message it edits are kept in the [SavedStateHandle]. Its text is plain: a message whose text another app
 *   formatted keeps its formatting while its text is not changed here. A picked photo's copy is discarded once Telegram
 *   answered, when another is picked, and when the editor closes without saving.
 * - **Deleting** one message asks first.
 *
 * The page starts its work after the frame that composes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // A function per event.
@HiltViewModel
class WelcomeMessagesViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val repository: WelcomeMessageRepository,
    private val pickedPhotos: PickedPhotoRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: WelcomeMessageRepository,
        pickedPhotos: PickedPhotoRepository,
    ) : this(savedStateHandle.toRoute<WelcomeMessagesRoute>().chatId, savedStateHandle, repository, pickedPhotos)

    private val effectChannel = Channel<WelcomeMessagesEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: the photo picker, snackbars. */
    val effects: Flow<WelcomeMessagesEffect> = effectChannel.receiveAsFlow()

    private val started = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)
    private val countMax = MutableStateFlow(WelcomeLimits.COUNT_MAX_DEFAULT)
    private val isBusy = MutableStateFlow(false)
    private val deleting = MutableStateFlow<Set<Int>>(emptySet())
    private val dialog = MutableStateFlow(restoredDialog())
    private val editor = MutableStateFlow(restoredEditor())

    /** The messages as Telegram has them; null while loading. */
    private val messages: Flow<Outcome<ImmutableList<WelcomeMessage>>?> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (isStarted) {
                repository
                    .messages(
                        chatId,
                    ).map<_, Outcome<ImmutableList<WelcomeMessage>>?> { it }
                    .onStart { emit(null) }
            } else {
                flowOf(null)
            }
        }

    /** The latest messages Telegram sent, so the editor finds a kept photo after process death. */
    private var latest: List<WelcomeMessage> = emptyList()

    val uiState: StateFlow<WelcomeMessagesUiState> =
        combine(messages, countMax, isBusy, deleting) { messages, max, busy, deleting ->
            when (messages) {
                null -> {
                    WelcomeMessagesUiState.Loading
                }

                is Outcome.Failure -> {
                    WelcomeMessagesUiState.Error(messages.error)
                }

                is Outcome.Success -> {
                    latest = messages.value
                    WelcomeMessagesUiState.Content(
                        messages = messages.value.map { it.toItem() }.toImmutableList(),
                        countMax = max,
                        isBusy = busy,
                        deleting = deleting,
                    )
                }
            }
        }.combine(
            dialog,
        ) { state, dialog -> (state as? WelcomeMessagesUiState.Content)?.copy(dialog = dialog) ?: state }
            .combine(editor) { state, editor ->
                (state as? WelcomeMessagesUiState.Content)?.copy(editor = editor?.withKeptPhoto()) ?: state
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                WelcomeMessagesUiState.Loading,
            )

    init {
        // The screen creates the view model in the frame that first composes it; its work starts after that frame.
        viewModelScope.launch(Dispatchers.Main) {
            started.value = true
            countMax.value = repository.countMax()
        }
    }

    fun onEvent(event: WelcomeMessagesEvent) {
        when (event) {
            WelcomeMessagesEvent.Retry -> attempts.update { it + 1 }
            is WelcomeMessagesEvent.Toggle -> toggle(event.on)
            WelcomeMessagesEvent.Add -> openEditor(null)
            is WelcomeMessagesEvent.Edit -> openEditor(event.id)
            is WelcomeMessagesEvent.Delete -> askDelete(event.id)
            WelcomeMessagesEvent.ConfirmDialog -> confirm()
            WelcomeMessagesEvent.DismissDialog -> setDialog(null)
            is WelcomeMessagesEvent.TextChanged -> editText(event.text)
            WelcomeMessagesEvent.PickPhoto -> if (editor.value?.isSaving == false) pick()
            is WelcomeMessagesEvent.PhotoPicked -> preparePhoto(event.uri)
            WelcomeMessagesEvent.RemovePhoto -> if (editor.value?.isSaving == false) changeEditor { it.withPhoto(null) }
            WelcomeMessagesEvent.Save -> save()
            WelcomeMessagesEvent.CloseEditor -> if (editor.value?.isSaving == false) closeEditor()
        }
    }

    override fun onCleared() {
        // A copy picked and not saved is not needed once the page is gone for good.
        (editor.value?.photo as? EditorPhoto.Picked)?.let { pickedPhotos.discardPhoto(it.path) }
    }

    private fun toggle(on: Boolean) {
        val content = uiState.value as? WelcomeMessagesUiState.Content ?: return
        if (on == content.isOn || content.isBusy) return
        if (on) openEditor(null) else setDialog(WelcomeDialog.TurnOff)
    }

    private fun openEditor(id: Int?) {
        if (editor.value != null) return
        (if (id == null) newEditor() else editorOf(id))?.let(::setEditor)
    }

    /** An empty editor, while the group may have another message. */
    private fun newEditor(): WelcomeEditor? =
        (uiState.value as? WelcomeMessagesUiState.Content)
            ?.takeIf { it.canAdd }
            ?.let { WelcomeEditor(messageId = null, text = "", photo = null) }

    /** The editor of message [id], as Telegram has it; null for one Pingogram cannot show. */
    private fun editorOf(id: Int): WelcomeEditor? =
        latest.firstOrNull { it.id == id && it.isSupported }?.let { message ->
            WelcomeEditor(
                messageId = id,
                text = message.text.text,
                photo = message.photo?.let(EditorPhoto::Kept),
                isFormatted = message.text.entities.isNotEmpty(),
            )
        }

    private fun askDelete(id: Int) {
        val position = latest.indexOfFirst { it.id == id }
        if (position < 0 || id in deleting.value) return
        setDialog(WelcomeDialog.Delete(id, position + 1))
    }

    private fun confirm() {
        when (val open = dialog.value ?: return) {
            is WelcomeDialog.Delete -> {
                setDialog(null)
                deleting.update { it + open.id }
                viewModelScope.launch {
                    val outcome = repository.delete(chatId, open.id)
                    deleting.update { it - open.id }
                    if (outcome is Outcome.Failure) showError(outcome.error)
                }
            }

            WelcomeDialog.TurnOff -> {
                if (isBusy.value) return
                setDialog(null)
                isBusy.value = true
                viewModelScope.launch {
                    val outcome = repository.deleteAll(chatId)
                    isBusy.value = false
                    when (outcome) {
                        is Outcome.Success -> notice(R.string.welcome_turned_off)
                        is Outcome.Failure -> showError(outcome.error)
                    }
                }
            }
        }
    }

    private fun editText(text: String) {
        if (editor.value?.isSaving != false) return
        changeEditor { it.copy(text = text, error = null) }
    }

    private fun pick() {
        if (editor.value?.photo == EditorPhoto.Preparing) return
        effectChannel.trySend(WelcomeMessagesEffect.PickPhoto)
    }

    private fun preparePhoto(uri: String) {
        val open = editor.value ?: return
        if (open.isSaving) return
        val before = open.photo
        changeEditor { it.copy(photo = EditorPhoto.Preparing, error = null) }
        viewModelScope.launch {
            when (val prepared = pickedPhotos.preparePhoto(uri)) {
                is Outcome.Success -> {
                    if (editor.value?.photo != EditorPhoto.Preparing) {
                        // The editor closed or changed meanwhile: the copy is not needed.
                        pickedPhotos.discardPhoto(prepared.value)
                    } else {
                        (before as? EditorPhoto.Picked)?.let { pickedPhotos.discardPhoto(it.path) }
                        changeEditor { it.copy(photo = EditorPhoto.Picked(prepared.value)) }
                    }
                }

                is Outcome.Failure -> {
                    changeEditor { it.copy(photo = before) }
                    showError(prepared.error)
                }
            }
        }
    }

    private fun save() {
        val open = editor.value?.withKeptPhoto() ?: return
        if (!open.canSave) return
        val original = latest.firstOrNull { it.id == open.messageId }
        val text =
            original
                ?.text
                ?.takeIf { it.text == open.text }
                ?: FormattedText(open.text.trim(), persistentListOf())
        val photo =
            when (val photo = open.photo) {
                is EditorPhoto.Picked -> WelcomePhoto.Picked(photo.path)
                is EditorPhoto.Kept -> WelcomePhoto.Kept(photo.photo)
                EditorPhoto.Preparing, null -> null
            }
        val draft = WelcomeDraft(text, photo)
        changeEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val outcome =
                if (open.messageId == null) {
                    repository.add(chatId, draft)
                } else {
                    repository.edit(chatId, open.messageId, draft)
                }
            when (outcome) {
                is Outcome.Success -> {
                    closeEditor()
                    notice(if (open.messageId == null) R.string.welcome_added else R.string.welcome_saved)
                }

                is Outcome.Failure -> {
                    changeEditor {
                        it.copy(
                            isSaving = false,
                            error =
                                outcome.error.takeIf { e ->
                                    e != DomainError.Silent
                                },
                        )
                    }
                }
            }
        }
    }

    /** Closes the editor, discarding the copy of a photo it picked: Telegram has it, or it is not needed. */
    private fun closeEditor() {
        val open = editor.value ?: return
        (open.photo as? EditorPhoto.Picked)?.let { pickedPhotos.discardPhoto(it.path) }
        setEditor(null)
    }

    private fun WelcomeEditor.withPhoto(photo: EditorPhoto?): WelcomeEditor {
        (this.photo as? EditorPhoto.Picked)?.let { pickedPhotos.discardPhoto(it.path) }
        return copy(photo = photo, error = null)
    }

    /** The kept photo found again in the messages after process death, which the saved state cannot hold. */
    private fun WelcomeEditor.withKeptPhoto(): WelcomeEditor =
        if (savedStateHandle.get<Boolean>(KEY_KEEP_PHOTO) == true && photo == null && messageId != null) {
            val kept = latest.firstOrNull { it.id == messageId }?.photo
            if (kept != null) copy(photo = EditorPhoto.Kept(kept)) else this
        } else {
            this
        }

    private fun changeEditor(change: (WelcomeEditor) -> WelcomeEditor) {
        val open = editor.value ?: return
        setEditor(change(open.withKeptPhoto()))
    }

    private fun setEditor(open: WelcomeEditor?) {
        editor.value = open
        if (open == null) {
            listOf(KEY_EDITING, KEY_ID, KEY_TEXT, KEY_PHOTO, KEY_KEEP_PHOTO, KEY_FORMATTED).forEach {
                savedStateHandle.remove<Any>(it)
            }
        } else {
            savedStateHandle[KEY_EDITING] = true
            savedStateHandle[KEY_ID] = open.messageId
            savedStateHandle[KEY_TEXT] = open.text
            savedStateHandle[KEY_PHOTO] = (open.photo as? EditorPhoto.Picked)?.path
            savedStateHandle[KEY_KEEP_PHOTO] = open.photo is EditorPhoto.Kept
            savedStateHandle[KEY_FORMATTED] = open.isFormatted
        }
    }

    private fun restoredEditor(): WelcomeEditor? {
        if (savedStateHandle.get<Boolean>(KEY_EDITING) != true) return null
        val photo = savedStateHandle.get<String>(KEY_PHOTO)?.let(EditorPhoto::Picked)
        return WelcomeEditor(
            messageId = savedStateHandle[KEY_ID],
            text = savedStateHandle[KEY_TEXT] ?: "",
            photo = photo,
            isFormatted = savedStateHandle[KEY_FORMATTED] ?: false,
        )
    }

    private fun setDialog(open: WelcomeDialog?) {
        dialog.value = open
        when (open) {
            is WelcomeDialog.Delete -> {
                savedStateHandle[KEY_DELETE_ID] = open.id
                savedStateHandle[KEY_DELETE_POSITION] = open.position
            }

            WelcomeDialog.TurnOff -> {
                savedStateHandle[KEY_TURN_OFF] = true
            }

            null -> {
                listOf(KEY_DELETE_ID, KEY_DELETE_POSITION, KEY_TURN_OFF).forEach { savedStateHandle.remove<Any>(it) }
            }
        }
    }

    private fun restoredDialog(): WelcomeDialog? {
        val id = savedStateHandle.get<Int>(KEY_DELETE_ID)
        return when {
            id != null -> WelcomeDialog.Delete(id, savedStateHandle[KEY_DELETE_POSITION] ?: 1)
            savedStateHandle.get<Boolean>(KEY_TURN_OFF) == true -> WelcomeDialog.TurnOff
            else -> null
        }
    }

    private fun notice(text: Int) {
        effectChannel.trySend(WelcomeMessagesEffect.Notice(text))
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(WelcomeMessagesEffect.ShowError(error))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_EDITING = "welcome.editing"
        const val KEY_ID = "welcome.id"
        const val KEY_TEXT = "welcome.text"
        const val KEY_PHOTO = "welcome.photo"
        const val KEY_KEEP_PHOTO = "welcome.keepPhoto"
        const val KEY_FORMATTED = "welcome.formatted"
        const val KEY_DELETE_ID = "welcome.deleteId"
        const val KEY_DELETE_POSITION = "welcome.deletePosition"
        const val KEY_TURN_OFF = "welcome.turnOff"
    }
}

private fun WelcomeMessage.toItem() = WelcomeItem(id, text, photo, isSupported)
