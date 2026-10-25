package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * TDLib's connection for the conversation's top bar, which says "Waiting for network", "Connecting…" or "Updating…"
 * in place of the chat's status while it is not connected, as Telegram's apps do (`docs/DESIGN_SYSTEM.md` §6
 * "Conversation top bar", audit A11). A view model of its own beside [ChatViewModel], as the location sheet's is: the
 * connection is the app's, not the chat's, and only the top bar reads it, so a change composes nothing else.
 *
 * [status] is [ConnectionStatus.Ready] until the repository answers, so nothing flashes as a chat opens, and is
 * collected only while the screen is ([STOP_TIMEOUT_MILLIS] after it leaves, as rotating keeps it).
 */
@HiltViewModel
class ChatConnectionViewModel
    @Inject
    constructor(
        repository: ConnectionRepository,
    ) : ViewModel() {
        val status: StateFlow<ConnectionStatus> =
            repository.connectionStatus
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConnectionStatus.Ready)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
