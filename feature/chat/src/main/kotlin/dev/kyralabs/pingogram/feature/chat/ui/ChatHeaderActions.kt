package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the experimental look's top bar and jump buttons ask for (`docs/DESIGN_SYSTEM.md` §13.5), for [ChatViewModel]:
 * "Jump to mention" shows the oldest unread message that mentions the user ([findMention],
 * `ChatSearchRepository.oldestUnreadMention`; one request per tap, a tap while one runs ignored), which [onShow] shows
 * in the list as a reply quote's jump does; the channel's mute toggle mutes or unmutes the chat
 * ([ChatRepository.setMuted]), which TDLib's update then shows. Failures are snackbars through [onEffect]; a mention
 * read meanwhile is none.
 */
internal class ChatHeaderActions(
    private val chatId: Long,
    private val findMention: suspend () -> Outcome<Long>,
    private val chats: ChatRepository,
    private val scope: CoroutineScope,
    private val onShow: (messageId: Long) -> Unit,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private var mentionJump: Job? = null

    fun jumpToMention() {
        if (mentionJump?.isActive == true) return
        mentionJump =
            scope.launch {
                when (val found = findMention()) {
                    is Outcome.Success -> onShow(found.value)
                    is Outcome.Failure -> found.error.takeIf { it != DomainError.NotFound }?.let(::showError)
                }
            }
    }

    fun setMuted(isMuted: Boolean) {
        scope.launch {
            val result = chats.setMuted(chatId, isMuted)
            if (result is Outcome.Failure) showError(result.error)
        }
    }

    private fun showError(error: DomainError) = onEffect(ChatEffect.ShowError(error))
}
