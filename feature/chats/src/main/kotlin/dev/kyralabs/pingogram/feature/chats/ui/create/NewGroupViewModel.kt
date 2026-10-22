package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Creating a group (`REQUIREMENTS.md` FR-P4), one view model for both steps of the flow's nested graph
 * (`NewGroupRoute`): picking people ([members], a [MemberPicker]), then the group's details ([details]) and creating
 * it. Each step has its own effects ([memberEffects], [detailsEffects]), so an effect never reaches the step that is
 * leaving.
 *
 * - **Details**: the title (required, at most 128 characters), the photo ([NewChatPhoto]) and how long messages stay.
 *   Everything typed and picked is kept in the [SavedStateHandle], so it survives process death.
 * - **Creating** ([ChatCreationRepository.createGroup]) sends what the user picked, once: "Create" waits meanwhile.
 *   A missing or refused title shows at its field, `FLOOD_WAIT` counts down, anything else is a snackbar.
 * - **Done**: the new group opens ([CreateChatEffect.OpenChat]). If Telegram did not add some of the people, a dialog
 *   says who first and offers to share the group's invite link with them ([NotAddedDialog]); it survives process death.
 *
 * It takes the repositories, the clock for the contacts' online dots, and the dispatcher and language that tests
 * replace, hence `LongParameterList` is suppressed; it has a handler for each event of its steps and dialog and the
 * requests they run, hence `TooManyFunctions`.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class NewGroupViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatCreationRepository,
    private val userRepository: UserRepository,
    contactRepository: ContactRepository,
    clock: WallClock,
    dispatcher: CoroutineDispatcher,
    locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: ChatCreationRepository,
        userRepository: UserRepository,
        contactRepository: ContactRepository,
        clock: WallClock,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(savedStateHandle, repository, userRepository, contactRepository, clock, dispatcher, Locale::getDefault)

    private val memberEffectChannel = Channel<CreateChatEffect>(Channel.BUFFERED)
    private val detailsEffectChannel = Channel<CreateChatEffect>(Channel.BUFFERED)

    /** One-off effects for the first step: snackbars. */
    val memberEffects: Flow<CreateChatEffect> = memberEffectChannel.receiveAsFlow()

    /** One-off effects for the last step: opening the new group, sharing its link, and snackbars. */
    val detailsEffects: Flow<CreateChatEffect> = detailsEffectChannel.receiveAsFlow()

    private val picker =
        MemberPicker(
            scope = viewModelScope,
            savedStateHandle = savedStateHandle,
            contactRepository = contactRepository,
            clock = clock,
            dispatcher = dispatcher,
            locale = locale,
            keyPrefix = KEY_PREFIX,
            onTooMany = { memberEffectChannel.trySend(CreateChatEffect.TooManyMembers) },
        )

    /** The first step: the contacts, the search and the picks. */
    val members: StateFlow<MemberPickerUiState> = picker.state

    private val photo = NewChatPhoto(viewModelScope, savedStateHandle, repository, KEY_PHOTO)

    private val form =
        MutableStateFlow(
            NewGroupDetailsUiState(
                title = savedStateHandle[KEY_TITLE] ?: "",
                autoDelete =
                    savedStateHandle.get<String>(KEY_AUTO_DELETE)?.let { name ->
                        MessageAutoDelete.entries.firstOrNull { it.name == name }
                    } ?: MessageAutoDelete.Off,
            ),
        )

    /** The last step: the group's details and its creation. */
    val details: StateFlow<NewGroupDetailsUiState> =
        combine(form, photo.state, picker.picked) { form, photo, picked ->
            form.copy(photo = photo, memberCount = picked.size)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value.copy(photo = photo.state.value))

    private var floodWaitJob: Job? = null

    init {
        // A group created before process death whose dialog was still open: it shows again.
        val createdChatId = savedStateHandle.get<Long>(KEY_CREATED_CHAT)
        if (createdChatId != null) {
            val notAdded = savedStateHandle.get<LongArray>(KEY_NOT_ADDED)?.toList().orEmpty()
            viewModelScope.launch { finish(createdChatId, notAdded) }
        }
    }

    fun onMembersEvent(event: MemberPickerEvent) = picker.onEvent(event)

    fun onEvent(event: ChatDetailsEvent) {
        when (event) {
            is ChatDetailsEvent.TitleChanged -> editTitle(event.value)

            is ChatDetailsEvent.AutoDeleteChanged -> setAutoDelete(event.autoDelete)

            is ChatDetailsEvent.PhotoPicked -> if (!isLocked()) photo.pick(event.uri, ::showError)

            ChatDetailsEvent.RemovePhoto -> if (!isLocked()) photo.remove()

            ChatDetailsEvent.Create -> create()

            // Groups have no description here.
            is ChatDetailsEvent.DescriptionChanged -> Unit
        }
    }

    fun onNotAddedEvent(event: NotAddedEvent) {
        val dialog = form.value.notAdded ?: return
        when (event) {
            NotAddedEvent.ShareLink -> shareLink(dialog)
            NotAddedEvent.Dismiss -> openChat(dialog.chatId)
        }
    }

    /** Nothing changes once creating started: the chat may already exist. */
    private fun isLocked(): Boolean = form.value.isCreating || savedStateHandle.contains(KEY_CREATED_CHAT)

    private fun editTitle(value: String) {
        if (isLocked()) return
        val title = value.limitedToCodePoints(ChatCreationLimits.TITLE_LENGTH_MAX)
        savedStateHandle[KEY_TITLE] = title
        form.update { it.copy(title = title, titleError = false) }
    }

    private fun setAutoDelete(autoDelete: MessageAutoDelete) {
        if (isLocked()) return
        savedStateHandle[KEY_AUTO_DELETE] = autoDelete.name
        form.update { it.copy(autoDelete = autoDelete) }
    }

    private fun create() {
        val state = details.value
        if (!state.canCreate || isLocked()) return
        if (state.title.isBlank()) {
            form.update { it.copy(titleError = true) }
            return
        }
        form.update { it.copy(isCreating = true, titleError = false) }
        viewModelScope.launch {
            val created = repository.createGroup(state.title, picker.pickedUserIds, state.autoDelete, photo.path)
            when (created) {
                is Outcome.Success -> created(created.value)
                is Outcome.Failure -> fail(created.error)
            }
        }
    }

    private suspend fun created(group: CreatedGroup) {
        photo.markUsed()
        savedStateHandle[KEY_CREATED_CHAT] = group.chatId
        savedStateHandle[KEY_NOT_ADDED] = group.notAddedUserIds.toLongArray()
        finish(group.chatId, group.notAddedUserIds)
    }

    /** Opens chat [chatId], or first says whom of [notAdded] Telegram did not add. */
    private suspend fun finish(
        chatId: Long,
        notAdded: List<Long>,
    ) {
        val dialog = notAddedDialog(chatId, notAdded, userRepository)
        if (dialog == null) {
            openChat(chatId)
        } else {
            form.update { it.copy(isCreating = false, notAdded = dialog) }
        }
    }

    private fun fail(error: DomainError) {
        form.update { it.copy(isCreating = false) }
        when (error) {
            is DomainError.FloodWait -> startFloodWait(error.seconds)
            DomainError.TitleInvalid -> form.update { it.copy(titleError = true) }
            else -> showError(error)
        }
    }

    private fun shareLink(dialog: NotAddedDialog) {
        if (dialog.isSharing) return
        form.update { it.copy(notAdded = dialog.copy(isSharing = true)) }
        viewModelScope.launch {
            when (val link = repository.inviteLink(dialog.chatId)) {
                is Outcome.Success -> {
                    detailsEffectChannel.send(CreateChatEffect.ShareLink(link.value))
                    openChat(dialog.chatId)
                }

                is Outcome.Failure -> {
                    form.update { it.copy(notAdded = dialog.copy(isSharing = false)) }
                    showError(link.error)
                }
            }
        }
    }

    private fun openChat(chatId: Long) {
        form.update { it.copy(notAdded = null, isCreating = false) }
        savedStateHandle[KEY_NOT_ADDED] = null
        detailsEffectChannel.trySend(CreateChatEffect.OpenChat(chatId))
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) detailsEffectChannel.trySend(CreateChatEffect.ShowError(error))
    }

    private fun startFloodWait(seconds: Long) {
        floodWaitJob?.cancel()
        floodWaitJob = viewModelScope.countDown(seconds) { wait -> form.update { it.copy(floodWait = wait) } }
    }

    override fun onCleared() {
        photo.release()
    }

    internal companion object {
        const val KEY_PREFIX = "newGroup"
        const val KEY_TITLE = "newGroup.title"
        const val KEY_AUTO_DELETE = "newGroup.autoDelete"
        const val KEY_PHOTO = "newGroup.photo"
        const val KEY_CREATED_CHAT = "newGroup.createdChat"
        const val KEY_NOT_ADDED = "newGroup.notAdded"
    }
}
