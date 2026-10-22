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
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.usernameProblem
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Creating a channel (`REQUIREMENTS.md` FR-P4), one view model for the three steps of the flow's nested graph
 * (`NewChannelRoute`), each with its state and effects:
 *
 * - **Details** ([details], [detailsEffects]): the name (required, at most 128 characters), the description (at most
 *   255) and the photo ([NewChatPhoto]). "Next" creates the channel ([ChatCreationRepository.createChannel]), once,
 *   as Telegram's own apps do: a channel is private until it gets a username, and its invite link needs the channel.
 * - **Type** ([type], [typeEffects]): private (the default) with its invite link, read as soon as the channel exists,
 *   or public with a username, checked for its form at once and with Telegram after [USERNAME_CHECK_DELAY] without
 *   typing (a check still running when the username changes again is cancelled), then set on "Next".
 * - **Subscribers** ([members], [memberState], [memberEffects]): picking contacts ([MemberPicker]) to add
 *   ([ChatCreationRepository.addMembers]), or none. Then the channel opens ([CreateChatEffect.OpenChat]); if Telegram
 *   did not add some, a dialog says who first and offers to share the channel's invite link ([NotAddedDialog]).
 *
 * Once the channel exists, back on the later steps opens it ([onFinish]): there is nothing to go back to. Everything
 * typed and picked, and the new channel's id, are kept in the [SavedStateHandle], so they survive process death; what
 * Telegram said about the username is asked again. Errors: what concerns a field shows at it, `FLOOD_WAIT` counts
 * down, anything else is a snackbar.
 *
 * It has a handler for each event of three steps and the requests they run, hence `TooManyFunctions` is suppressed;
 * it takes the repositories, the clock for the contacts' online dots, and the dispatcher and language that tests
 * replace, hence `LongParameterList`.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class NewChannelViewModel internal constructor(
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

    private val detailsEffectChannel = Channel<CreateChatEffect>(Channel.BUFFERED)
    private val typeEffectChannel = Channel<CreateChatEffect>(Channel.BUFFERED)
    private val memberEffectChannel = Channel<CreateChatEffect>(Channel.BUFFERED)

    /** One-off effects for the first step: on to the type, and snackbars. */
    val detailsEffects: Flow<CreateChatEffect> = detailsEffectChannel.receiveAsFlow()

    /** One-off effects for the second step: on to the subscribers, sharing the link, the channel, and snackbars. */
    val typeEffects: Flow<CreateChatEffect> = typeEffectChannel.receiveAsFlow()

    /** One-off effects for the last step: the channel, sharing its link, and snackbars. */
    val memberEffects: Flow<CreateChatEffect> = memberEffectChannel.receiveAsFlow()

    private val photo = NewChatPhoto(viewModelScope, savedStateHandle, repository, KEY_PHOTO)

    private val detailsForm =
        MutableStateFlow(
            NewChannelDetailsUiState(
                title = savedStateHandle[KEY_TITLE] ?: "",
                description = savedStateHandle[KEY_DESCRIPTION] ?: "",
            ),
        )

    /** The first step: the channel's name, description and photo. */
    val details: StateFlow<NewChannelDetailsUiState> =
        combine(detailsForm, photo.state) { form, photo -> form.copy(photo = photo) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, detailsForm.value.copy(photo = photo.state.value))

    private val mutableType =
        MutableStateFlow(
            NewChannelTypeUiState(
                isPublic = savedStateHandle[KEY_PUBLIC] ?: false,
                username = savedStateHandle[KEY_USERNAME] ?: "",
            ),
        )

    /** The second step: public or private. */
    val type: StateFlow<NewChannelTypeUiState> = mutableType.asStateFlow()

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

    /** The last step: the contacts, the search and the picks. */
    val members: StateFlow<MemberPickerUiState> = picker.state

    private val mutableMemberState = MutableStateFlow(NewChannelMembersUiState())

    /** The last step's adding, and the dialog about the people Telegram did not add. */
    val memberState: StateFlow<NewChannelMembersUiState> = mutableMemberState.asStateFlow()

    /** The new channel, once it exists. */
    private val chatId: Long? get() = savedStateHandle[KEY_CHAT]

    private var floodWaitJob: Job? = null
    private var usernameJob: Job? = null
    private var linkJob: Job? = null

    init {
        // After process death: the link and what Telegram says about the username, read again.
        if (chatId != null) {
            viewModelScope.launch {
                loadLink()
                checkUsername(type.value.username, afterTyping = false)
                savedStateHandle.get<LongArray>(KEY_NOT_ADDED)?.let { finish(it.toList()) }
            }
        }
    }

    fun onDetailsEvent(event: ChatDetailsEvent) {
        when (event) {
            is ChatDetailsEvent.TitleChanged -> editTitle(event.value)

            is ChatDetailsEvent.DescriptionChanged -> editDescription(event.value)

            is ChatDetailsEvent.PhotoPicked -> if (!isLocked()) photo.pick(event.uri, ::showDetailsError)

            ChatDetailsEvent.RemovePhoto -> if (!isLocked()) photo.remove()

            ChatDetailsEvent.Create -> create()

            // Channels have no auto-delete step.
            is ChatDetailsEvent.AutoDeleteChanged -> Unit
        }
    }

    private fun editDescription(value: String) {
        if (isLocked()) return
        val description = value.limitedToCodePoints(ChatCreationLimits.DESCRIPTION_LENGTH_MAX)
        savedStateHandle[KEY_DESCRIPTION] = description
        detailsForm.update { it.copy(description = description) }
    }

    fun onTypeEvent(event: NewChannelTypeEvent) {
        when (event) {
            is NewChannelTypeEvent.TypeChanged -> setPublic(event.isPublic)
            is NewChannelTypeEvent.UsernameChanged -> editUsername(event.value)
            NewChannelTypeEvent.RetryLink -> if (type.value.inviteLink is InviteLinkState.Error) loadLink()
            NewChannelTypeEvent.Continue -> continueFromType()
        }
    }

    fun onMembersEvent(event: MemberPickerEvent) = picker.onEvent(event)

    /** "Skip", or "Add" with people picked. */
    fun onAddMembers() {
        val chatId = chatId
        val state = memberState.value
        val busy = state.isAdding || state.floodWait != null || state.notAdded != null
        val picked = picker.pickedUserIds
        when {
            chatId == null || busy -> Unit
            picked.isEmpty() -> openChat(chatId)
            else -> addMembers(chatId, picked)
        }
    }

    private fun addMembers(
        chatId: Long,
        picked: List<Long>,
    ) {
        mutableMemberState.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            when (val added = repository.addMembers(chatId, picked)) {
                is Outcome.Success -> {
                    savedStateHandle[KEY_NOT_ADDED] = added.value.toLongArray()
                    finish(added.value)
                }

                is Outcome.Failure -> {
                    mutableMemberState.update { it.copy(isAdding = false) }
                    val error = added.error
                    if (error is DomainError.FloodWait) {
                        startFloodWait(error.seconds) { wait ->
                            mutableMemberState.update { it.copy(floodWait = wait) }
                        }
                    } else {
                        showError(memberEffectChannel, error)
                    }
                }
            }
        }
    }

    fun onNotAddedEvent(event: NotAddedEvent) {
        val dialog = memberState.value.notAdded ?: return
        when (event) {
            NotAddedEvent.ShareLink -> shareLink(dialog)
            NotAddedEvent.Dismiss -> openChat(dialog.chatId)
        }
    }

    /** Back on a step after the first: the channel exists, so it opens. */
    fun onFinish() {
        val chatId = chatId ?: return
        if (type.value.isSaving || memberState.value.isAdding) return
        openChat(chatId)
    }

    // Details

    /** Nothing of the details changes once creating started: the channel may already exist. */
    private fun isLocked(): Boolean = details.value.isCreating || chatId != null

    private fun editTitle(value: String) {
        if (isLocked()) return
        val title = value.limitedToCodePoints(ChatCreationLimits.TITLE_LENGTH_MAX)
        savedStateHandle[KEY_TITLE] = title
        detailsForm.update { it.copy(title = title, titleError = false) }
    }

    private fun create() {
        val state = details.value
        if (!state.canCreate || isLocked()) return
        if (state.title.isBlank()) {
            detailsForm.update { it.copy(titleError = true) }
            return
        }
        detailsForm.update { it.copy(isCreating = true, titleError = false) }
        viewModelScope.launch {
            when (val created = repository.createChannel(state.title, state.description, photo.path)) {
                is Outcome.Success -> {
                    photo.markUsed()
                    savedStateHandle[KEY_CHAT] = created.value
                    detailsForm.update { it.copy(isCreating = false) }
                    loadLink()
                    detailsEffectChannel.send(CreateChatEffect.Next)
                }

                is Outcome.Failure -> {
                    detailsForm.update { it.copy(isCreating = false) }
                    failDetails(created.error)
                }
            }
        }
    }

    private fun failDetails(error: DomainError) {
        when (error) {
            is DomainError.FloodWait -> {
                startFloodWait(error.seconds) { wait -> detailsForm.update { it.copy(floodWait = wait) } }
            }

            DomainError.TitleInvalid -> {
                detailsForm.update { it.copy(titleError = true) }
            }

            else -> {
                showDetailsError(error)
            }
        }
    }

    private fun showDetailsError(error: DomainError) = showError(detailsEffectChannel, error)

    // Type

    private fun setPublic(isPublic: Boolean) {
        if (type.value.isSaving) return
        savedStateHandle[KEY_PUBLIC] = isPublic
        mutableType.update { it.copy(isPublic = isPublic) }
    }

    private fun editUsername(value: String) {
        if (type.value.isSaving) return
        // The field shows the @ and the t.me/ itself; one pasted with the username is dropped.
        val username =
            value
                .trim()
                .removePrefix("@")
                .removePrefix("t.me/")
                .removePrefix("https://t.me/")
        savedStateHandle[KEY_USERNAME] = username
        mutableType.update { it.copy(username = username) }
        checkUsername(username, afterTyping = true)
    }

    /**
     * Finds out what to say about [username]: nothing if empty, its first problem if it cannot be one, else what
     * Telegram says, asked after a pause in typing ([afterTyping]).
     */
    private fun checkUsername(
        username: String,
        afterTyping: Boolean,
    ) {
        usernameJob?.cancel()
        val status =
            when {
                username.isEmpty() -> UsernameStatus.Empty
                else -> usernameProblem(username)?.let(UsernameStatus::Problem) ?: UsernameStatus.Checking
            }
        mutableType.update { it.copy(usernameStatus = status) }
        val chatId = chatId
        if (status != UsernameStatus.Checking || chatId == null) return
        usernameJob =
            viewModelScope.launch {
                if (afterTyping) delay(USERNAME_CHECK_DELAY)
                val checked =
                    when (val result = repository.checkUsername(chatId, username)) {
                        is Outcome.Success -> UsernameStatus.Checked(result.value)
                        is Outcome.Failure -> UsernameStatus.CheckFailed
                    }
                mutableType.update { if (it.username == username) it.copy(usernameStatus = checked) else it }
            }
    }

    private fun loadLink() {
        val chatId = chatId ?: return
        linkJob?.cancel()
        mutableType.update { it.copy(inviteLink = InviteLinkState.Loading) }
        linkJob =
            viewModelScope.launch {
                val link =
                    when (val result = repository.inviteLink(chatId)) {
                        is Outcome.Success -> InviteLinkState.Ready(result.value)
                        is Outcome.Failure -> InviteLinkState.Error(result.error)
                    }
                mutableType.update { it.copy(inviteLink = link) }
            }
    }

    private fun continueFromType() {
        val chatId = chatId
        val state = type.value
        when {
            chatId == null || !state.canContinue -> Unit
            state.isPublic -> saveUsername(chatId, state.username)
            else -> typeEffectChannel.trySend(CreateChatEffect.Next)
        }
    }

    private fun saveUsername(
        chatId: Long,
        username: String,
    ) {
        usernameJob?.cancel()
        mutableType.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val outcome = repository.setUsername(chatId, username)
            mutableType.update { it.copy(isSaving = false) }
            when (outcome) {
                is Outcome.Success -> typeEffectChannel.send(CreateChatEffect.Next)
                is Outcome.Failure -> failUsername(outcome.error)
            }
        }
    }

    private fun failUsername(error: DomainError) {
        val refused =
            when (error) {
                DomainError.UsernameTaken -> UsernameCheck.Taken
                DomainError.UsernameInvalid -> UsernameCheck.Invalid
                DomainError.UsernameForSale -> UsernameCheck.ForSale
                DomainError.TooManyPublicLinks -> UsernameCheck.Unavailable
                else -> null
            }
        when {
            refused != null -> {
                mutableType.update { it.copy(usernameStatus = UsernameStatus.Checked(refused)) }
                if (error == DomainError.TooManyPublicLinks) showError(typeEffectChannel, error)
            }

            error is DomainError.FloodWait -> {
                startFloodWait(error.seconds) { wait -> mutableType.update { it.copy(floodWait = wait) } }
            }

            else -> {
                showError(typeEffectChannel, error)
            }
        }
    }

    // Subscribers

    /** Opens the channel, or first says whom of [notAdded] Telegram did not add. */
    private suspend fun finish(notAdded: List<Long>) {
        val chatId = chatId ?: return
        val dialog = notAddedDialog(chatId, notAdded, userRepository)
        if (dialog == null) {
            openChat(chatId)
        } else {
            mutableMemberState.update { it.copy(isAdding = false, notAdded = dialog) }
        }
    }

    private fun shareLink(dialog: NotAddedDialog) {
        if (dialog.isSharing) return
        mutableMemberState.update { it.copy(notAdded = dialog.copy(isSharing = true)) }
        viewModelScope.launch {
            when (val link = repository.inviteLink(dialog.chatId)) {
                is Outcome.Success -> {
                    memberEffectChannel.send(CreateChatEffect.ShareLink(link.value))
                    openChat(dialog.chatId)
                }

                is Outcome.Failure -> {
                    mutableMemberState.update { it.copy(notAdded = dialog.copy(isSharing = false)) }
                    showError(memberEffectChannel, link.error)
                }
            }
        }
    }

    /** Opens the channel from the step that shows: the subscribers once anything was added, else the type. */
    private fun openChat(chatId: Long) {
        mutableMemberState.update { it.copy(notAdded = null, isAdding = false) }
        savedStateHandle[KEY_NOT_ADDED] = null
        val effect = CreateChatEffect.OpenChat(chatId)
        typeEffectChannel.trySend(effect)
        memberEffectChannel.trySend(effect)
    }

    private fun showError(
        channel: Channel<CreateChatEffect>,
        error: DomainError,
    ) {
        if (error != DomainError.Silent) channel.trySend(CreateChatEffect.ShowError(error))
    }

    private fun startFloodWait(
        seconds: Long,
        update: (FloodWait?) -> Unit,
    ) {
        floodWaitJob?.cancel()
        floodWaitJob = viewModelScope.countDown(seconds, update)
    }

    override fun onCleared() {
        photo.release()
    }

    internal companion object {
        /** How long typing pauses before a username is checked with Telegram. */
        val USERNAME_CHECK_DELAY = 500.milliseconds

        const val KEY_PREFIX = "newChannel"
        const val KEY_TITLE = "newChannel.title"
        const val KEY_DESCRIPTION = "newChannel.description"
        const val KEY_PHOTO = "newChannel.photo"
        const val KEY_CHAT = "newChannel.chat"
        const val KEY_PUBLIC = "newChannel.public"
        const val KEY_USERNAME = "newChannel.username"
        const val KEY_NOT_ADDED = "newChannel.notAdded"
    }
}
