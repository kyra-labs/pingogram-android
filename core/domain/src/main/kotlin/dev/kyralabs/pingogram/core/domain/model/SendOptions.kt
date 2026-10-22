package dev.kyralabs.pingogram.core.domain.model

/**
 * How a message is sent (`REQUIREMENTS.md` FR-C5): [silent] delivers it without a notification sound; [scheduledAt]
 * (Unix time, seconds) holds it on Telegram's servers until then, outside the chat's history; null sends it now.
 * [topic] is the forum topic or message thread it goes into (FR-C9); null sends it to the chat itself.
 */
data class SendOptions(
    val silent: Boolean = false,
    val scheduledAt: Long? = null,
    val topic: MessageTopic? = null,
)
