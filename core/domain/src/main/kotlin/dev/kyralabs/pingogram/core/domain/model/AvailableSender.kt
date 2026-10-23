package dev.kyralabs.pingogram.core.domain.model

/**
 * Someone the user can send messages in a chat as (send-as, `REQUIREMENTS.md` FR-C5; td_api.tl `chatMessageSender`):
 * the user, a public channel they own, or the group itself for an anonymous admin. [needsPremium] if choosing it needs
 * Telegram Premium, which the user does not have.
 */
data class AvailableSender(
    val sender: MessageSender,
    val needsPremium: Boolean,
)
