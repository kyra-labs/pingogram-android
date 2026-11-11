package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.CommunityLimits
import dev.kyralabs.pingogram.core.domain.model.CommunityRole
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.canChangeInfo
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.CommunityRoute
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.limitedToCodePoints
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The page of community [communityId] (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a) as
 * [uiState]: the community as TDLib announced it, its chats (`getCommunityFullInfo`, then its updates), each named by
 * `ChatRepository` (`getChat` once for a chat TDLib has not announced) with the user's membership from
 * `GroupRepository`, so a chat joined anywhere shows "Joined" by itself.
 *
 * - **Joining** a chat is one request on the user's tap (`ChatJoinRepository.join`); a request an administrator must
 *   accept, and a refusal, are snackbars.
 * - **Renaming** (the owner, and administrators who may change its info) types the name in a dialog kept in the
 *   [SavedStateHandle]; **deleting** (the owner) asks first and closes the page once Telegram took it.
 *
 * The page starts its work after the frame that composes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList", "TooManyFunctions") // Its repositories; a function per event.
@HiltViewModel
class CommunityViewModel internal constructor(
    private val communityId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val communityRepository: CommunityRepository,
    private val chatRepository: ChatRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val joinRepository: ChatJoinRepository,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        communityRepository: CommunityRepository,
        chatRepository: ChatRepository,
        groupRepository: GroupRepository,
        userRepository: UserRepository,
        joinRepository: ChatJoinRepository,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<CommunityRoute>().communityId,
        savedStateHandle,
        communityRepository,
        chatRepository,
        groupRepository,
        userRepository,
        joinRepository,
        dispatcher,
    )

    private val effectChannel = Channel<CommunityEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a chat, snackbars, closing once deleted. */
    val effects: Flow<CommunityEffect> = effectChannel.receiveAsFlow()

    private val started = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)
    private val joining = MutableStateFlow<Set<Long>>(emptySet())
    private val isBusy = MutableStateFlow(false)
    private val dialog =
        MutableStateFlow(
            when {
                savedStateHandle.contains(KEY_NAME) -> CommunityDialog.Rename(savedStateHandle[KEY_NAME] ?: "")
                savedStateHandle.get<Boolean>(KEY_DELETE) == true -> CommunityDialog.Delete
                else -> null
            },
        )

    private val details: Flow<Outcome<CommunityDetails>?> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (isStarted) {
                communityRepository
                    .details(
                        communityId,
                    ).map<_, Outcome<CommunityDetails>?> { it }
                    .onStart { emit(LOADING) }
            } else {
                flowOf(LOADING)
            }
        }

    private val chats: Flow<List<CommunityChatItem>> =
        details
            .map { (it as? Outcome.Success)?.value?.chats.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { chats ->
                if (chats.isEmpty()) flowOf(emptyList()) else combine(chats.map(::itemOf)) { it.filterNotNull() }
            }

    val uiState: StateFlow<CommunityUiState> =
        combine(communityRepository.community(communityId), details, chats, joining) { community, details, chats, j ->
            Sources(community, details, chats, j)
        }.combine(dialog) { sources, dialog -> sources to dialog }
            .combine(isBusy) { (sources, dialog), busy -> sources.toUiState(dialog, busy) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CommunityUiState.Loading)

    init {
        // The screen creates the view model in the frame that first composes it; its work starts after that frame.
        viewModelScope.launch(Dispatchers.Main) { started.value = true }
    }

    fun onEvent(event: CommunityEvent) {
        when (event) {
            CommunityEvent.Retry -> attempts.update { it + 1 }
            is CommunityEvent.OpenChat -> effectChannel.trySend(CommunityEffect.OpenChat(event.chatId))
            is CommunityEvent.Join -> join(event.chatId)
            CommunityEvent.Rename -> openRename()
            is CommunityEvent.NameChanged -> editName(event.name)
            CommunityEvent.Delete -> openDialog(CommunityDialog.Delete)
            CommunityEvent.Confirm -> confirm()
            CommunityEvent.DismissDialog -> openDialog(null)
        }
    }

    /** A chat's row, from the chat and, for a group or channel, the user's membership; null until the chat is known. */
    private fun itemOf(communityChat: CommunityChat): Flow<CommunityChatItem?> =
        chatRepository
            .chat(communityChat.chatId)
            .onStart { chatRepository.getChat(communityChat.chatId) }
            .flatMapLatest { chat ->
                when (val type = chat?.type) {
                    null -> {
                        flowOf(null)
                    }

                    is ChatType.Supergroup -> {
                        groupRepository.supergroup(type.supergroupId).map { group ->
                            chat.item(
                                communityChat,
                                if (type.isChannel) CommunityChatKind.Channel else CommunityChatKind.Group,
                                group?.memberCount,
                                group?.status?.isMember,
                                AvatarShape.ofChat(
                                    group?.isForum == true,
                                    isGroup = !type.isChannel,
                                    isChannel = type.isChannel,
                                ),
                            )
                        }
                    }

                    is ChatType.BasicGroup -> {
                        groupRepository.basicGroup(type.basicGroupId).map { group ->
                            chat.item(
                                communityChat,
                                CommunityChatKind.Group,
                                group?.memberCount,
                                group?.status?.isMember,
                                AvatarShape.ofChat(isForum = false, isGroup = true),
                            )
                        }
                    }

                    is ChatType.Private -> {
                        userRepository.users.map { users ->
                            val isBot = users[type.userId]?.type == UserType.Bot
                            chat.item(
                                communityChat,
                                if (isBot) CommunityChatKind.Bot else CommunityChatKind.Chat,
                                memberCount = null,
                                isMember = null,
                                shape = AvatarShape.Circle,
                            )
                        }
                    }

                    is ChatType.Secret -> {
                        flowOf(null)
                    }
                }
            }.distinctUntilChanged()

    private fun join(chatId: Long) {
        if (chatId in joining.value) return
        joining.update { it + chatId }
        viewModelScope.launch {
            when (val joined = joinRepository.join(chatId)) {
                is Outcome.Success -> {
                    when (joined.value) {
                        is JoinResult.Joined -> Unit
                        JoinResult.RequestSent -> notice(R.string.community_join_requested)
                        JoinResult.NeedsBotApproval -> notice(R.string.community_join_bot)
                        JoinResult.Declined -> notice(R.string.community_join_declined)
                    }
                }

                is Outcome.Failure -> {
                    showError(joined.error)
                }
            }
            joining.update { it - chatId }
        }
    }

    private fun openRename() {
        val community = (uiState.value as? CommunityUiState.Content)?.header ?: return
        if (!community.canRename) return
        openDialog(CommunityDialog.Rename(community.name))
    }

    private fun editName(name: String) {
        if (dialog.value !is CommunityDialog.Rename || isBusy.value) return
        val limited = name.limitedToCodePoints(CommunityLimits.NAME_LENGTH_MAX)
        openDialog(CommunityDialog.Rename(limited))
    }

    private fun openDialog(open: CommunityDialog?) {
        dialog.value = open
        when (open) {
            is CommunityDialog.Rename -> {
                savedStateHandle[KEY_NAME] = open.name
            }

            CommunityDialog.Delete -> {
                savedStateHandle[KEY_DELETE] = true
            }

            null -> {
                savedStateHandle.remove<String>(KEY_NAME)
                savedStateHandle.remove<Boolean>(KEY_DELETE)
            }
        }
    }

    private fun confirm() {
        val open = dialog.value ?: return
        if (isBusy.value) return
        isBusy.value = true
        viewModelScope.launch {
            val outcome =
                when (open) {
                    is CommunityDialog.Rename -> communityRepository.rename(communityId, open.name)
                    CommunityDialog.Delete -> communityRepository.delete(communityId)
                }
            isBusy.value = false
            when (outcome) {
                is Outcome.Success -> {
                    openDialog(null)
                    if (open == CommunityDialog.Delete) effectChannel.trySend(CommunityEffect.Deleted)
                }

                is Outcome.Failure -> {
                    showError(outcome.error)
                }
            }
        }
    }

    private fun notice(text: Int) {
        effectChannel.trySend(CommunityEffect.Notice(text))
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(CommunityEffect.ShowError(error))
    }

    /** What the page is made from, as of one emission. */
    private class Sources(
        val community: Community?,
        val details: Outcome<CommunityDetails>?,
        val chats: List<CommunityChatItem>,
        val joining: Set<Long>,
    ) {
        fun toUiState(
            dialog: CommunityDialog?,
            isBusy: Boolean,
        ): CommunityUiState =
            when (details) {
                null -> {
                    CommunityUiState.Loading
                }

                is Outcome.Failure -> {
                    CommunityUiState.Error(details.error)
                }

                is Outcome.Success -> {
                    CommunityUiState.Content(
                        header = header(),
                        chats =
                            chats
                                .map { if (it.chatId in joining) it.copy(membership = Membership.Joining) else it }
                                .toImmutableList(),
                        dialog = dialog,
                        isBusy = isBusy,
                    )
                }
            }

        private fun header(): CommunityHeader {
            val role = community?.role
            return CommunityHeader(
                id = community?.id ?: 0,
                name = community?.name.orEmpty(),
                photo = community?.photo,
                role =
                    when (role) {
                        CommunityRole.Owner -> CommunityRoleLabel.Owner
                        is CommunityRole.Administrator -> CommunityRoleLabel.Administrator
                        else -> null
                    },
                canRename = role?.canChangeInfo == true,
                canDelete = role == CommunityRole.Owner,
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_NAME = "community.name"
        const val KEY_DELETE = "community.delete"

        val LOADING: Outcome<CommunityDetails>? = null
    }
}

/** This chat's row in its community. */
private fun Chat.item(
    communityChat: CommunityChat,
    kind: CommunityChatKind,
    memberCount: Int?,
    isMember: Boolean?,
    shape: AvatarShape,
): CommunityChatItem =
    CommunityChatItem(
        chatId = id,
        title = title,
        photo = photo,
        shape = shape,
        kind = kind,
        memberCount = memberCount,
        isHidden = communityChat.isHidden,
        membership =
            when (isMember) {
                true -> Membership.Joined
                false -> Membership.CanJoin
                null -> Membership.None
            },
    )
