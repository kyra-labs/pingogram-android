package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.error.toOutcome
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a request may wait for TDLib, which queues requests while offline (`docs/PERFORMANCE.md` §3). */
internal val REQUEST_TIMEOUT = 30.seconds

/**
 * How long an authentication request may wait. TDLib gives queries up to 60 seconds (`total_timeout_limit`,
 * td/telegram/net/NetQueryCreator.cpp), waiting out short `FLOOD_WAIT`s and resending by itself before it answers.
 * Giving up earlier would report "no connection" for a request still running, and a retry would send another code
 * (or be refused while the first one runs).
 */
internal val AUTH_REQUEST_TIMEOUT = 90.seconds

/** Sends [function] and maps the answer; no answer within [timeout] fails with [DomainError.Network]. */
internal suspend fun <T : TdApi.Object> TdlibClient.request(
    function: TdApi.Function<T>,
    timeout: Duration = REQUEST_TIMEOUT,
): Outcome<T> = withTimeoutOrNull(timeout) { send(function).toOutcome() } ?: Outcome.Failure(DomainError.Network)
