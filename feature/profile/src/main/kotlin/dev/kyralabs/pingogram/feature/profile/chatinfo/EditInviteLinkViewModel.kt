package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.InviteLink
import dev.kyralabs.pingogram.core.domain.model.InviteLinkSettings
import dev.kyralabs.pingogram.core.domain.repository.InviteLinkRepository
import dev.kyralabs.pingogram.core.navigation.EditInviteLinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the page that creates or edits an invite link shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface EditInviteLinkUiState {
    data object Loading : EditInviteLinkUiState

    data class Error(
        val error: DomainError,
    ) : EditInviteLinkUiState

    /**
     * @property isNew the page creates a link; otherwise it edits one.
     * @property expiry when the link stops working, among [expiryChoices].
     * @property memberLimit how many may join, among [limitChoices] (0: no limit); none with [createsJoinRequest].
     */
    data class Content(
        val isNew: Boolean,
        val name: String = "",
        val expiry: ExpiryChoice = ExpiryChoice.Never,
        val expiryChoices: ImmutableList<ExpiryChoice> = DefaultExpiryChoices,
        val memberLimit: Int = 0,
        val limitChoices: ImmutableList<Int> = DefaultLimitChoices,
        val createsJoinRequest: Boolean = false,
        val isSaving: Boolean = false,
    ) : EditInviteLinkUiState {
        override fun toString() = "Content(isNew=$isNew, expiry=$expiry, memberLimit=$memberLimit, …)"
    }
}

/** When a link stops working. */
@Immutable
sealed interface ExpiryChoice {
    data object Never : ExpiryChoice

    /** [seconds] after it is saved. */
    data class After(
        val seconds: Long,
    ) : ExpiryChoice

    /** When it does now, at [epochSeconds]: an edited link's own expiry, kept. */
    data class At(
        val epochSeconds: Long,
    ) : ExpiryChoice
}

/** The expiries the page offers: none, an hour, a day, a week. */
internal val DefaultExpiryChoices: ImmutableList<ExpiryChoice> =
    persistentListOf(
        ExpiryChoice.Never,
        ExpiryChoice.After(3_600),
        ExpiryChoice.After(86_400),
        ExpiryChoice.After(604_800),
    )

/** The member limits the page offers: none, 1, 10 and 100. */
internal val DefaultLimitChoices: ImmutableList<Int> = persistentListOf(0, 1, 10, 100)

sealed interface EditInviteLinkEvent {
    data object Retry : EditInviteLinkEvent

    data class NameChanged(
        val name: String,
    ) : EditInviteLinkEvent {
        override fun toString() = "NameChanged(…)"
    }

    data class SelectExpiry(
        val expiry: ExpiryChoice,
    ) : EditInviteLinkEvent

    data class SelectLimit(
        val memberLimit: Int,
    ) : EditInviteLinkEvent

    data class SetJoinRequests(
        val on: Boolean,
    ) : EditInviteLinkEvent

    data object Save : EditInviteLinkEvent
}

sealed interface EditInviteLinkEffect {
    /** Telegram has the link: the page closes, and the links page reads its links again. */
    data object Saved : EditInviteLinkEffect

    data class ShowError(
        val error: DomainError,
    ) : EditInviteLinkEffect
}

/**
 * Creating an invite link of group or channel [chatId], or editing [link] (`REQUIREMENTS.md` FR-P3): its name (up to
 * 32 characters), when it expires (never, an hour, a day or a week after saving; an edited link's own expiry is
 * offered too), how many may join (no limit, 1, 10, 100, or an edited link's own limit), and whether joining asks the
 * administrators first, which allows no member limit. What the user chose is kept in the [SavedStateHandle]. Saving
 * is one request ([InviteLinkRepository.create] or `edit`).
 */
