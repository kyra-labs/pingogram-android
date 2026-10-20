package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * Push notifications for the logged-in account (`REQUIREMENTS.md` FR-N3, `docs/adr/0004-push-delivery.md`): the
 * device's push token goes to Telegram, which then sends each new message, encrypted, through the push service, and
 * TDLib turns a received push into notifications. Only the `gms` flavor has a push service ([PushTokenSource],
 * Firebase Cloud Messaging); without one, nothing here does anything.
 */
interface PushRepository {
    /**
     * Follows the login, once per process: at every login (and every start while logged in) it registers the push
     * service's current token with Telegram; at logout it deletes the token, so nothing more reaches it. Returns at
     * once.
     */
    fun start()

    /** The push service issued a new [token]; it is registered now if the account is logged in, else at login. */
    fun onNewToken(token: String)

    /**
     * Hands a received push, [payload] (its data as JSON), to TDLib and returns once TDLib has turned it into
     * notifications, or after a few seconds, the time a push service gives an app: TDLib may have to connect first, or
     * hold back notifications for a moment. Fails with `DomainError.Silent` for a push TDLib cannot read without
     * connecting (it then fetches what is new itself), or another `DomainError`.
     */
    suspend fun processPush(payload: String): Outcome<Unit>
}

/**
 * The device's push service, as far as Telegram needs it: Firebase Cloud Messaging in the `gms` flavor. The `foss`
 * flavor has none and binds none.
 */
interface PushTokenSource {
    /** The current token, made if there is none yet; null if the push service is unavailable (no Play services). */
    suspend fun token(): String?

    /** Deletes the current token, so the push service drops whatever is still sent to it. */
    suspend fun deleteToken()
}
