package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.OwnershipTransferCheck
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sheet before the owner leaves their group or channel (`docs/DESIGN_SYSTEM.md` §6 "Leaving as the owner").
 *
 * @property kind what the chat is: a basic group's next owner takes over as the user leaves, a supergroup's or
 *   channel's after 7 days unless they come back (td_api.tl `getChatOwnerAfterLeaving`).
 * @property heir who Telegram makes the owner if the user chooses no one; null while unknown or where Telegram names
 *   no one.
 * @property candidates the administrators the user may choose (people, not bots or deleted accounts); null while they
 *   load.
 * @property chosen the administrator chosen, by user id; 0 for letting Telegram choose.
 * @property step choosing, or the 2-step verification password before the ownership goes to [chosen].
 * @property isBusy Telegram is asked whether the ownership may be transferred, is transferring it, or the user leaves.
 * @property error why the last password was refused; null otherwise.
 */
@Immutable
data class OwnerLeaveSheet(
    val kind: GroupKind,
    val heir: UiText? = null,
    val candidates: ImmutableList<MemberItem>? = null,
    val chosen: Long = 0L,
    val step: OwnerLeaveStep = OwnerLeaveStep.Choose,
    val isBusy: Boolean = false,
    val error: DomainError? = null,
) {
    /** The administrator chosen, once the candidates are known; null for letting Telegram choose. */
    val chosenItem: MemberItem?
        get() = candidates?.firstOrNull { (it.sender as? MessageSender.User)?.userId == chosen }
}

/** The sheet's steps. */
enum class OwnerLeaveStep {
    Choose,
    Password,
}

/** What the user does in the [OwnerLeaveSheet]. */
sealed interface OwnerLeaveEvent {
    /** Choose administrator [userId] as the next owner; 0 lets Telegram choose. */
    data class Choose(
        val userId: Long,
    ) : OwnerLeaveEvent

    /** Leave, letting Telegram choose; or, with an administrator chosen, ask for the password. */
    data object Continue : OwnerLeaveEvent

    /** Transfer the ownership with the password typed, then leave. */
    data object Confirm : OwnerLeaveEvent

    /** From the password back to choosing. */
    data object Back : OwnerLeaveEvent

    /** Close the sheet without leaving. */
    data object Dismiss : OwnerLeaveEvent
}

/**
 * A part of [ChatInfoViewModel]: leaving a group or channel the user owns, choosing who owns it next.
 *
 * - **Opening** loads who Telegram would choose ([GroupAdministrationRepository.ownerAfterLeaving]) and the
 *   administrators ([GroupInfoRepository.members]), people other than the user only; a failure of either leaves it out.
 * - **Letting Telegram choose** leaves ([ChatRepository.deleteChat] for the user only), as other members do.
 * - **Choosing an administrator** asks [GroupAdministrationRepository.ownershipTransferCheck] first, whose refusals are
 *   snackbars ([ChatInfoEffect.TransferUnavailable]), then the 2-step verification password ([password], in memory
 *   only), then transfers the ownership and leaves. Should leaving fail after the transfer, the user stays an
 *   administrator and a snackbar says why.
 *
 * Whether the sheet shows and who is chosen are kept in [savedStateHandle]; after process death it opens again at
 * choosing, as the password is never kept.
 */
