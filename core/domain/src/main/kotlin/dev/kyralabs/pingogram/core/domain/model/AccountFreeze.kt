package dev.kyralabs.pingogram.core.domain.model

/**
 * The current account frozen by Telegram for breaking its rules (td_api.tl `updateFreezeState`): it can read but not
 * send, since [frozenAt], until Telegram deletes it at [deletesAt] (both Unix seconds, 0 where Telegram gave none),
 * unless an appeal through [appealLink] succeeds.
 */
data class AccountFreeze(
    val frozenAt: Long,
    val deletesAt: Long,
    val appealLink: String,
)
