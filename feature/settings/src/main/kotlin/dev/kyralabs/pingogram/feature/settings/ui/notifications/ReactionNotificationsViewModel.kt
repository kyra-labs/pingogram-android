package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.domain.repository.ReactionNotificationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the reactions page shows: the [settings] as TDLib announced them, or a choice on its way to Telegram; null until
 * TDLib announced them.
 */
@Immutable
data class ReactionNotificationsUiState(
    val settings: ReactionNotificationSettings? = null,
)

/** Which notification a choice changes. */
enum class ReactionNotificationKind { Messages, Stories, PollVotes }

sealed interface ReactionNotificationsEvent {
    data class Choose(
        val kind: ReactionNotificationKind,
        val source: ReactionNotificationSource,
    ) : ReactionNotificationsEvent

    data class SetShowPreview(
        val on: Boolean,
    ) : ReactionNotificationsEvent
}

sealed interface ReactionNotificationsEffect {
    data class ShowError(
        val error: DomainError,
    ) : ReactionNotificationsEffect
}

/**
 * Settings → Notifications → Reactions (`docs/audit/feature-gaps-octogram.md`, P1): whom the account hears of reactions
 * to its messages and stories and of votes in its polls from, and whether the notification shows the reaction. A choice
 * shows at once and goes to Telegram; a refusal takes it back with a snackbar.
 */
@HiltViewModel
class ReactionNotificationsViewModel
    @Inject
    constructor(
        private val repository: ReactionNotificationRepository,
    ) : ViewModel() {
        /** The settings being sent, shown until TDLib announces them or refuses them. */
        private val pending = MutableStateFlow<ReactionNotificationSettings?>(null)

        val uiState: StateFlow<ReactionNotificationsUiState> =
            combine(repository.settings, pending) { settings, sending ->
                ReactionNotificationsUiState(sending ?: settings)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ReactionNotificationsUiState(),
            )

        private val effectChannel = Channel<ReactionNotificationsEffect>(Channel.BUFFERED)
        val effects: Flow<ReactionNotificationsEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: ReactionNotificationsEvent) {
            val current = pending.value ?: repository.settings.value ?: return
            val changed =
                when (event) {
                    is ReactionNotificationsEvent.Choose -> current.with(event.kind, event.source)
                    is ReactionNotificationsEvent.SetShowPreview -> current.copy(showPreview = event.on)
                }
            if (changed == current) return
            pending.value = changed
            viewModelScope.launch {
                val saved = repository.setSettings(changed)
                // TDLib announces the change before it answers; what it holds now is what counts.
                if (pending.value == changed) pending.value = null
                if (saved is Outcome.Failure && saved.error != DomainError.Silent) {
                    effectChannel.trySend(ReactionNotificationsEffect.ShowError(saved.error))
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun ReactionNotificationSettings.with(
    kind: ReactionNotificationKind,
    source: ReactionNotificationSource,
) = when (kind) {
    ReactionNotificationKind.Messages -> copy(messages = source)
    ReactionNotificationKind.Stories -> copy(stories = source)
    ReactionNotificationKind.PollVotes -> copy(pollVotes = source)
}
