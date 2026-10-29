package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.OwnershipTransferCheck
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.MemberRightsRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/** What the rights page of a member shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface MemberRightsUiState {
    data object Loading : MemberRightsUiState

    /** The member or the group could not be read, because of [error]; [MemberRightsEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : MemberRightsUiState

    /**
     * @property restrict the page sets what the member may still do, rather than their administrator rights.
     * @property isNew the member is not an administrator (or not restricted) yet: saving promotes (or restricts) them.
     * @property switches the rights or permissions, those the user may not change disabled.
     * @property canRevoke the member's rights can be taken away (or their restrictions lifted).
     * @property isSaving a change was asked for, and TDLib has not answered yet.
     * @property canTransfer the user owns the supergroup or channel and may make this member its owner.
     * @property isCheckingTransfer Telegram is asked whether the ownership may be transferred now.
     */
    data class Content(
        val name: UiText,
        val photo: ProfilePhoto?,
        val avatarId: Long,
        @param:DrawableRes val placeholderIcon: Int? = null,
        val restrict: Boolean,
        val isNew: Boolean,
        val switches: ImmutableList<RightSwitch>,
        val canRevoke: Boolean,
        val isSaving: Boolean = false,
        val canTransfer: Boolean = false,
        val isCheckingTransfer: Boolean = false,
    ) : MemberRightsUiState
}

/**
 * The question before transferring the ownership to [name]: [isTransferring] while Telegram answers, [error] why the
 * last try failed (a wrong password).
 */
@Immutable
data class TransferDialog(
    val name: UiText,
    val isChannel: Boolean,
    val isTransferring: Boolean = false,
    val error: DomainError? = null,
)

sealed interface MemberRightsEvent {
    data object Retry : MemberRightsEvent

    data class Toggle(
        val key: String,
        val on: Boolean,
    ) : MemberRightsEvent

    data object Save : MemberRightsEvent

    /** Take the administrator's rights away, or lift the member's restrictions. */
    data object Revoke : MemberRightsEvent

    /** "Transfer ownership": Telegram is asked whether it may be done now, then the password is asked for. */
    data object Transfer : MemberRightsEvent

    data object ConfirmTransfer : MemberRightsEvent

    data object DismissTransfer : MemberRightsEvent
}

sealed interface MemberRightsEffect {
    /** The change was made: the page closes, and the members page reads its list again. */
    data object Saved : MemberRightsEffect

    data class ShowError(
        val error: DomainError,
    ) : MemberRightsEffect

    /** The ownership cannot be transferred now, because of [check]. */
    data class TransferUnavailable(
        val check: OwnershipTransferCheck,
    ) : MemberRightsEffect

    /** The member owns the chat now: the page closes, as after [Saved]. */
    data object Transferred : MemberRightsEffect
}

/**
 * The rights page of member [userId] of group or channel [chatId] (`REQUIREMENTS.md` FR-P3): their administrator rights
 * or, with [restrict], what they may still do.
 *
 * - **Administrator rights**: an administrator's own, else, promoting, the rights the official apps start with
 *   ([AdministratorRights.defaultsFor]) as far as the user has them. Only the rights of the group's kind show, and the
 *   user may switch only those they have themselves (the owner, all), as Telegram refuses the others. Saving sends
 *   `MemberRole.Administrator` with every right, shown or not, kept.
 * - **Restrictions**: a restricted member's permissions, else the group's defaults; nothing the group's defaults do
 *   not allow can be switched on. Saving restricts for ever (`untilDate` 0).
 * - **Revoking** takes the rights away, or lifts the restrictions (`MemberRole.Member`).
 * - **Transferring the ownership** (the owner of a supergroup or channel, for a person): `canTransferOwnership` first,
 *   whose refusals are snackbars, then the 2-step verification password in [transferDialog], kept in memory only
 *   ([transferPassword]), then `transferChatOwnership`.
 * - The switches the user changed are kept in the [SavedStateHandle], so they survive process death.
 */
