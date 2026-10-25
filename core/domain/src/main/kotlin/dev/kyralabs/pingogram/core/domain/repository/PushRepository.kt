package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Push notifications for one account (`REQUIREMENTS.md` FR-N3, `docs/adr/0004-push-delivery.md`): the device's push
 * token goes to Telegram, which then sends each new message, encrypted, through the push service, and TDLib turns a
 * received push into notifications. Only the `gms` flavor has a push service ([PushTokenSource], Firebase Cloud
 * Messaging); without one, nothing here does anything. The app's push service reaches the accounts through
 * [DevicePushRepository], which hands each push to the account it is for.
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
     * The id Telegram gave this account's registration of the token (td_api.tl `pushReceiverId`), which names the
     * account a push is for ([receiverIdOf]); null until registered, and after a logout.
     */
    val receiverId: StateFlow<Long?>

    /** The receiver id a push with these [data] fields names (td_api.tl `getPushReceiverId`); null if none. */
    suspend fun receiverIdOf(data: Map<String, String>): Long?

    /**
     * Hands a received push to TDLib: its [data] fields as the push service delivered them, the time Telegram sent it
     * ([sentTimeMillis]) and the sound of its notification part, if it has one ([notificationSound]). Returns once
     * TDLib has turned it into notifications, or after a few seconds, the time a push service gives an app: TDLib may
     * have to connect first, or hold back notifications for a moment. Fails with `DomainError.Silent` for a push TDLib
     * cannot read without connecting (it then fetches what is new itself), or another `DomainError`.
     */
    suspend fun processPush(
        data: Map<String, String>,
        sentTimeMillis: Long,
        notificationSound: String?,
    ): Outcome<Unit>
}

/**
 * The app's push service and every account on the device (`docs/adr/0031-multiple-accounts.md`): each account
 * registers the one token, naming the other accounts' users so Telegram knows the device holds them, and a push goes
 * to the account its receiver id names ([PushRepository.receiverIdOf]), or to every account when none matches.
 */
interface DevicePushRepository {
    /** Starts every account's [PushRepository.start], the accounts added later too; once per process. */
    fun start()

    /** The push service issued a new [token], for every account. */
    fun onNewToken(token: String)

    /** Hands a received push to the account it is for ([PushRepository.processPush]). */
    suspend fun processPush(
        data: Map<String, String>,
        sentTimeMillis: Long,
        notificationSound: String?,
    ): Outcome<Unit>
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
