package dev.kyralabs.pingogram.core.domain.model

/**
 * How a message is sent (`REQUIREMENTS.md` FR-C5): [silent] delivers it without a notification sound; [scheduledAt]
 * (Unix time, seconds) holds it on Telegram's servers until then, outside the chat's history; null sends it now.
 */
data class SendOptions(
    val silent: Boolean = false,
    val scheduledAt: Long? = null,
)
