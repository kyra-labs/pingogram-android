package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.repository.ChatFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.navigation.ChatFolderEditRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The folder editor (`REQUIREMENTS.md` FR-L3): a folder's name, icon, and the chats and kinds of chats it includes
 * and leaves out, for a new folder ([ChatFolderEditRoute.folderId] null) or one of the user's.
 *
 * What the user changes is kept in the [SavedStateHandle], so it survives process death; what the editor does not
 * show (the pinned chats, the tag's color, whether the folder is shared, a title's custom emoji while the name keeps
 * its text) comes from the folder as Telegram has it. A chat goes to one side at a time: picking it for one side takes
 * it from the other. "Save" asks for a name and for something to include before it reaches Telegram.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatFolderEditViewModel internal constructor(
    private val folderId: Int?,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val folderRepository: ChatFolderRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        folderRepository: ChatFolderRepository,
    ) : this(
        savedStateHandle.toRoute<ChatFolderEditRoute>().folderId,
        savedStateHandle,
        chatRepository,
        folderRepository,
    )

    /** The folder as Telegram has it; an empty one for a new folder. Null until it is loaded. */
    private var original: ChatFolderSettings? = null

    private val draft = MutableStateFlow<FolderDraft?>(null)
    private val status = MutableStateFlow(EditStatus())
    private val loadError = MutableStateFlow<DomainError?>(null)

    /** The chats the editor knows the names of, by id. */
    private val knownChats = MutableStateFlow<Map<Long, FolderChatItem>>(emptyMap())

    private val pickerSide =
        MutableStateFlow(savedStateHandle.get<String>(KEY_PICKER)?.let(FolderPickerSide::valueOf))
    private val pickerQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())

    private val effectChannel = Channel<ChatFolderEditEffect>(Channel.BUFFERED)
    val effects: Flow<ChatFolderEditEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<ChatFolderEditUiState> =
        combine(draft, status, knownChats, loadError) { draft, status, chats, error ->
            when {
                error != null -> ChatFolderEditUiState.Error(error)
                draft == null -> ChatFolderEditUiState.Loading
                else -> draft.toContent(isNew = folderId == null, status, chats)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatFolderEditUiState.Loading)

    /** The picker sheet while it shows; null otherwise. */
    val picker: StateFlow<FolderPickerUiState?> =
        pickerSide
            .flatMapLatest { side ->
                if (side == null) {
                    flowOf(null)
                } else {
                    combine(chatRepository.chatList(ChatListType.Main), pickerQuery, draft) { chats, query, draft ->
                        pickerState(side, query, chats, draft)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: ChatFolderEditEvent) {
        when (event) {
            ChatFolderEditEvent.Retry -> {
                if (loadError.value != null) {
                    loadError.value = null
                    viewModelScope.launch { load() }
                }
            }

            is ChatFolderEditEvent.NameChanged -> {
                editDraft { copy(name = event.name.take(MAX_NAME)) }
            }

            is ChatFolderEditEvent.SelectIcon -> {
                editDraft { copy(iconName = event.iconName) }
            }

            is ChatFolderEditEvent.ToggleIncludedType -> {
                editDraft {
                    copy(
                        includedTypes = includedTypes.toggle(event.type),
                    )
                }
            }

            is ChatFolderEditEvent.ToggleExcludedType -> {
                editDraft {
                    copy(
                        excludedTypes = excludedTypes.toggle(event.type),
                    )
                }
            }

            is ChatFolderEditEvent.RemoveChat -> {
                editDraft { without(event.side, event.chatId) }
            }

            is ChatFolderEditEvent.OpenPicker -> {
                setPicker(event.side)
            }

            is ChatFolderEditEvent.PickerQueryChanged -> {
                setPickerQuery(event.query)
            }

            is ChatFolderEditEvent.TogglePickedChat -> {
                togglePicked(event.chat)
            }

            ChatFolderEditEvent.ClosePicker -> {
                setPicker(null)
            }

            ChatFolderEditEvent.Save -> {
                save()
            }

            ChatFolderEditEvent.RequestDelete -> {
                status.update { it.copy(confirmingDelete = folderId != null) }
            }

            ChatFolderEditEvent.DismissDelete -> {
                status.update { it.copy(confirmingDelete = false) }
            }

            ChatFolderEditEvent.ConfirmDelete -> {
                delete()
            }
        }
    }

    private suspend fun load() {
        val settings =
            if (folderId == null) {
                ChatFolderSettings(title = FormattedText("", persistentListOf()))
            } else {
                when (val outcome = folderRepository.folder(folderId)) {
                    is Outcome.Success -> {
                        outcome.value
                    }

                    is Outcome.Failure -> {
                        loadError.value = outcome.error
                        return
                    }
                }
            }
        original = settings
        val loaded = FolderDraft.restore(savedStateHandle) ?: FolderDraft.of(settings)
        draft.value = loaded
        (loaded.included + loaded.excluded).forEach(::learnChat)
    }

    /** Looks up chat [id]'s name and photo once, for its row. */
    private fun learnChat(id: Long) {
        if (id in knownChats.value) return
        viewModelScope.launch {
            val chat = (chatRepository.getChat(id) as? Outcome.Success)?.value ?: return@launch
            knownChats.update { it + (id to chat.toFolderChatItem()) }
        }
    }

    private fun editDraft(change: FolderDraft.() -> FolderDraft) {
        val current = draft.value ?: return
        if (status.value.isBusy) return
        val edited = current.change()
        draft.value = edited
        edited.saveTo(savedStateHandle)
        status.update {
            it.copy(
                nameMissing = it.nameMissing && edited.name.isBlank(),
                nothingIncluded = it.nothingIncluded && !edited.includesAnything,
            )
        }
    }

    /** Opens the picker for [side], or closes it with null. */
    private fun setPicker(side: FolderPickerSide?) {
        pickerSide.value = side
        savedStateHandle[KEY_PICKER] = side?.name
        setPickerQuery("")
        // The chat list shows the chats TDLib loaded; the picker asks for its first page itself.
        if (side != null) viewModelScope.launch { chatRepository.loadChats(ChatListType.Main) }
    }

    private fun setPickerQuery(query: String) {
        pickerQuery.value = query
        savedStateHandle[KEY_QUERY] = query
    }

    private fun togglePicked(chat: FolderChatItem) {
        val side = pickerSide.value ?: return
        knownChats.update { it + (chat.chatId to chat.copy(selected = false)) }
        editDraft { if (chat.chatId in chatsOf(side)) without(side, chat.chatId) else with(side, chat.chatId) }
    }

    private fun save() {
        val current = draft.value
        val base = original
        if (current == null || base == null || status.value.isBusy) return
        val nameMissing = current.name.isBlank()
        val nothingIncluded = !current.includesAnything
        if (nameMissing || nothingIncluded) {
            status.update { it.copy(nameMissing = nameMissing, nothingIncluded = nothingIncluded) }
            effectChannel.trySend(ChatFolderEditEffect.Rejected)
            return
        }
        val settings = current.applyTo(base)
        status.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val outcome =
                if (folderId == null) {
                    folderRepository.createFolder(settings)
                } else {
                    folderRepository.editFolder(folderId, settings)
                }
            status.update { it.copy(isSaving = false) }
            finish(outcome)
        }
    }

    private fun delete() {
        val id = folderId ?: return
        if (status.value.isBusy) return
        status.update { it.copy(confirmingDelete = false, isDeleting = true) }
        viewModelScope.launch {
            // The folder's chats stay where they are, also those of a shared folder.
            val outcome = folderRepository.deleteFolder(id, leaveChatIds = emptyList())
            status.update { it.copy(isDeleting = false) }
            finish(outcome)
        }
    }

    private fun finish(outcome: Outcome<*>) {
        when (outcome) {
            is Outcome.Success -> {
                effectChannel.trySend(ChatFolderEditEffect.Done)
            }

            is Outcome.Failure -> {
                effectChannel.trySend(ChatFolderEditEffect.Rejected)
                if (outcome.error != DomainError.Silent) {
                    effectChannel.trySend(ChatFolderEditEffect.ShowError(outcome.error))
                }
            }
        }
    }

    private companion object {
        const val KEY_PICKER = "picker"
        const val KEY_QUERY = "pickerQuery"
        const val MAX_NAME = ChatFolderSettings.MAX_TITLE_LENGTH
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What is waiting for Telegram, and why "Save" was refused. */
private data class EditStatus(
    val nameMissing: Boolean = false,
    val nothingIncluded: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val confirmingDelete: Boolean = false,
) {
    val isBusy: Boolean get() = isSaving || isDeleting
}

private fun Chat.toFolderChatItem() = FolderChatItem(chatId = id, title = title, photo = photo)

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun FolderDraft.toContent(
    isNew: Boolean,
    status: EditStatus,
    chats: Map<Long, FolderChatItem>,
) = ChatFolderEditUiState.Content(
    isNew = isNew,
    name = name,
    iconName = iconName,
    includedTypes = includedTypes.toImmutableSet(),
    excludedTypes = excludedTypes.toImmutableSet(),
    includedChats = included.mapNotNull { chats[it] }.toImmutableList(),
    excludedChats = excluded.mapNotNull { chats[it] }.toImmutableList(),
    nameMissing = status.nameMissing,
    nothingIncluded = status.nothingIncluded,
    isSaving = status.isSaving,
    isDeleting = status.isDeleting,
    confirmingDelete = status.confirmingDelete,
)

private fun pickerState(
    side: FolderPickerSide,
    query: String,
    chats: List<Chat>,
    draft: FolderDraft?,
): FolderPickerUiState {
    val picked = draft?.chatsOf(side).orEmpty()
    val needle = query.trim()
    val matches =
        chats
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .map { it.toFolderChatItem().copy(selected = it.id in picked) }
    return FolderPickerUiState(
        side = side,
        query = query,
        includedTypes = draft?.includedTypes.orEmpty().toImmutableSet(),
        excludedTypes = draft?.excludedTypes.orEmpty().toImmutableSet(),
        chats = matches.toImmutableList(),
    )
}
