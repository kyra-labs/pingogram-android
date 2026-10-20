package dev.kyralabs.pingogram.core.domain.model

/**
 * How messages are forwarded (`REQUIREMENTS.md` FR-C6).
 *
 * @property hideSenderName sends copies instead of forwards: the messages show as the user's own, without "Forwarded
 *   from" (td_api.tl `forwardMessages.send_copy`). Only for messages whose `MessageProperties.canBeCopied`.
 * @property hideCaptions leaves the captions of photos, videos, files and voice messages out of the copies
 *   (`remove_caption`); only with [hideSenderName], as TDLib keeps the captions of forwards.
 */
data class ForwardOptions(
    val hideSenderName: Boolean = false,
    val hideCaptions: Boolean = false,
)