@HiltViewModel
class EditInviteLinkViewModel internal constructor(
    private val chatId: Long,
    private val link: String?,
    private val savedStateHandle: SavedStateHandle,
    private val repository: InviteLinkRepository,
    private val clock: WallClock,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: InviteLinkRepository,
        clock: WallClock,
    ) : this(
        savedStateHandle.toRoute<EditInviteLinkRoute>().chatId,
        savedStateHandle.toRoute<EditInviteLinkRoute>().link,
        savedStateHandle,
        repository,
        clock,
    )

    private val mutableUiState =
        MutableStateFlow<EditInviteLinkUiState>(
            if (link == null) restored(EditInviteLinkUiState.Content(isNew = true)) else EditInviteLinkUiState.Loading,
        )
    val uiState: StateFlow<EditInviteLinkUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<EditInviteLinkEffect>(Channel.BUFFERED)
    val effects: Flow<EditInviteLinkEffect> = effectChannel.receiveAsFlow()

    init {
        if (link != null) viewModelScope.launch(Dispatchers.Main) { load(link) }
    }

    fun onEvent(event: EditInviteLinkEvent) {
        when (event) {
            EditInviteLinkEvent.Retry -> {
                if (link != null && mutableUiState.value is EditInviteLinkUiState.Error) {
                    viewModelScope.launch { load(link) }
                }
            }

            is EditInviteLinkEvent.NameChanged -> {
                edit { it.copy(name = event.name.take(InviteLinkSettings.NAME_LENGTH_MAX)) }
            }

            is EditInviteLinkEvent.SelectExpiry -> {
                edit { if (event.expiry in it.expiryChoices) it.copy(expiry = event.expiry) else it }
            }

            is EditInviteLinkEvent.SelectLimit -> {
                edit { if (event.memberLimit in it.limitChoices) it.copy(memberLimit = event.memberLimit) else it }
            }

            is EditInviteLinkEvent.SetJoinRequests -> {
                edit { it.copy(createsJoinRequest = event.on, memberLimit = if (event.on) 0 else it.memberLimit) }
            }

            EditInviteLinkEvent.Save -> {
                save()
            }
        }
    }

    private suspend fun load(link: String) {
        mutableUiState.value = EditInviteLinkUiState.Loading
        mutableUiState.value =
            when (val found = repository.link(chatId, link)) {
                is Outcome.Success -> restored(contentOf(found.value))
                is Outcome.Failure -> EditInviteLinkUiState.Error(found.error)
            }
    }

    /** The page for an existing link: its own expiry and limit among the choices where they are none of the usual. */
    private fun contentOf(found: InviteLink): EditInviteLinkUiState.Content {
        val expiry = if (found.expirationDate > 0) ExpiryChoice.At(found.expirationDate) else ExpiryChoice.Never
        val expiries = if (expiry is ExpiryChoice.At) DefaultExpiryChoices + expiry else DefaultExpiryChoices
        val limits = (DefaultLimitChoices + found.memberLimit).distinct().sorted()
        return EditInviteLinkUiState.Content(
            isNew = false,
            name = found.name,
            expiry = expiry,
            expiryChoices = expiries.toImmutableList(),
            memberLimit = found.memberLimit,
            limitChoices = limits.toImmutableList(),
            createsJoinRequest = found.createsJoinRequest,
        )
    }

    /** [content] with what the user chose before the process was recreated. */
    private fun restored(content: EditInviteLinkUiState.Content): EditInviteLinkUiState.Content {
        val name = savedStateHandle.get<String>(KEY_NAME) ?: content.name
        val expiry = savedStateHandle.get<Long>(KEY_EXPIRY)?.let(::expiryOf)?.takeIf { it in content.expiryChoices }
        val limit = savedStateHandle.get<Int>(KEY_LIMIT)?.takeIf { it in content.limitChoices }
        val requests = savedStateHandle.get<Boolean>(KEY_REQUESTS) ?: content.createsJoinRequest
        return content.copy(
            name = name,
            expiry = expiry ?: content.expiry,
            memberLimit = if (requests) 0 else limit ?: content.memberLimit,
            createsJoinRequest = requests,
        )
    }

    private inline fun edit(change: (EditInviteLinkUiState.Content) -> EditInviteLinkUiState.Content) {
        val current = mutableUiState.value as? EditInviteLinkUiState.Content ?: return
        if (current.isSaving) return
        val changed = change(current)
        savedStateHandle[KEY_NAME] = changed.name
        savedStateHandle[KEY_EXPIRY] = changed.expiry.saved()
        savedStateHandle[KEY_LIMIT] = changed.memberLimit
        savedStateHandle[KEY_REQUESTS] = changed.createsJoinRequest
        mutableUiState.value = changed
    }

    private fun save() {
        val current = mutableUiState.value as? EditInviteLinkUiState.Content ?: return
        if (current.isSaving) return
        val settings =
            InviteLinkSettings(
                name = current.name,
                expirationDate =
                    when (val expiry = current.expiry) {
                        ExpiryChoice.Never -> 0
                        is ExpiryChoice.After -> clock.now().epochSecond + expiry.seconds
                        is ExpiryChoice.At -> expiry.epochSeconds
                    },
                memberLimit = current.memberLimit,
                createsJoinRequest = current.createsJoinRequest,
            )
        mutableUiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val saved =
                when (link) {
                    null -> repository.create(chatId, settings)
                    else -> repository.edit(chatId, link, settings)
                }
            mutableUiState.update { (it as? EditInviteLinkUiState.Content)?.copy(isSaving = false) ?: it }
            when (saved) {
                is Outcome.Success -> {
                    effectChannel.trySend(EditInviteLinkEffect.Saved)
                }

                is Outcome.Failure -> {
                    if (saved.error != DomainError.Silent) {
                        effectChannel.trySend(EditInviteLinkEffect.ShowError(saved.error))
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_NAME = "editInviteLink.name"
        const val KEY_EXPIRY = "editInviteLink.expiry"
        const val KEY_LIMIT = "editInviteLink.limit"
        const val KEY_REQUESTS = "editInviteLink.requests"
    }
}

/** An expiry as a number the [SavedStateHandle] keeps: 0 never, a positive duration, a negated timestamp. */
private fun ExpiryChoice.saved(): Long =
    when (this) {
        ExpiryChoice.Never -> 0
        is ExpiryChoice.After -> seconds
        is ExpiryChoice.At -> -epochSeconds
    }

private fun expiryOf(saved: Long): ExpiryChoice =
    when {
        saved == 0L -> ExpiryChoice.Never
        saved > 0 -> ExpiryChoice.After(saved)
        else -> ExpiryChoice.At(-saved)
    }
