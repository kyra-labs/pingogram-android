package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens who a mention tapped in a message names (`REQUIREMENTS.md` FR-P2), for [ChatViewModel]: the profile of a user
 * named by their id at once; for an `@username`, the profile of the user or the chat of the group or channel Telegram
 * says has it ([SearchRepository.publicChat]), one request per tap. A username nobody has is a snackbar. Runs in
 * [scope], the view model's; what to open goes out through [onEffect].
 */
internal class ChatMentions(
    private val search: SearchRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    fun open(target: MentionTarget) {
        when (target) {
            is MentionTarget.User -> onEffect(ChatEffect.OpenProfile(target.userId))
            is MentionTarget.Username -> scope.launch { openUsername(target.username) }
        }
    }

    private suspend fun openUsername(username: String) {
        when (val owner = search.publicChat(username)) {
            is Outcome.Success -> {
                when (val found = owner.value) {
                    is UsernameOwner.User -> onEffect(ChatEffect.OpenProfile(found.userId))
                    is UsernameOwner.Chat -> onEffect(ChatEffect.OpenChat(found.chatId))
                }
            }

            is Outcome.Failure -> {
                onEffect(ChatEffect.ShowError(owner.error))
            }
        }
    }
}

/** Opens the profile or the chat a tap named ([ChatEffect.OpenProfile], [ChatEffect.OpenChat]); no snackbar. */
internal fun openNamed(
    effect: ChatEffect,
    onOpenProfile: (userId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
): String {
    when (effect) {
        is ChatEffect.OpenProfile -> onOpenProfile(effect.userId)
        is ChatEffect.OpenChat -> onOpenChat(effect.chatId)
        else -> Unit
    }
    return ""
}
