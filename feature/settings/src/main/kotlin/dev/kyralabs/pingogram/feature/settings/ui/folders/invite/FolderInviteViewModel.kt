package dev.kyralabs.pingogram.feature.settings.ui.folders.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInvite
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.navigation.ChatFolderInviteRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The sheet of shared folder link [inviteLink] (`t.me/addlist/…`, `REQUIREMENTS.md` FR-L3): asks Telegram what the
 * link holds ([ChatFolderRepository.checkInviteLink], which only reads), and adds the folder with the chats the user
 * picked, joining them, only on "Add" ([ChatFolderRepository.addFolderByInviteLink]). A folder the user has already
 * offers the chats added to it since, or says there is nothing new.
 *
 * Nothing needs restoring after process death: the link is in the route, and the sheet checks it again.
 */
@HiltViewModel
class FolderInviteViewModel internal constructor(
    private val inviteLink: String,
    private val folderRepository: ChatFolderRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        folderRepository: ChatFolderRepository,
        chatRepository: ChatRepository,
    ) : this(savedStateHandle.toRoute<ChatFolderInviteRoute>().inviteLink, folderRepository, chatRepository)

    private val mutableUiState = MutableStateFlow<FolderInviteUiState>(FolderInviteUiState.Loading)
    val uiState: StateFlow<FolderInviteUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<FolderInviteEffect>(Channel.BUFFERED)
    val effects: Flow<FolderInviteEffect> = effectChannel.receiveAsFlow()

    init {
        // After the sheet's first frame (docs/PERFORMANCE.md §4).
        viewModelScope.launch(Dispatchers.Main) { load() }
    }

    fun onEvent(event: FolderInviteEvent) {
        when (event) {
            FolderInviteEvent.Retry -> {
                if (mutableUiState.value is FolderInviteUiState.Failed) {
                    mutableUiState.value = FolderInviteUiState.Loading
                    viewModelScope.launch { load() }
                }
            }

            is FolderInviteEvent.TogglePicked -> {
                updateContent { content ->
                    content.copy(
                        newChats =
                            content.newChats
                                .map { if (it.chatId == event.chatId) it.copy(picked = !it.picked) else it }
                                .toImmutableList(),
                        addError = null,
                    )
                }
            }

            FolderInviteEvent.Add -> {
                add()
            }
        }
    }

    private suspend fun load() {
        mutableUiState.value =
            when (val checked = folderRepository.checkInviteLink(inviteLink)) {
                is Outcome.Success -> checked.value.toContent()
                is Outcome.Failure -> FolderInviteUiState.Failed(checked.error, canRetry = checked.error.canRetry())
            }
    }

    /** The folder with its chats as TDLib knows them; a chat TDLib cannot name is left out of the list. */
    private suspend fun ChatFolderInvite.toContent(): FolderInviteUiState.Content {
        suspend fun chats(ids: List<Long>) =
            coroutineScope {
                ids
                    .map { id -> async { chatRepository.getChat(id).getOrNull() } }
                    .awaitAll()
                    .filterNotNull()
                    .map { chat ->
                        FolderInviteChat(
                            chatId = chat.id,
                            title = chat.title,
                            photo = chat.photo,
                            channel = (chat.type as? ChatType.Supergroup)?.isChannel == true,
                        )
                    }.toImmutableList()
            }
        return FolderInviteUiState.Content(
            title = folder.title.text,
            iconName = folder.iconName,
            isAdded = isAdded,
            newChats = chats(missingChatIds),
            joinedChats = chats(addedChatIds),
        )
    }

    private fun add() {
        val content = mutableUiState.value as? FolderInviteUiState.Content ?: return
        if (!content.canAdd) return
        mutableUiState.value = content.copy(isAdding = true, addError = null)
        viewModelScope.launch {
            // A new folder holds the chats the user is in already too; one they have only gains the picked ones.
            val joined = if (content.isAdded) emptyList() else content.joinedChats.map { it.chatId }
            val picked = content.newChats.filter { it.picked }.map { it.chatId } + joined
            when (val added = folderRepository.addFolderByInviteLink(inviteLink, picked)) {
                is Outcome.Success -> effectChannel.send(FolderInviteEffect.Added)
                is Outcome.Failure -> updateContent { it.copy(isAdding = false, addError = added.error) }
            }
        }
    }

    private fun updateContent(transform: (FolderInviteUiState.Content) -> FolderInviteUiState.Content) =
        mutableUiState.update { state -> if (state is FolderInviteUiState.Content) transform(state) else state }
}

/** Whether checking the link again may help: no network, a flood wait; not for a link that is no longer valid. */
private fun DomainError.canRetry(): Boolean = this == DomainError.Network || this is DomainError.FloodWait
