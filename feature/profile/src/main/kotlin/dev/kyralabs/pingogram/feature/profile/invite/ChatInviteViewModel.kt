package dev.kyralabs.pingogram.feature.profile.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.ChatInvite
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ChatInviteRoute
import dev.kyralabs.pingogram.core.ui.text.fullName
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
 * The join sheet of invite link [inviteLink] (`REQUIREMENTS.md` FR-P5, `docs/adr/0013-deep-links.md`): asks Telegram
 * what it leads to ([ChatJoinRepository.checkInvite]), which only reads, and joins only on the user's "Join"
 * ([ChatJoinRepository.joinByInvite]); a chat that asks for it gets a request instead. A chat the user is in already
 * opens at once, without the sheet. Joined, the chat opens ([ChatInviteEffect.OpenChat]); a request sent says so.
 *
 * Nothing needs restoring after process death: the link is in the route, and the sheet checks it again.
 */
@HiltViewModel
class ChatInviteViewModel internal constructor(
    private val inviteLink: String,
    private val joinRepository: ChatJoinRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        joinRepository: ChatJoinRepository,
        userRepository: UserRepository,
    ) : this(savedStateHandle.toRoute<ChatInviteRoute>().inviteLink, joinRepository, userRepository)

    private val mutableUiState = MutableStateFlow<ChatInviteUiState>(ChatInviteUiState.Loading)
    val uiState: StateFlow<ChatInviteUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<ChatInviteEffect>(Channel.BUFFERED)
    val effects: Flow<ChatInviteEffect> = effectChannel.receiveAsFlow()

    init {
        // After the sheet's first frame (docs/PERFORMANCE.md §4).
        viewModelScope.launch(Dispatchers.Main) { load() }
    }

    fun onEvent(event: ChatInviteEvent) {
        when (event) {
            ChatInviteEvent.Retry -> {
                if (mutableUiState.value is ChatInviteUiState.Failed) {
                    mutableUiState.value = ChatInviteUiState.Loading
                    viewModelScope.launch { load() }
                }
            }

            ChatInviteEvent.Join -> {
                join()
            }
        }
    }

    private suspend fun load() {
        val invite =
            when (val checked = joinRepository.checkInvite(inviteLink)) {
                is Outcome.Success -> {
                    checked.value
                }

                is Outcome.Failure -> {
                    mutableUiState.value = ChatInviteUiState.Failed(checked.error, canRetry = checked.error.canRetry())
                    return
                }
            }
        if (invite.isMember) {
            effectChannel.send(ChatInviteEffect.OpenChat(invite.chatId))
            return
        }
        mutableUiState.value = ChatInviteUiState.Content(invite.toCard())
    }

    /** The card of the invitation, with up to [MEMBER_AVATARS] of the members TDLib named; others are left out. */
    private suspend fun ChatInvite.toCard(): InviteCard {
        val members =
            coroutineScope {
                memberUserIds
                    .take(MEMBER_AVATARS)
                    .map { id -> async { userRepository.getUser(id).getOrNull() } }
                    .awaitAll()
                    .filterNotNull()
                    .map { InviteMember(it.id, it.fullName(), it.profilePhoto) }
            }
        return InviteCard(
            title = title,
            kind = kind,
            isPublic = isPublic,
            memberCount = memberCount,
            description = description,
            photo = photo,
            avatarId = if (chatId != 0L) chatId else inviteLink.hashCode().toLong(),
            members = members.toImmutableList(),
            isVerified = isVerified,
            isScam = isScam,
            isFake = isFake,
            createsJoinRequest = createsJoinRequest,
            needsSubscription = needsSubscription,
        )
    }

    private fun join() {
        val content = mutableUiState.value as? ChatInviteUiState.Content ?: return
        if (!content.canJoin) return
        mutableUiState.value = content.copy(isJoining = true, joinError = null, notice = null)
        viewModelScope.launch {
            when (val joined = joinRepository.joinByInvite(inviteLink)) {
                is Outcome.Success -> {
                    when (val result = joined.value) {
                        is JoinResult.Joined -> {
                            effectChannel.send(ChatInviteEffect.OpenChat(result.chatId))
                        }

                        JoinResult.RequestSent -> {
                            mutableUiState.value = ChatInviteUiState.RequestSent(content.invite.kind)
                        }

                        JoinResult.NeedsBotApproval -> {
                            showNotice(JoinNotice.NeedsBotApproval)
                        }

                        JoinResult.Declined -> {
                            showNotice(JoinNotice.Declined)
                        }
                    }
                }

                is Outcome.Failure -> {
                    updateContent { it.copy(isJoining = false, joinError = joined.error) }
                }
            }
        }
    }

    private fun showNotice(notice: JoinNotice) = updateContent { it.copy(isJoining = false, notice = notice) }

    private fun updateContent(transform: (ChatInviteUiState.Content) -> ChatInviteUiState.Content) =
        mutableUiState.update { state -> if (state is ChatInviteUiState.Content) transform(state) else state }
}

/** Whether checking the link again may help: no network, a flood wait; not for a link that is no longer valid. */
private fun DomainError.canRetry(): Boolean = this == DomainError.Network || this is DomainError.FloodWait

/** The members whose avatars the sheet shows. */
internal const val MEMBER_AVATARS = 5
