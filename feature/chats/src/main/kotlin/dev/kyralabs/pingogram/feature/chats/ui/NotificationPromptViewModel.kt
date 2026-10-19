package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
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

/** What the chat list shows about notifications (Android 13+, `REQUIREMENTS.md` FR-N1). */
@Immutable
sealed interface NotificationPromptUiState {
    /** Nothing: notifications are allowed, the user chose "Not now", or the permission is not known yet. */
    data object Hidden : NotificationPromptUiState

    /** Why Pingogram asks to post notifications, with "Allow", which shows the system's dialog, and "Not now". */
    data object Ask : NotificationPromptUiState

    /**
     * The user refused in the system's dialog, which Android shows no more: notifications can be turned on in the
     * app's notification settings only, which "Open settings" opens; or "Not now".
     */
    data object Blocked : NotificationPromptUiState
}

/** What happens to the prompt, handled by [NotificationPromptViewModel.onEvent]. */
sealed interface NotificationPromptEvent {
    /**
     * The permission as the screen found it when it showed or came back (the user may have changed it in the system
     * settings meanwhile), and after the system's dialog: [granted], and whether Android would show its dialog again
     * with a reason first ([shouldShowRationale], which is false before the first request and after a final refusal).
     */
    data class PermissionChecked(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : NotificationPromptEvent

    data object Allow : NotificationPromptEvent

    data object NotNow : NotificationPromptEvent

    data object OpenSettings : NotificationPromptEvent
}

/** One-off side effects of [NotificationPromptViewModel], handled once by the prompt's host. */
sealed interface NotificationPromptEffect {
    /** Show the system's dialog for the notification permission. */
    data object RequestPermission : NotificationPromptEffect

    /** Open the app's notification settings in the system settings. */
    data object OpenSettings : NotificationPromptEffect
}

/**
 * The request to allow notifications at the top of the chat list (Android 13+, `REQUIREMENTS.md` FR-N1): shown once
 * the user is logged in and on the chat list, until notifications are allowed or the user chose "Not now", which is
 * kept on the device ([NotificationPromptRepository]), so it never nags. It explains first and shows the system's
 * dialog only when the user asks for it; once Android refuses to show that dialog again, it offers the app's
 * notification settings instead. The screen reports the permission ([NotificationPromptEvent.PermissionChecked]), as
 * only an activity can read it; nothing to restore after process death, everything is read again.
 */
@HiltViewModel
class NotificationPromptViewModel
    @Inject
    constructor(
        private val repository: NotificationPromptRepository,
    ) : ViewModel() {
        private val permission = MutableStateFlow<NotificationPromptEvent.PermissionChecked?>(null)

        val uiState: StateFlow<NotificationPromptUiState> =
            combine(repository.prompt, permission) { prompt, permission -> promptState(prompt, permission) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    NotificationPromptUiState.Hidden,
                )

        private val effectChannel = Channel<NotificationPromptEffect>(Channel.BUFFERED)
        val effects: Flow<NotificationPromptEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: NotificationPromptEvent) {
            when (event) {
                is NotificationPromptEvent.PermissionChecked -> {
                    permission.value = event
                }

                NotificationPromptEvent.Allow -> {
                    // Asked once the dialog shows: a refusal then counts as final when Android stops showing it.
                    viewModelScope.launch { repository.setRequested() }
                    effectChannel.trySend(NotificationPromptEffect.RequestPermission)
                }

                // A failure to save only means the prompt shows again at the next start.
                NotificationPromptEvent.NotNow -> {
                    viewModelScope.launch { repository.dismiss() }
                }

                NotificationPromptEvent.OpenSettings -> {
                    effectChannel.trySend(NotificationPromptEffect.OpenSettings)
                }
            }
        }

        private fun promptState(
            prompt: NotificationPrompt,
            permission: NotificationPromptEvent.PermissionChecked?,
        ): NotificationPromptUiState =
            when {
                permission == null || permission.granted || prompt.isDismissed -> NotificationPromptUiState.Hidden
                prompt.wasRequested && !permission.shouldShowRationale -> NotificationPromptUiState.Blocked
                else -> NotificationPromptUiState.Ask
            }

        private companion object {
            /** Keeps the state through a rotation, as every screen's view model does. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
