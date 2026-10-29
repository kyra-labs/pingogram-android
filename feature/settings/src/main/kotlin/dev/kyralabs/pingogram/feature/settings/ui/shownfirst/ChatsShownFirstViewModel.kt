package dev.kyralabs.pingogram.feature.settings.ui.shownfirst

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
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Chats shown first page shows. */
@Immutable
sealed interface ChatsShownFirstUiState {
    data object Loading : ChatsShownFirstUiState

    /** The chats in their order; [canAdd] while there are fewer than the most; [isSaving] while a change is saved. */
    data class Content(
        val chats: ImmutableList<ShownFirstRow>,
        val canAdd: Boolean,
        val isSaving: Boolean = false,
    ) : ChatsShownFirstUiState
}

/** A chat shown first: its [title] and [photo]. */
@Immutable
data class ShownFirstRow(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
)

/** The picker: the [query], the chats it finds, those picked [ShownFirstCandidate.selected], and how many fit. */
@Immutable
data class ShownFirstPickerUiState(
    val query: String,
    val candidates: ImmutableList<ShownFirstCandidate>,
    val slots: Int,
)

@Immutable
data class ShownFirstCandidate(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val selected: Boolean,
)

/** What the user does on the page, handled by [ChatsShownFirstViewModel.onEvent]. */
sealed interface ChatsShownFirstEvent {
    data class Remove(
        val chatId: Long,
    ) : ChatsShownFirstEvent

    /** Moves the chat one place up ([up]) or down: screen readers' "Move up" and "Move down". */
    data class Move(
        val chatId: Long,
        val up: Boolean,
    ) : ChatsShownFirstEvent

    data object OpenPicker : ChatsShownFirstEvent

    data class PickerQueryChanged(
        val query: String,
    ) : ChatsShownFirstEvent

    data class TogglePicked(
        val chatId: Long,
    ) : ChatsShownFirstEvent

    /** "Done", or the sheet dismissed: adds the chats picked after those there. */
    data object ClosePicker : ChatsShownFirstEvent
}

sealed interface ChatsShownFirstEffect {
    data class ShowError(
        val error: DomainError,
    ) : ChatsShownFirstEffect
}

/**
 * Settings → Appearance → Chats → "Chats shown first": the chats the forward and share sheets offer first, after Saved
 * Messages ([ChatsShownFirstRepository], at most [ChatsShownFirstRepository.MAX]). Their names come from TDLib's chats,
 * as they change; a chat TDLib no longer knows is left out until it is again. "Add a chat" opens a picker of the main
 * chat list's chats, filtered on the device, which takes as many as there are places left; closing it adds them at the
 * end. Each change is saved at once. Whether the picker is open, its query and its picks survive process death in the
 * [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsShownFirstViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val shownFirst: ChatsShownFirstRepository,
        private val chatRepository: ChatRepository,
    ) : ViewModel() {
        private val saving = MutableStateFlow(false)
        private val picker = MutableStateFlow(savedPicker())
        private val effectChannel = Channel<ChatsShownFirstEffect>(Channel.BUFFERED)
        val effects: Flow<ChatsShownFirstEffect> = effectChannel.receiveAsFlow()

        /** The ids as last read, for the changes made from them. */
        private val currentIds: StateFlow<List<Long>> =
            shownFirst.chatIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val ids: List<Long> get() = currentIds.value

        private val chats: Flow<List<Chat>> =
            shownFirst.chatIds.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(ids.map(chatRepository::chat)) { chats -> chats.filterNotNull() }
                }
            }

        val uiState: StateFlow<ChatsShownFirstUiState> =
            combine(chats, saving) { chats, saving ->
                ChatsShownFirstUiState.Content(
                    chats = chats.map { ShownFirstRow(it.id, it.title, it.photo) }.toImmutableList(),
                    canAdd = chats.size < ChatsShownFirstRepository.MAX,
                    isSaving = saving,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ChatsShownFirstUiState.Loading,
            )

        val pickerState: StateFlow<ShownFirstPickerUiState?> =
            combine(picker, chatRepository.chatList(ChatListType.Main), shownFirst.chatIds) { draft, main, shown ->
                draft?.let { pickerStateOf(it, main, shown) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun onEvent(event: ChatsShownFirstEvent) {
            when (event) {
                is ChatsShownFirstEvent.Remove -> save(ids - event.chatId)
                is ChatsShownFirstEvent.Move -> move(event.chatId, event.up)
                ChatsShownFirstEvent.OpenPicker -> openPicker()
                is ChatsShownFirstEvent.PickerQueryChanged -> setPicker(picker.value?.copy(query = event.query))
                is ChatsShownFirstEvent.TogglePicked -> toggle(event.chatId)
                ChatsShownFirstEvent.ClosePicker -> closePicker()
            }
        }

        private fun move(
            chatId: Long,
            up: Boolean,
        ) {
            val index = ids.indexOf(chatId)
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            save(ids.toMutableList().apply { add(target, removeAt(index)) })
        }

        private fun toggle(chatId: Long) {
            val draft = picker.value ?: return
            val slots = ChatsShownFirstRepository.MAX - ids.size
            // Only as many as there are places left; untoggling always works.
            if (chatId !in draft.picked && draft.picked.size >= slots) return
            setPicker(draft.toggled(chatId))
        }

        private fun openPicker() {
            if (saving.value || ids.size >= ChatsShownFirstRepository.MAX) return
            setPicker(PickerDraft(query = "", picked = emptyList()))
            viewModelScope.launch { chatRepository.loadChats(ChatListType.Main) }
        }

        private fun closePicker() {
            val draft = picker.value ?: return
            setPicker(null)
            if (draft.picked.isNotEmpty()) save(ids + draft.picked)
        }

        private fun save(chatIds: List<Long>) {
            if (saving.value) return
            saving.value = true
            viewModelScope.launch {
                val outcome = shownFirst.setChatIds(chatIds)
                if (outcome is Outcome.Failure) effectChannel.trySend(ChatsShownFirstEffect.ShowError(outcome.error))
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
            const val KEY_PICKER_OPEN = "shownFirst.picker"
            const val KEY_PICKER_QUERY = "shownFirst.query"
            const val KEY_PICKER_CHATS = "shownFirst.chats"
        }
    }

/** What the picker holds while it shows: the [query] and the chats [picked], in the order picked. */
private data class PickerDraft(
    val query: String,
    val picked: List<Long>,
) {
    fun toggled(chatId: Long) = copy(picked = if (chatId in picked) picked - chatId else picked + chatId)
}

/** The chats that may be added: those of [chats] not shown first yet whose title matches the query. */
private fun pickerStateOf(
    draft: PickerDraft,
    chats: List<Chat>,
    shown: List<Long>,
): ShownFirstPickerUiState {
    val needle = draft.query.trim()
    val candidates =
        chats
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id !in shown }
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .map { ShownFirstCandidate(it.id, it.title, it.photo, selected = it.id in draft.picked) }
            .toList()
    return ShownFirstPickerUiState(
        query = draft.query,
        candidates = candidates.toImmutableList(),
        slots = ChatsShownFirstRepository.MAX - shown.size,
    )
}
