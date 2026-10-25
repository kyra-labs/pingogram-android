package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Locked chats page shows. */
@Immutable
sealed interface LockedChatsUiState {
    data object Loading : LockedChatsUiState

    /** The passcode lock is off: chats can be locked only behind it. */
    data object NoPasscode : LockedChatsUiState

    /** The locked chats are closed: the passcode entry shows first. */
    data object Locked : LockedChatsUiState

    /** The locked chats, open; [isSaving] while a change is saved. */
    data class Content(
        val chats: ImmutableList<LockedChatRow>,
        val isSaving: Boolean = false,
    ) : LockedChatsUiState
}

/** A locked chat on the page: its [title], [photo] and how many messages are unread. */
@Immutable
data class LockedChatRow(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val unreadCount: Int,
)

/** The picker of chats to lock: the [query] and the chats it finds, those picked [LockedChatCandidate.selected]. */
@Immutable
data class LockedChatsPickerUiState(
    val query: String,
    val candidates: ImmutableList<LockedChatCandidate>,
)

@Immutable
data class LockedChatCandidate(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val selected: Boolean,
)

/** What the user does on the Locked chats page, handled by [LockedChatsViewModel.onEvent]. */
sealed interface LockedChatsEvent {
    /** "Unlock" on a chat: it leaves the locked chats and shows in the chat list again. */
    data class Unlock(
        val chatId: Long,
    ) : LockedChatsEvent

    data object OpenPicker : LockedChatsEvent

    data class PickerQueryChanged(
        val query: String,
    ) : LockedChatsEvent

    data class TogglePicked(
        val chatId: Long,
    ) : LockedChatsEvent

    /** "Done", or the sheet dismissed: locks the chats picked. */
    data object ClosePicker : LockedChatsEvent
}

sealed interface LockedChatsEffect {
    data class ShowError(
        val error: DomainError,
    ) : LockedChatsEffect
}

/**
 * Settings → Privacy and security → Locked chats (`docs/adr/0029-locked-chats.md`): the chats kept behind the passcode,
 * which only works while the passcode lock is on ([LockedChatsUiState.NoPasscode]), and only once they are opened
 * ([LockedChatsUiState.Locked] until then, [ChatLockRepository.unlocked]). Their names come from TDLib's chats, as they
 * change. "Lock a chat" opens a picker of the chat list's and the archive's chats, filtered on the device; closing it
 * locks the chats picked. Whether it is open, its query and its picks survive process death in the [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LockedChatsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val chatLock: ChatLockRepository,
        passcodeRepository: PasscodeRepository,
        private val chatRepository: ChatRepository,
    ) : ViewModel() {
        private val saving = MutableStateFlow(false)
        private val picker = MutableStateFlow(savedPicker())
        private val effectChannel = Channel<LockedChatsEffect>(Channel.BUFFERED)
        val effects: Flow<LockedChatsEffect> = effectChannel.receiveAsFlow()

        private val lockedChats: Flow<List<Chat>> =
            chatLock.lockedChatIds.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(ids.map(chatRepository::chat)) { chats -> chats.filterNotNull() }
                }
            }

        val uiState: StateFlow<LockedChatsUiState> =
            combine(
                passcodeRepository.settings.map { it.isSet }.distinctUntilChanged(),
                chatLock.unlocked,
                lockedChats,
                saving,
            ) { passcode, unlocked, chats, saving ->
                when {
                    !passcode -> LockedChatsUiState.NoPasscode
                    !unlocked -> LockedChatsUiState.Locked
                    else -> LockedChatsUiState.Content(chats.toRows(), saving)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LockedChatsUiState.Loading)

        val pickerState: StateFlow<LockedChatsPickerUiState?> =
            combine(
                picker,
                chatRepository.chatList(ChatListType.Main),
                chatRepository.chatList(ChatListType.Archive),
                chatLock.lockedChatIds,
            ) { draft, main, archive, locked ->
                draft?.let { pickerStateOf(it, main + archive, locked) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun onEvent(event: LockedChatsEvent) {
            when (event) {
                is LockedChatsEvent.Unlock -> save(setOf(event.chatId), locked = false)
                LockedChatsEvent.OpenPicker -> openPicker()
                is LockedChatsEvent.PickerQueryChanged -> setPicker(picker.value?.copy(query = event.query))
                is LockedChatsEvent.TogglePicked -> setPicker(picker.value?.toggled(event.chatId))
                LockedChatsEvent.ClosePicker -> closePicker()
            }
        }

        private fun openPicker() {
            if (saving.value || !chatLock.unlocked.value) return
            setPicker(PickerDraft(query = "", picked = emptyList()))
            viewModelScope.launch { chatRepository.loadChats(ChatListType.Main) }
        }

        private fun closePicker() {
            val draft = picker.value ?: return
            setPicker(null)
            if (draft.picked.isNotEmpty()) save(draft.picked.toSet(), locked = true)
        }

        private fun save(
            chatIds: Set<Long>,
            locked: Boolean,
        ) {
            if (saving.value) return
            saving.value = true
            viewModelScope.launch {
                val outcome = chatLock.setLocked(chatIds, locked)
                if (outcome is Outcome.Failure && outcome.error != DomainError.Silent) {
                    effectChannel.trySend(LockedChatsEffect.ShowError(outcome.error))
                }
                saving.value = false
            }
        }

        private fun setPicker(draft: PickerDraft?) {
            picker.value = draft
            savedStateHandle[KEY_PICKER_OPEN] = draft != null
            savedStateHandle[KEY_PICKER_QUERY] = draft?.query
            savedStateHandle[KEY_PICKER_CHATS] = draft?.picked?.toLongArray()
        }

        private fun savedPicker(): PickerDraft? =
            if (savedStateHandle.get<Boolean>(KEY_PICKER_OPEN) == true) {
                PickerDraft(
                    query = savedStateHandle[KEY_PICKER_QUERY] ?: "",
                    picked = savedStateHandle.get<LongArray>(KEY_PICKER_CHATS)?.asList().orEmpty(),
                )
            } else {
                null
            }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val KEY_PICKER_OPEN = "lockedChats.picker"
            const val KEY_PICKER_QUERY = "lockedChats.query"
            const val KEY_PICKER_CHATS = "lockedChats.chats"
        }
    }

/** What the picker holds while it shows: the [query] and the chats [picked]. */
private data class PickerDraft(
    val query: String,
    val picked: List<Long>,
) {
    fun toggled(chatId: Long) = copy(picked = if (chatId in picked) picked - chatId else picked + chatId)
}

private fun List<Chat>.toRows(): ImmutableList<LockedChatRow> =
    map { LockedChatRow(it.id, it.title, it.photo, it.unreadCount) }.toImmutableList()

/** The chats that may be locked: those of [chats] not locked yet whose title matches the query. */
private fun pickerStateOf(
    draft: PickerDraft,
    chats: List<Chat>,
    locked: Set<Long>,
): LockedChatsPickerUiState {
    val needle = draft.query.trim()
    val candidates =
        chats
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id !in locked }
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .map { LockedChatCandidate(it.id, it.title, it.photo, selected = it.id in draft.picked) }
            .toList()
    return LockedChatsPickerUiState(draft.query, candidates.toImmutableList())
}
