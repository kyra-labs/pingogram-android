package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import dev.kyralabs.pingogram.core.navigation.FocusProfileRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * A Focus profile's editor (board 31): profile [profileId], or a new one from [template]. The draft lives in the saved
 * state, so it survives process death; nothing is saved before "Save", which needs a name and, on a schedule, a day.
 * The folders it may let through are the user's chat folders, followed while the page shows.
 */
@HiltViewModel
class FocusEditViewModel internal constructor(
    private val profileId: Long,
    private val template: FocusTemplate?,
    private val repository: FocusRepository,
    folders: Flow<ImmutableList<ChatFolder>>,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: FocusRepository,
        chatRepository: ChatRepository,
    ) : this(
        savedStateHandle.toRoute<FocusProfileRoute>().profileId,
        savedStateHandle.toRoute<FocusProfileRoute>().template?.let { name ->
            FocusTemplate.entries.firstOrNull { it.name == name }
        },
        repository,
        chatRepository.folders,
        savedStateHandle,
    )

    private val mutableUiState =
        MutableStateFlow(
            FocusEditUiState(
                draft = savedStateHandle.restoredDraft(),
                isNew = profileId == 0L,
                askDelete = savedStateHandle[KEY_ASK_DELETE] ?: false,
            ),
        )
    val uiState: StateFlow<FocusEditUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<FocusEditEffect>(Channel.BUFFERED)
    val effects: Flow<FocusEditEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (uiState.value.draft == null) {
                val profile =
                    if (profileId == 0L) {
                        (template ?: FocusTemplate.Blank).profile(name = "")
                    } else {
                        repository.profiles.first().firstOrNull { it.id == profileId }
                    }
                if (profile == null) {
                    // Deleted meanwhile (another window): nothing to edit.
                    effectChannel.trySend(FocusEditEffect.Done)
                } else {
                    setDraft(profile.toDraft())
                }
            }
        }
        viewModelScope.launch {
            folders.collect { list ->
                mutableUiState.update { state ->
                    state.copy(folders = list.map { FocusFolderChoice(it.id, it.title.text) }.toImmutableList())
                }
            }
        }
    }

    fun onEvent(event: FocusEditEvent) {
        when (event) {
            is FocusEditEvent.Prefill -> {
                if (savedStateHandle.get<Boolean>(KEY_PREFILLED) != true) {
                    savedStateHandle[KEY_PREFILLED] = true
                    if (template != null && template != FocusTemplate.Blank) edit { it.copy(name = event.name) }
                }
            }

            is FocusEditEvent.Name -> {
                mutableUiState.update { it.copy(nameMissing = false) }
                edit { it.copy(name = event.name.take(NAME_MAX_LENGTH)) }
            }

            is FocusEditEvent.Icon -> {
                edit { it.copy(icon = event.icon) }
            }

            is FocusEditEvent.Folder -> {
                edit { it.copy(folderIds = it.folderIds.toggled(event.id, event.allowed)) }
            }

            is FocusEditEvent.Scheduled -> {
                edit { it.copy(scheduled = event.scheduled) }
            }

            is FocusEditEvent.Day -> {
                edit { it.copy(days = it.days.toggled(event.day, event.on)) }
            }

            is FocusEditEvent.Calls -> {
                edit { it.copy(allowsCalls = event.allowed) }
            }

            is FocusEditEvent.Pick -> {
                mutableUiState.update { it.copy(picking = event.end) }
            }

            is FocusEditEvent.Time -> {
                setTime(event)
            }

            FocusEditEvent.Save -> {
                save()
            }

            FocusEditEvent.Delete -> {
                setAskDelete(true)
            }

            FocusEditEvent.DeleteDismissed -> {
                setAskDelete(false)
            }

            FocusEditEvent.DeleteConfirmed -> {
                delete()
            }
        }
    }

    private fun setTime(event: FocusEditEvent.Time) {
        mutableUiState.update { it.copy(picking = null) }
        edit { draft ->
            when (event.end) {
                FocusTimeEnd.Start -> draft.copy(startMinute = event.minute)
                FocusTimeEnd.End -> draft.copy(endMinute = event.minute)
            }
        }
    }

    private fun save() {
        val state = uiState.value
        val draft = state.draft?.takeUnless { state.isBusy } ?: return
        val name = draft.name.trim()
        if (name.isEmpty() || (draft.scheduled && draft.days.isEmpty())) {
            mutableUiState.update { it.copy(nameMissing = name.isEmpty()) }
            effectChannel.trySend(FocusEditEffect.Rejected)
        } else {
            busy { repository.saveProfile(draft.copy(name = name).toProfile(profileId)) }
        }
    }

    private fun delete() {
        setAskDelete(false)
        if (profileId != 0L) busy { repository.deleteProfile(profileId) }
    }

    private fun busy(action: suspend () -> Outcome<*>) {
        mutableUiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = action()) {
                is Outcome.Success -> {
                    effectChannel.trySend(FocusEditEffect.Done)
                }

                is Outcome.Failure -> {
                    mutableUiState.update { it.copy(isBusy = false) }
                    effectChannel.trySend(FocusEditEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun setAskDelete(ask: Boolean) {
        savedStateHandle[KEY_ASK_DELETE] = ask
        mutableUiState.update { it.copy(askDelete = ask) }
    }

    private fun edit(change: (FocusDraft) -> FocusDraft) {
        uiState.value.draft?.let { setDraft(change(it)) }
    }

    private fun setDraft(draft: FocusDraft) {
        savedStateHandle.saveDraft(draft)
        mutableUiState.update { it.copy(draft = draft) }
    }

    private companion object {
        const val KEY_PREFILLED = "focus.prefilled"
        const val KEY_ASK_DELETE = "focus.askDelete"
    }
}

/** The longest name a profile takes. */
internal const val NAME_MAX_LENGTH = 32

private fun <T> Set<T>.toggled(
    value: T,
    on: Boolean,
) = (if (on) this + value else this - value).toPersistentSet()

private fun FocusProfile.toDraft(): FocusDraft {
    val schedule = schedule
    return FocusDraft(
        name = name,
        icon = icon,
        folderIds = allowedFolderIds.toPersistentSet(),
        chatIds = allowedChatIds,
        scheduled = schedule != null,
        days = (schedule?.days ?: DayOfWeek.entries.toSet()).toPersistentSet(),
        startMinute = schedule?.startMinute ?: DEFAULT_START,
        endMinute = schedule?.endMinute ?: DEFAULT_END,
        allowsCalls = allowsCallsFromContacts,
    )
}

private fun FocusDraft.toProfile(id: Long) =
    FocusProfile(
        id = id,
        name = name,
        icon = icon,
        allowedFolderIds = folderIds.sorted().toImmutableList(),
        allowedChatIds = chatIds,
        schedule = if (scheduled) FocusSchedule(days, startMinute, endMinute) else null,
        allowsCallsFromContacts = allowsCalls,
    )

// The draft in the saved state, as plain values.
private const val KEY_DRAFT = "focus.draft"
private const val KEY_NAME = "focus.name"
private const val KEY_ICON = "focus.icon"
private const val KEY_FOLDERS = "focus.folders"
private const val KEY_CHATS = "focus.chats"
private const val KEY_SCHEDULED = "focus.scheduled"
private const val KEY_DAYS = "focus.days"
private const val KEY_START = "focus.start"
private const val KEY_END = "focus.end"
private const val KEY_CALLS = "focus.calls"
private const val DEFAULT_START = 9 * 60
private const val DEFAULT_END = 18 * 60

private fun SavedStateHandle.saveDraft(draft: FocusDraft) {
    this[KEY_DRAFT] = true
    this[KEY_NAME] = draft.name
    this[KEY_ICON] = draft.icon.name
    this[KEY_FOLDERS] = draft.folderIds.toIntArray()
    this[KEY_CHATS] = draft.chatIds.toLongArray()
    this[KEY_SCHEDULED] = draft.scheduled
    this[KEY_DAYS] = draft.days.map { it.value }.toIntArray()
    this[KEY_START] = draft.startMinute
    this[KEY_END] = draft.endMinute
    this[KEY_CALLS] = draft.allowsCalls
}

private fun SavedStateHandle.restoredDraft(): FocusDraft? {
    if (get<Boolean>(KEY_DRAFT) != true) return null
    return FocusDraft(
        name = get<String>(KEY_NAME).orEmpty(),
        icon = FocusIcon.entries.firstOrNull { it.name == get<String>(KEY_ICON) } ?: FocusIcon.Work,
        folderIds = (get<IntArray>(KEY_FOLDERS) ?: IntArray(0)).toList().toPersistentSet(),
        chatIds = (get<LongArray>(KEY_CHATS) ?: LongArray(0)).toList().toImmutableList(),
        scheduled = get<Boolean>(KEY_SCHEDULED) ?: false,
        days = (get<IntArray>(KEY_DAYS) ?: IntArray(0)).map(DayOfWeek::of).toPersistentSet(),
        startMinute = get<Int>(KEY_START) ?: DEFAULT_START,
        endMinute = get<Int>(KEY_END) ?: DEFAULT_END,
        allowsCalls = get<Boolean>(KEY_CALLS) ?: true,
    )
}