@Suppress("LongParameterList") // Its repositories, and the view model's scope and effects.
internal class OwnerLeaving(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
    private val chatRepository: ChatRepository,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
    private val userRepository: UserRepository,
    private val send: (ChatInfoEffect) -> Unit,
) {
    private val mutableSheet = MutableStateFlow<OwnerLeaveSheet?>(null)

    /** The sheet, or null while it does not show. */
    val sheet: StateFlow<OwnerLeaveSheet?> = mutableSheet.asStateFlow()

    /** The password typed at [OwnerLeaveStep.Password]: never in the saved state. */
    val password = TextFieldState()

    /** Whether the sheet showed before the process died: it opens again once the group is known. */
    val wasOpen: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_OPEN) == true

    fun open(
        kind: GroupKind,
        myUserId: Long?,
    ) {
        if (mutableSheet.value != null) return
        savedStateHandle[KEY_OPEN] = true
        mutableSheet.value = OwnerLeaveSheet(kind, chosen = savedStateHandle.get<Long>(KEY_CHOSEN) ?: 0L)
        scope.launch {
            val heir = (administrationRepository.ownerAfterLeaving(chatId) as? Outcome.Success)?.value
            val heirName = heir?.let { userRepository.getUser(it) as? Outcome.Success }?.value?.fullName()
            mutableSheet.update { it?.copy(heir = heirName) }
        }
        scope.launch {
            val candidates = candidates(myUserId)
            mutableSheet.update { sheet ->
                sheet?.copy(
                    candidates = candidates,
                    // A chosen administrator who is no longer one is no longer chosen.
                    chosen = sheet.chosen.takeIf { id -> candidates.any { it.isUser(id) } } ?: 0L,
                )
            }
        }
    }

    fun onEvent(event: OwnerLeaveEvent) {
        val sheet = mutableSheet.value ?: return
        when (event) {
            is OwnerLeaveEvent.Choose -> {
                if (!sheet.isBusy && sheet.step == OwnerLeaveStep.Choose) {
                    savedStateHandle[KEY_CHOSEN] = event.userId
                    mutableSheet.value = sheet.copy(chosen = event.userId)
                }
            }

            OwnerLeaveEvent.Continue -> {
                if (sheet.chosen == 0L) leave(sheet) else check(sheet)
            }

            OwnerLeaveEvent.Confirm -> {
                transfer(sheet)
            }

            OwnerLeaveEvent.Back -> {
                if (!sheet.isBusy) {
                    password.clearText()
                    mutableSheet.value = sheet.copy(step = OwnerLeaveStep.Choose, error = null)
                }
            }

            OwnerLeaveEvent.Dismiss -> {
                if (!sheet.isBusy) close()
            }
        }
    }

    private fun leave(sheet: OwnerLeaveSheet) {
        if (sheet.isBusy) return
        mutableSheet.value = sheet.copy(isBusy = true)
        scope.launch { finishLeaving() }
    }

    private fun check(sheet: OwnerLeaveSheet) {
        if (sheet.isBusy || sheet.chosenItem == null) return
        mutableSheet.value = sheet.copy(isBusy = true)
        scope.launch {
            val check = administrationRepository.ownershipTransferCheck()
            mutableSheet.update { it?.copy(isBusy = false) }
            when {
                check is Outcome.Failure -> {
                    showError(check.error)
                }

                (check as Outcome.Success).value == OwnershipTransferCheck.Allowed -> {
                    password.clearText()
                    mutableSheet.update { it?.copy(step = OwnerLeaveStep.Password, error = null) }
                }

                else -> {
                    send(ChatInfoEffect.TransferUnavailable(check.value))
                }
            }
        }
    }

    private fun transfer(sheet: OwnerLeaveSheet) {
        val typed = password.text.toString()
        val isReady = sheet.step == OwnerLeaveStep.Password && sheet.chosen != 0L
        if (sheet.isBusy || !isReady || typed.isEmpty()) return
        mutableSheet.value = sheet.copy(isBusy = true, error = null)
        scope.launch {
            when (val done = administrationRepository.transferOwnership(chatId, sheet.chosen, typed)) {
                is Outcome.Success -> {
                    password.clearText()
                    finishLeaving()
                }

                is Outcome.Failure -> {
                    mutableSheet.update { it?.copy(isBusy = false, error = done.error) }
                }
            }
        }
    }

    /** Leaves the chat; the page closes once it did, the sheet with a snackbar otherwise. */
    private suspend fun finishLeaving() {
        when (val left = chatRepository.deleteChat(chatId, forEveryone = false)) {
            is Outcome.Success -> {
                close()
                send(ChatInfoEffect.Left)
            }

            is Outcome.Failure -> {
                close()
                showError(left.error)
            }
        }
    }

    private fun close() {
        mutableSheet.value = null
        password.clearText()
        savedStateHandle.remove<Boolean>(KEY_OPEN)
        savedStateHandle.remove<Long>(KEY_CHOSEN)
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) send(ChatInfoEffect.ShowError(error))
    }

    /** The administrators the ownership may go to: people other than the user, not bots or deleted accounts. */
    private suspend fun candidates(myUserId: Long?): ImmutableList<MemberItem> {
        val page =
            groupInfoRepository.members(chatId, MemberFilter.Administrators, limit = ADMINISTRATORS_MAX)
                as? Outcome.Success ?: return emptyList<MemberItem>().toImmutableList()
        val known = userRepository.users.first()
        return page.value.members
            .mapNotNull { member ->
                val userId = (member.sender as? MessageSender.User)?.userId ?: return@mapNotNull null
                if (userId == myUserId || member.role !is MemberRole.Administrator) return@mapNotNull null
                val user = known[userId] ?: (userRepository.getUser(userId) as? Outcome.Success)?.value
                user?.takeIf { it.canOwn() }?.let { candidate(member.sender, it) }
            }.toImmutableList()
    }

    private companion object {
        const val KEY_OPEN = "chat_info_owner_leaving"
        const val KEY_CHOSEN = "chat_info_owner_leaving_chosen"

        /** Telegram lists at most this many of a group's administrators in one page. */
        const val ADMINISTRATORS_MAX = 200
    }
}

/** A candidate's row: [user] as an administrator of the chat. */
private fun candidate(
    sender: MessageSender,
    user: User,
) = MemberItem(
    sender = sender,
    name = user.fullName(),
    photo = user.profilePhoto,
    avatarId = user.id,
    role = UiText.resource(R.string.chat_info_role_admin),
    isAdministrator = true,
)

private fun User.canOwn() = !isDeleted && type != UserType.Bot

private fun MemberItem.isUser(id: Long) = (sender as? MessageSender.User)?.userId == id
