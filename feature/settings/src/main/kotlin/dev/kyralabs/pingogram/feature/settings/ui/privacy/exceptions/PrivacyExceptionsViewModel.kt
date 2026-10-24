package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionLists
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.PrivacyExceptionsRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One side ([side]) of a privacy [setting]'s exceptions: the users and groups it always or never allows, as TDLib
 * lists them, read again whenever the setting changes.
 *
 * - **Remove** takes one out at once and saves it; a refusal brings it back with the error.
 * - **The picker** offers the people and groups of the main chat list (Saved Messages, channels and secret chats left
 *   out), filtered on the device, starting from this side's users and groups. Closing it saves what is picked, which
 *   leaves the other side, as one user or group cannot be both. Whether it is open, its query and its picks survive
 *   process death in the [SavedStateHandle].
 * - Names come from TDLib's users and chats, asked for once where TDLib has not announced them.
 */
@HiltViewModel
class PrivacyExceptionsViewModel internal constructor(
    private val setting: PrivacySetting,
    private val side: PrivacyExceptionSide,
    private val savedStateHandle: SavedStateHandle,
    private val privacyRepository: PrivacyRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        privacyRepository: PrivacyRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
    ) : this(
        setting = savedStateHandle.toRoute<PrivacyExceptionsRoute>().privacySetting(),
        side = if (savedStateHandle.toRoute<PrivacyExceptionsRoute>().always) ALWAYS else NEVER,
        savedStateHandle = savedStateHandle,
        privacyRepository = privacyRepository,
        chatRepository = chatRepository,
        userRepository = userRepository,
    )

    private val lists = MutableStateFlow<PrivacyExceptionLists?>(null)
    private val loadError = MutableStateFlow<DomainError?>(null)
    private val saving = MutableStateFlow(false)
    private val knownChats = MutableStateFlow(emptyMap<Long, Chat>())
    private val knownUsers = MutableStateFlow(emptyMap<Long, User>())
    private val asked = mutableSetOf<ExceptionKey>()
    private val picker = MutableStateFlow(savedPicker())

    private val effectChannel = Channel<PrivacyExceptionsEffect>(Channel.BUFFERED)
    val effects: Flow<PrivacyExceptionsEffect> = effectChannel.receiveAsFlow()

    private val users = combine(userRepository.users, knownUsers) { announced, known -> known + announced }

    val uiState: StateFlow<PrivacyExceptionsUiState> =
        combine(lists, loadError, saving, users, knownChats) { lists, error, saving, users, chats ->
            when {
                lists != null -> {
                    PrivacyExceptionsUiState.Content(entriesOf(lists, users, chats), isSaving = saving)
                }

                error != null -> {
                    PrivacyExceptionsUiState.Error(error)
                }

                else -> {
                    PrivacyExceptionsUiState.Loading
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PrivacyExceptionsUiState.Loading)

    val pickerState: StateFlow<ExceptionPickerUiState?> =
        combine(picker, chatRepository.chatList(ChatListType.Main), userRepository.myUserId) { draft, chats, me ->
            draft?.let { pickerStateOf(it, chats, me) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        privacyRepository.changes
            .filter { it.setting == setting }
            .onEach { if (!saving.value) load() }
            .launchIn(viewModelScope)
        load()
    }

    fun onEvent(event: PrivacyExceptionsEvent) {
        when (event) {
            PrivacyExceptionsEvent.Retry -> load()
            is PrivacyExceptionsEvent.Remove -> remove(event.key)
            PrivacyExceptionsEvent.OpenPicker -> openPicker()
            is PrivacyExceptionsEvent.PickerQueryChanged -> setPicker(picker.value?.copy(query = event.query))
            is PrivacyExceptionsEvent.TogglePicked -> setPicker(picker.value?.toggled(event.key))
            PrivacyExceptionsEvent.ClosePicker -> closePicker()
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val loaded = privacyRepository.exceptions(setting)) {
                is Outcome.Success -> {
                    lists.value = loaded.value
                    loadError.value = null
                    askForNames(loaded.value)
                }

                is Outcome.Failure -> {
                    if (lists.value == null) loadError.value = loaded.error
                }
            }
        }
    }

    /** Asks TDLib once for each user and group of [lists] it has not announced, so their names show. */
    private fun askForNames(lists: PrivacyExceptionLists) {
        val keys =
            lists.userIds(side).map { ExceptionKey(it, false) } + lists.chatIds(side).map { ExceptionKey(it, true) }
        keys.filter(asked::add).forEach { key ->
            viewModelScope.launch {
                if (key.isGroup) {
                    val chat = chatRepository.getChat(key.id)
                    if (chat is Outcome.Success) knownChats.update { it + (key.id to chat.value) }
                } else {
                    val user = userRepository.getUser(key.id)
                    if (user is Outcome.Success) knownUsers.update { it + (key.id to user.value) }
                }
            }
        }
    }

    private fun remove(key: ExceptionKey) {
        val current = lists.value ?: return
        val userIds = current.userIds(side).filterNot { !key.isGroup && it == key.id }
        val chatIds = current.chatIds(side).filterNot { key.isGroup && it == key.id }
        save(current.with(side, userIds, chatIds))
    }

    private fun openPicker() {
        val current = lists.value ?: return
        if (saving.value) return
        setPicker(PickerDraft(query = "", userIds = current.userIds(side), chatIds = current.chatIds(side)))
        viewModelScope.launch { chatRepository.loadChats(ChatListType.Main) }
    }

    private fun closePicker() {
        val draft = picker.value ?: return
        setPicker(null)
        val current = lists.value ?: return
        val picked = current.with(side, draft.userIds, draft.chatIds)
        if (picked != current) save(picked)
    }

    /** Shows [changed] at once and saves it; one change at a time. */
    private fun save(changed: PrivacyExceptionLists) {
        val before = lists.value ?: return
        if (saving.value || changed == before) return
        lists.value = changed
        saving.value = true
        askForNames(changed)
        viewModelScope.launch {
            val outcome = privacyRepository.setExceptions(setting, changed)
            if (outcome is Outcome.Failure) {
                lists.value = before
                if (outcome.error != DomainError.Silent) {
                    effectChannel.trySend(PrivacyExceptionsEffect.ShowError(outcome.error))
                }
            }
            saving.value = false
        }
    }

    private fun setPicker(draft: PickerDraft?) {
        picker.value = draft
        savedStateHandle[KEY_PICKER_OPEN] = draft != null
        savedStateHandle[KEY_PICKER_QUERY] = draft?.query
        savedStateHandle[KEY_PICKER_USERS] = draft?.userIds?.toLongArray()
        savedStateHandle[KEY_PICKER_CHATS] = draft?.chatIds?.toLongArray()
    }

    private fun savedPicker(): PickerDraft? =
        if (savedStateHandle.get<Boolean>(KEY_PICKER_OPEN) == true) {
            PickerDraft(
                query = savedStateHandle[KEY_PICKER_QUERY] ?: "",
                userIds = savedStateHandle.get<LongArray>(KEY_PICKER_USERS)?.asList().orEmpty(),
                chatIds = savedStateHandle.get<LongArray>(KEY_PICKER_CHATS)?.asList().orEmpty(),
            )
        } else {
            null
        }

    private fun entriesOf(
        lists: PrivacyExceptionLists,
        users: Map<Long, User>,
        chats: Map<Long, Chat>,
    ) = (
        lists.userIds(side).map { id -> users[id].toEntry(id) } +
            lists.chatIds(side).map { id -> chats[id].toEntry(id) }
    ).toImmutableList()

    private companion object {
        val ALWAYS = PrivacyExceptionSide.Always
        val NEVER = PrivacyExceptionSide.Never
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_PICKER_OPEN = "privacyExceptions.picker"
        const val KEY_PICKER_QUERY = "privacyExceptions.query"
        const val KEY_PICKER_USERS = "privacyExceptions.users"
        const val KEY_PICKER_CHATS = "privacyExceptions.chats"
    }
}

/** What the picker holds while it shows: the [query] and the users and groups picked. */
private data class PickerDraft(
    val query: String,
    val userIds: List<Long>,
    val chatIds: List<Long>,
) {
    fun toggled(key: ExceptionKey): PickerDraft =
        if (key.isGroup) {
            copy(chatIds = if (key.id in chatIds) chatIds - key.id else chatIds + key.id)
        } else {
            copy(userIds = if (key.id in userIds) userIds - key.id else userIds + key.id)
        }

    operator fun contains(key: ExceptionKey) = if (key.isGroup) key.id in chatIds else key.id in userIds
}

/** The picker's people and groups: the main list's private chats (not Saved Messages) and groups matching the query. */
private fun pickerStateOf(
    draft: PickerDraft,
    chats: List<Chat>,
    myUserId: Long?,
): ExceptionPickerUiState {
    val needle = draft.query.trim()
    val candidates =
        chats
            .mapNotNull { chat ->
                val key =
                    when (val type = chat.type) {
                        is ChatType.Private -> {
                            ExceptionKey(type.userId, isGroup = false).takeIf {
                                type.userId !=
                                    myUserId
                            }
                        }

                        is ChatType.BasicGroup -> {
                            ExceptionKey(chat.id, isGroup = true)
                        }

                        is ChatType.Supergroup -> {
                            ExceptionKey(chat.id, isGroup = true).takeIf { !type.isChannel }
                        }

                        is ChatType.Secret -> {
                            null
                        }
                    }
                key?.takeIf { needle.isEmpty() || chat.title.contains(needle, ignoreCase = true) }?.let {
                    ExceptionCandidate(
                        entry = ExceptionEntry(it, UiText.Plain(chat.title), chat.photo),
                        isGroup = it.isGroup,
                        selected = it in draft,
                    )
                }
            }.distinctBy { it.entry.key }
    return ExceptionPickerUiState(draft.query, candidates.toImmutableList())
}

private fun User?.toEntry(id: Long) =
    ExceptionEntry(
        key = ExceptionKey(id, isGroup = false),
        title = this?.fullName() ?: UiText.resource(UiR.string.user_deleted_account),
        photo = this?.profilePhoto,
    )

private fun Chat?.toEntry(id: Long) =
    ExceptionEntry(
        key = ExceptionKey(id, isGroup = true),
        title = UiText.Plain(this?.title.orEmpty()),
        photo = this?.photo,
    )

/** The setting a route names; one this version does not know reads as last seen, which every account has. */
private fun PrivacyExceptionsRoute.privacySetting(): PrivacySetting =
    PrivacySetting.entries.firstOrNull { it.name == setting } ?: PrivacySetting.LastSeen
