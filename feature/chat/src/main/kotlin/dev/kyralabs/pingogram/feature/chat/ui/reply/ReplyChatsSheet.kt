package dev.kyralabs.pingogram.feature.chat.ui.reply

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The chat picker of "Reply in another chat" (`docs/DESIGN_SYSTEM.md` §6 "Message actions"): the message [messageId]
 * it replies to, with one line of it ([preview], null while not loaded), and the chats the user can reply in, Saved
 * Messages first, the main chat list or what the search finds.
 */
@Immutable
data class ReplyChatsSheet(
    val messageId: Long,
    val preview: UiText?,
    val targets: ImmutableList<ForwardTarget> = persistentListOf(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val searchError: DomainError? = null,
) {
    override fun toString() = "ReplyChatsSheet(messageId=$messageId, targets=${targets.size}, isLoading=$isLoading)"
}
