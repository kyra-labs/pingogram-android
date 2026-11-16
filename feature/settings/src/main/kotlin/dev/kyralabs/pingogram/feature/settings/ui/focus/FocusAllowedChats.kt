package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Also allow" of the Focus profile editor (board 31), a part of [FocusEditViewModel]: the chats the draft lets
 * through whatever their folder, named from [chats] ([allowed]), and the picker of the main list's other chats
 * ([picker]), whose picks join the draft's ([draftChatIds], [setDraftChatIds]) as it closes. Whether the picker shows,
 * its query and its picks survive process death in [savedStateHandle].
 */
internal class FocusAllowedChats(
    private val chats: ChatRepository,
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val draftChatIds: () -> ImmutableList<Long>?,
    private val setDraftChatIds: (ImmutableList<Long>) -> Unit,
) {
    private val pickerDraft = MutableStateFlow(savedPicker())

    /** The chats [chatIds] names, in their order, those TDLib has not announced left out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun allowed(chatIds: Flow<List<Long>>): Flow<ImmutableList<FocusChatChoice>> =
        chatIds.distinctUntilChanged().flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(persistentListOf())
            } else {
                combine(ids.map(chats::chat)) { found -> found.filterNotNull().map { it.toChoice() }.toImmutableList() }
            }
        }

    /** The picker while it shows: the main list's chats not allowed yet whose title has the query. */
    val picker: StateFlow<FocusChatPickerUiState?> =
        combine(pickerDraft, chats.chatList(ChatListType.Main)) { draft, main ->
            draft?.let { pickerStateOf(it, main, draftChatIds().orEmpty()) }
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onEvent(event: FocusChatEvent) {
        when (event) {
            FocusChatEvent.Add -> {
                setPicker(PickerDraft(query = "", picked = emptyList()))
                scope.launch { chats.loadChats(ChatListType.Main) }
            }

            is FocusChatEvent.Remove -> {
                draftChatIds()?.let { ids -> setDraftChatIds((ids - event.chatId).toImmutableList()) }
            }

            is FocusChatEvent.Query -> {
                setPicker(pickerDraft.value?.copy(query = event.query))
            }

            is FocusChatEvent.Toggle -> {
                setPicker(pickerDraft.value?.toggled(event.chatId))
            }

            FocusChatEvent.Close -> {
                close()
            }
        }
    }

    private fun close() {
        val draft = pickerDraft.value ?: return
        setPicker(null)
        val ids = draftChatIds() ?: return
        val added = draft.picked.filterNot { it in ids }
        if (added.isNotEmpty()) setDraftChatIds((ids + added).toImmutableList())
    }

    private fun setPicker(draft: PickerDraft?) {
        pickerDraft.value = draft
        savedStateHandle[KEY_OPEN] = draft != null
        savedStateHandle[KEY_QUERY] = draft?.query
        savedStateHandle[KEY_PICKED] = draft?.picked?.toLongArray()
    }

    private fun savedPicker(): PickerDraft? =
        if (savedStateHandle.get<Boolean>(KEY_OPEN) == true) {
            PickerDraft(
                query = savedStateHandle[KEY_QUERY] ?: "",
                picked = savedStateHandle.get<LongArray>(KEY_PICKED)?.asList().orEmpty(),
            )
        } else {
            null
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_OPEN = "focus.chatPicker"
        const val KEY_QUERY = "focus.chatPickerQuery"
        const val KEY_PICKED = "focus.chatPickerPicked"
    }
}

/** What the picker holds while it shows: the [query] and the chats [picked], in the order picked. */
private data class PickerDraft(
    val query: String,
    val picked: List<Long>,
) {
    fun toggled(chatId: Long) = copy(picked = if (chatId in picked) picked - chatId else picked + chatId)
}

private fun Chat.toChoice() = FocusChatChoice(id, title, photo)

/** The chats that may be added: those of [main] not allowed yet ([allowed]) whose title has the query. */
private fun pickerStateOf(
    draft: PickerDraft,
    main: List<Chat>,
    allowed: List<Long>,
): FocusChatPickerUiState {
    val needle = draft.query.trim()
    val candidates =
        main
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id !in allowed }
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .map { FocusChatCandidate(it.toChoice(), selected = it.id in draft.picked) }
            .toList()
    return FocusChatPickerUiState(draft.query, candidates.toImmutableList())
}
