package dev.kyralabs.pingogram.core.tdlib

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

/**
 * The app's TDLib client (`docs/TDLIB.md`, `docs/ARCHITECTURE.md`). Only repositories in `core:data` use it;
 * ViewModels and composables never do (CLAUDE.md rule 6). Tests use `FakeTdlibClient` from `core:testing`.
 */
interface TdlibClient {
    /**
     * Every update from TDLib, in order and never dropped. Hot and without replay: subscribe before [start] to get
     * the first ones. [authState] and [connectionState] already reflect an update when it is emitted here.
     *
     * After `UpdateAuthorizationState(AuthorizationStateClosed)` the client starts a new TDLib instance; repositories
     * drop their caches when they see that update here ([authState] is conflated and may skip [AuthState.Closed]).
     */
    val updates: SharedFlow<TdApi.Update>

    val authState: StateFlow<AuthState>

    val connectionState: StateFlow<ConnectionState>

    /**
     * The servers the current TDLib instance talks to: [TdlibServers.Production] unless [useServers] chose the test
     * servers, which last until the next close (a logout, a deleted account) and across restarts until then.
     */
    val servers: StateFlow<TdlibServers>

    /** Starts TDLib in the background, without blocking the caller. Idempotent; [send] calls it too. */
    fun start()

    /**
     * Sends [function] to TDLib and suspends until it answers. Cancelling only stops the wait: TDLib cannot cancel a
     * request, so it still runs and its late answer is dropped.
     */
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T>

    /**
     * Restarts TDLib on [servers] unless it already talks to them: closes the current instance, whose `Closed` starts
     * the next one there, and returns once that one is past starting up (at `WaitPhoneNumber`, before a login). Only
     * sign-in calls it, before sending a number ([TdlibServers.Test] for a test account's, `REQUIREMENTS.md` FR-A9).
     */
    suspend fun useServers(servers: TdlibServers)
}
