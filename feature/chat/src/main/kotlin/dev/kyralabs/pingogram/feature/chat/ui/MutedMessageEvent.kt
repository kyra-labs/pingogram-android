package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.muted.ChatMutedWordsViewModel

/**
 * What the user does with a message collapsed for a muted word (`REQUIREMENTS.md` FR-X21), handled by
 * [ChatMutedWordsViewModel] rather than the conversation's view model: the route hands these events to it.
 */
sealed interface MutedMessageEvent : ChatEvent {
    /** "Tap to show" on message [messageId]'s line: it shows until the user leaves the chat. */
    data class Show(
        val messageId: Long,
    ) : MutedMessageEvent
}
