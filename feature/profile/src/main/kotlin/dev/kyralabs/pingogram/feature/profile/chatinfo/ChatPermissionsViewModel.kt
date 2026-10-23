package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.SlowModeDelays
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.navigation.ChatPermissionsRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the permissions page of a group shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface ChatPermissionsUiState {
    data object Loading : ChatPermissionsUiState

    data class Error(
        val error: DomainError,
    ) : ChatPermissionsUiState

    /**
     * @property switches what members may do by default.
     * @property slowModeDelay seconds between a member's messages, one of `SlowModeDelays`; null where there is no
     *   slow mode (basic groups).
     * @property isSaving a change was asked for, and TDLib has not answered yet.
     */
    data class Content(
        val switches: ImmutableList<RightSwitch>,
        val slowModeDelay: Int? = null,
        val isSaving: Boolean = false,
    ) : ChatPermissionsUiState
}

sealed interface ChatPermissionsEvent {
    data object Retry : ChatPermissionsEvent

    data class Toggle(
        val key: String,
        val on: Boolean,
    ) : ChatPermissionsEvent

    /** The slow mode slider was let go at [seconds]. */
    data class SetSlowMode(
        val seconds: Int,
    ) : ChatPermissionsEvent
}

sealed interface ChatPermissionsEffect {
    data class ShowError(
        val error: DomainError,
    ) : ChatPermissionsEffect
}

/**
 * The permissions page of group [chatId] (`REQUIREMENTS.md` FR-P3): what members may do by default and, in a
 * supergroup, the slow mode. Each switch and each slow mode chosen is one request, as settings pages change one setting
 * per tap; it shows at once and stays until the group's update says the same, or goes back with a snackbar when
 * Telegram refuses it. The group follows [GroupInfoRepository.groupInfo] while the page shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatPermissionsViewModel internal constructor(
    private val chatId: Long,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
    ) : this(savedStateHandle.toRoute<ChatPermissionsRoute>().chatId, groupInfoRepository, administrationRepository)

    private val effectChannel = Channel<ChatPermissionsEffect>(Channel.BUFFERED)
    val effects: Flow<ChatPermissionsEffect> = effectChannel.receiveAsFlow()

    private val started = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)
    private val pending = MutableStateFlow(Pending())

    private val group: Flow<Outcome<GroupInfo>?> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (isStarted) groupInfoRepository.groupInfo(chatId) else flowOf(null)
        }

    val uiState: StateFlow<ChatPermissionsUiState> =
        combine(group, pending) { group, pending ->
            when (group) {
                null -> ChatPermissionsUiState.Loading
                is Outcome.Failure -> ChatPermissionsUiState.Error(group.error)
                is Outcome.Success -> content(group.value, pending)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatPermissionsUiState.Loading)

    /** The group's own values as last shown, to send a whole set of permissions with one changed. */
    private var shown: GroupInfo? = null

    init {
        viewModelScope.launch(Dispatchers.Main) { started.value = true }
    }

    fun onEvent(event: ChatPermissionsEvent) {
        when (event) {
            ChatPermissionsEvent.Retry -> attempts.update { it + 1 }
            is ChatPermissionsEvent.Toggle -> toggle(event.key, event.on)
            is ChatPermissionsEvent.SetSlowMode -> setSlowMode(event.seconds)
        }
    }

    private fun toggle(
        key: String,
        on: Boolean,
    ) {
        val group = shown
        val permission = Permission.entries.firstOrNull { it.key == key }
        if (group == null || permission == null || pending.value.isSaving) return
        val current = pending.value.permissions ?: group.defaultPermissions
        val changed = permission.set(current, on)
        pending.update { it.copy(permissions = changed, isSaving = true) }
        viewModelScope.launch {
            val result = administrationRepository.setPermissions(chatId, changed)
            pending.update { it.copy(isSaving = false) }
            if (result is Outcome.Failure) {
                pending.update { it.copy(permissions = null) }
                showError(result.error)
            }
        }
    }

    private fun setSlowMode(seconds: Int) {
        val group = shown ?: return
        if (pending.value.isSaving || seconds == (pending.value.slowModeDelay ?: group.details?.slowModeDelay)) return
        pending.update { it.copy(slowModeDelay = seconds, isSaving = true) }
        viewModelScope.launch {
            val result = administrationRepository.setSlowModeDelay(chatId, seconds)
            pending.update { it.copy(isSaving = false) }
            if (result is Outcome.Failure) {
                pending.update { it.copy(slowModeDelay = null) }
                showError(result.error)
            }
        }
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(ChatPermissionsEffect.ShowError(error))
    }

    private fun content(
        group: GroupInfo,
        pending: Pending,
    ): ChatPermissionsUiState.Content {
        shown = group
        // What the user set shows until the group says the same, then the group's own values take over.
        val permissions = pending.permissions?.takeIf { it != group.defaultPermissions } ?: group.defaultPermissions
        val delay = group.details?.slowModeDelay
        val shownDelay = pending.slowModeDelay?.takeIf { it != delay } ?: delay
        val canChange = group.myRights.canRestrictMembers
        return ChatPermissionsUiState.Content(
            switches =
                Permission.entries
                    .map { RightSwitch(it.key, it.label, it.of(permissions), enabled = canChange) }
                    .toImmutableList(),
            slowModeDelay = shownDelay?.takeIf { group.kind == GroupKind.Supergroup }?.let(::nearestSlowMode),
            isSaving = pending.isSaving,
        )
    }

    /** What the user changed and Telegram has not confirmed yet. */
    private data class Pending(
        val permissions: MemberPermissions? = null,
        val slowModeDelay: Int? = null,
        val isSaving: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The slow mode delay Telegram accepts nearest to [seconds], which TDLib may report outside the list. */
internal fun nearestSlowMode(seconds: Int): Int = SlowModeDelays.minBy { kotlin.math.abs(it - seconds) }
