package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OauthLoginRequest
import kotlinx.coroutines.flow.Flow

/**
 * "Log in with Telegram" (`docs/audit/feature-gaps-telegram-2026.md`): a website's or an app's request to log in with
 * the account. Nothing is shared before [accept].
 */
interface OauthLoginRepository {
    /** The links of the requests Telegram sends while the app runs (`updateNewOauthRequest`), each valid for 60 s. */
    val requests: Flow<String>

    /** What the request of [url] asks for (`getOauthLinkInfo`). */
    suspend fun request(url: String): Outcome<OauthLoginRequest>

    /** Tells Telegram the code the user picked ([code]); a wrong one fails, and the request with it. */
    suspend fun checkMatchCode(
        url: String,
        code: String,
    ): Outcome<Unit>

    /**
     * Logs in, sharing the phone number only with [sharePhoneNumber] and letting the bot write only with
     * [allowMessages]; never the TON wallet. Answers the address to open, where the website goes on.
     */
    suspend fun accept(
        url: String,
        matchCode: String,
        allowMessages: Boolean,
        sharePhoneNumber: Boolean,
    ): Outcome<String>

    /** Declines the request (`declineOauthRequest`). */
    suspend fun decline(url: String): Outcome<Unit>
}
