package dev.kyralabs.pingogram.core.tdlib

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Answer to [TdlibClient.send]: TDLib answers every request with its result type or a `TdApi.Error`. */
sealed interface TdResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : TdResult<T>

    data class Failure(
        val error: TdError,
    ) : TdResult<Nothing>
}

/**
 * A `TdApi.Error`. [code] is HTTP-like (400 bad request, 401 unauthorized, 404 not found, 406 must be ignored
 * silently, 429 too many requests, 500 internal); [message] is TDLib's text such as `PHONE_CODE_INVALID`, shown to
 * users only in debug builds (`docs/ARCHITECTURE.md`, Error handling). Repositories map it to domain errors.
 */
data class TdError(
    val code: Int,
    val message: String,
) {
    /**
     * How long Telegram asks to wait before trying again, from `FLOOD_WAIT_x` or TDLib's
     * `Too Many Requests: retry after x`; null for any other error. Drives the countdown UI (`REQUIREMENTS.md` §6.5).
     */
    val retryAfter: Duration?
        get() =
            FLOOD_WAIT
                .find(message)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?.seconds

    private companion object {
        val FLOOD_WAIT = Regex("""(?:FLOOD_WAIT_|retry after )(\d+)""", RegexOption.IGNORE_CASE)
    }
}
