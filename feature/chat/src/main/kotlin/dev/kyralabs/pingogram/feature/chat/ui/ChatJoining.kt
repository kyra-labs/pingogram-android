package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Joining public group or channel [chatId] from its conversation, for `ChatViewModel` (`REQUIREMENTS.md` FR-P5): the
 * composer's "Join" ([ComposerUiState.Join]) calls [join], once at a time; [isJoining] shows meanwhile. Joined, TDLib
 * announces the new membership (`updateSupergroup`), and the composer follows by itself; a request that administrators
 * accept says so ([ChatEffect.JoinRequestSent]), a refusal or a bot's check too ([ChatEffect.JoinRefused]), and errors
 * are snackbars ([ChatEffect.ShowError]). Only on the user's tap, never by itself.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
internal class ChatJoining(
    private val chatId: Long,
    private val repository: ChatJoinRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val joining = MutableStateFlow(false)

    /** Whether Telegram is being asked to let the user in. */
    val isJoining: StateFlow<Boolean> = joining.asStateFlow()

    fun join() {
        if (joining.value) return
        joining.value = true
        scope.launch {
            when (val joined = repository.join(chatId)) {
                is Outcome.Success -> {
                    when (joined.value) {
                        is JoinResult.Joined -> Unit
                        JoinResult.RequestSent -> onEffect(ChatEffect.JoinRequestSent)
                        JoinResult.NeedsBotApproval, JoinResult.Declined -> onEffect(ChatEffect.JoinRefused)
                    }
                }

                is Outcome.Failure -> {
                    onEffect(ChatEffect.ShowError(joined.error))
                }
            }
            joining.value = false
        }
    }
}

/** This state with the composer's "Join" busy while [isJoining]. */
internal fun ChatUiState.withJoining(isJoining: Boolean): ChatUiState {
    val join = composer as? ComposerUiState.Join ?: return this
    return if (join.isJoining == isJoining) this else copy(composer = join.copy(isJoining = isJoining))
}