@Suppress("LongParameterList", "TooManyFunctions") // The route's arguments, saved state, three repositories.
@HiltViewModel
class MemberRightsViewModel internal constructor(
    private val chatId: Long,
    private val userId: Long,
    private val restrict: Boolean,
    private val savedStateHandle: SavedStateHandle,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
        userRepository: UserRepository,
    ) : this(
        savedStateHandle.toRoute<MemberRightsRoute>().chatId,
        savedStateHandle.toRoute<MemberRightsRoute>().userId,
        savedStateHandle.toRoute<MemberRightsRoute>().restrict,
        savedStateHandle,
        groupInfoRepository,
        administrationRepository,
        userRepository,
    )

    private val mutableUiState = MutableStateFlow<MemberRightsUiState>(MemberRightsUiState.Loading)
    val uiState: StateFlow<MemberRightsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<MemberRightsEffect>(Channel.BUFFERED)
    val effects: Flow<MemberRightsEffect> = effectChannel.receiveAsFlow()

    private val mutableTransferDialog = MutableStateFlow<TransferDialog?>(null)
    val transferDialog: StateFlow<TransferDialog?> = mutableTransferDialog.asStateFlow()

    /** The password typed into [transferDialog]: never in the saved state. */
    val transferPassword = TextFieldState()

    /** What was loaded, from which the switches are built. */
    private var loaded: Loaded? = null

    init {
        viewModelScope.launch(Dispatchers.Main) { load() }
    }

    fun onEvent(event: MemberRightsEvent) {
        when (event) {
            MemberRightsEvent.Retry -> retry()
            is MemberRightsEvent.Toggle -> toggle(event.key, event.on)
            MemberRightsEvent.Save -> save()
            MemberRightsEvent.Revoke -> send(MemberRole.Member)
            MemberRightsEvent.Transfer -> checkTransfer()
            MemberRightsEvent.ConfirmTransfer -> transfer()
            MemberRightsEvent.DismissTransfer -> dismissTransfer()
        }
    }

    private fun retry() {
        if (mutableUiState.value is MemberRightsUiState.Error) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableUiState.value = MemberRightsUiState.Loading
        val (group, member, user) =
            coroutineScope {
                val group = async { groupInfoRepository.groupInfo(chatId).first() }
                val member = async { groupInfoRepository.member(chatId, userId) }
                val user = async { userRepository.getUser(userId) }
                Triple(group.await(), member.await(), user.await())
            }
        val info = (group as? Outcome.Success)?.value
        val found = (member as? Outcome.Success)?.value
        if (info == null || found == null) {
            val error =
                (group as? Outcome.Failure)?.error ?: (member as? Outcome.Failure)?.error ?: DomainError.NotFound
            mutableUiState.value = MemberRightsUiState.Error(error)
            return
        }
        loaded = Loaded(info, found, (user as? Outcome.Success)?.value)
        mutableUiState.value = content()
    }

    private fun toggle(
        key: String,
        on: Boolean,
    ) {
        val current = mutableUiState.value as? MemberRightsUiState.Content ?: return
        if (current.switches.none { it.key == key && it.enabled }) return
        val changed = savedStateHandle.get<Map<String, Boolean>>(KEY_CHANGED).orEmpty() + (key to on)
        savedStateHandle[KEY_CHANGED] = HashMap(changed)
        mutableUiState.value = content(isSaving = current.isSaving)
    }

    private fun save() {
        val current = mutableUiState.value as? MemberRightsUiState.Content ?: return
        val loaded = loaded ?: return
        val role =
            if (restrict) {
                MemberRole.Restricted(isMember = true, untilDate = 0, permissions = editedPermissions(loaded))
            } else {
                MemberRole.Administrator(canBeEdited = true, rights = editedRights(loaded).copy(canManageChat = true))
            }
        if (!current.isSaving) send(role)
    }

    private fun send(role: MemberRole) {
        val current = mutableUiState.value as? MemberRightsUiState.Content ?: return
        if (current.isSaving) return
        mutableUiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val changed = administrationRepository.setMemberRole(chatId, MessageSender.User(userId), role)
            mutableUiState.update { (it as? MemberRightsUiState.Content)?.copy(isSaving = false) ?: it }
            when (changed) {
                is Outcome.Success -> effectChannel.trySend(MemberRightsEffect.Saved)
                is Outcome.Failure -> showError(changed.error)
            }
        }
    }

    private fun checkTransfer() {
        val current = mutableUiState.value as? MemberRightsUiState.Content ?: return
        if (!current.canTransfer || current.isCheckingTransfer || mutableTransferDialog.value != null) return
        mutableUiState.value = current.copy(isCheckingTransfer = true)
        viewModelScope.launch {
            val check = administrationRepository.ownershipTransferCheck()
            mutableUiState.update { (it as? MemberRightsUiState.Content)?.copy(isCheckingTransfer = false) ?: it }
            when (check) {
                is Outcome.Failure -> {
                    showError(check.error)
                }

                is Outcome.Success -> {
                    if (check.value == OwnershipTransferCheck.Allowed) {
                        transferPassword.clearText()
                        val isChannel = loaded?.group?.kind == GroupKind.Channel
                        mutableTransferDialog.value = TransferDialog(current.name, isChannel)
                    } else {
                        effectChannel.trySend(MemberRightsEffect.TransferUnavailable(check.value))
                    }
                }
            }
        }
    }

    private fun transfer() {
        val dialog = mutableTransferDialog.value ?: return
        val password = transferPassword.text.toString()
        if (dialog.isTransferring || password.isEmpty()) return
        mutableTransferDialog.value = dialog.copy(isTransferring = true, error = null)
        viewModelScope.launch {
            when (val done = administrationRepository.transferOwnership(chatId, userId, password)) {
                is Outcome.Success -> {
                    mutableTransferDialog.value = null
                    transferPassword.clearText()
                    effectChannel.trySend(MemberRightsEffect.Transferred)
                }

                is Outcome.Failure -> {
                    mutableTransferDialog.value = dialog.copy(isTransferring = false, error = done.error)
                }
            }
        }
    }

    private fun dismissTransfer() {
        if (mutableTransferDialog.value?.isTransferring == true) return
        mutableTransferDialog.value = null
        transferPassword.clearText()
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(MemberRightsEffect.ShowError(error))
    }

    private fun content(isSaving: Boolean = false): MemberRightsUiState {
        val loaded = loaded ?: return MemberRightsUiState.Loading
        val user = loaded.user
        val isDeleted = user == null || user.isDeleted
        val role = loaded.member.role
        return MemberRightsUiState.Content(
            name = if (user == null || isDeleted) UiText.resource(UiR.string.user_deleted_account) else user.fullName(),
            photo = user?.profilePhoto,
            avatarId = userId,
            placeholderIcon = if (isDeleted) UiR.drawable.ic_person_filled else null,
            restrict = restrict,
            isNew = if (restrict) role !is MemberRole.Restricted else role !is MemberRole.Administrator,
            switches = if (restrict) permissionSwitches(loaded) else rightSwitches(loaded),
            canRevoke = if (restrict) role is MemberRole.Restricted else role is MemberRole.Administrator,
            isSaving = isSaving,
            canTransfer =
                !restrict && !isDeleted && user?.type == UserType.Regular &&
                    loaded.group.myRole is MemberRole.Owner && loaded.group.kind != GroupKind.BasicGroup,
            isCheckingTransfer = (mutableUiState.value as? MemberRightsUiState.Content)?.isCheckingTransfer == true,
        )
    }

    private fun rightSwitches(loaded: Loaded): ImmutableList<RightSwitch> {
        val mine = loaded.group.myRights
        val isOwner = loaded.group.myRole is MemberRole.Owner
        val rights = editedRights(loaded)
        return AdminRight.entries
            .filter { it.appliesTo(loaded.group.kind) }
            .map { right ->
                RightSwitch(right.key, right.label, right.of(rights), enabled = isOwner || right.of(mine))
            }.toImmutableList()
    }

    private fun permissionSwitches(loaded: Loaded): ImmutableList<RightSwitch> {
        val defaults = loaded.group.defaultPermissions
        val permissions = editedPermissions(loaded)
        return Permission.entries
            .map { permission ->
                val allowed = permission.of(defaults)
                RightSwitch(permission.key, permission.label, allowed && permission.of(permissions), enabled = allowed)
            }.toImmutableList()
    }

    /** The member's rights, or the rights to start with, with the switches the user changed. */
    private fun editedRights(loaded: Loaded): AdministratorRights {
        val group = loaded.group
        val start =
            (loaded.member.role as? MemberRole.Administrator)?.rights
                ?: AdministratorRights.defaultsFor(group.kind).let { defaults ->
                    if (group.myRole is MemberRole.Owner) defaults else defaults.within(group.myRights)
                }
        val changed = savedStateHandle.get<Map<String, Boolean>>(KEY_CHANGED).orEmpty()
        return AdminRight.entries.fold(start) { rights, right ->
            changed[right.key]?.let { right.set(rights, it) } ?: rights
        }
    }

    /** The member's permissions, or the group's defaults, with the switches the user changed, within the defaults. */
    private fun editedPermissions(loaded: Loaded): MemberPermissions {
        val defaults = loaded.group.defaultPermissions
        val start = (loaded.member.role as? MemberRole.Restricted)?.permissions ?: defaults
        val changed = savedStateHandle.get<Map<String, Boolean>>(KEY_CHANGED).orEmpty()
        return Permission.entries.fold(start) { permissions, permission ->
            val on = changed[permission.key] ?: permission.of(permissions)
            permission.set(permissions, on && permission.of(defaults))
        }
    }

    /** What the page was made from. */
    private class Loaded(
        val group: GroupInfo,
        val member: GroupMember,
        val user: User?,
    )

    private companion object {
        const val KEY_CHANGED = "memberRights.changed"
    }
}

/** A right's switch key. */
internal val AdminRight.key: String get() = "right.$name"

/** A permission's switch key. */
internal val Permission.key: String get() = "permission.$name"

/** [this] without the rights [mine] lacks, as an administrator grants only what they have. */
private fun AdministratorRights.within(mine: AdministratorRights): AdministratorRights =
    AdminRight.entries.fold(this) { rights, right -> if (right.of(mine)) rights else right.set(rights, false) }

/** The string of the page's title: promoting, editing an administrator's rights, or restricting. */
internal fun MemberRightsUiState.Content.titleRes(): Int =
    when {
        restrict -> R.string.chat_info_restrictions_title
        isNew -> R.string.chat_info_promote_title
        else -> R.string.chat_info_rights_title
    }
