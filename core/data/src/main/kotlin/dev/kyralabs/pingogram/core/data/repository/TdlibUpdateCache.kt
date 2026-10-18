package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * A repository that caches state from TDLib's updates (`docs/ARCHITECTURE.md`, Repositories). TDLib introduces each
 * user and chat only once (`updateUser`, `updateNewChat`, …), and [TdlibClient.updates] replays nothing, so such a
 * repository must exist before TDLib starts: `UpdateCacheModule` binds each one into a set that `PingogramApplication`
 * injects before it calls [TdlibClient.start]. The repository keeps its state in an [UpdateStore].
 */
interface TdlibUpdateCache

/**
 * The in-memory state of a [TdlibUpdateCache]: an immutable snapshot [S] (persistent collections), reduced from every
 * TDLib update by [reduce] and published through [state].
 *
 * - The constructor returns only once it is subscribed to [client]'s updates, so it misses no update emitted later.
 *   Do not send requests while constructing: [TdlibClient.send] starts TDLib before the other caches subscribed.
 * - Updates are applied one at a time, in TDLib's order, on [scope]'s dispatcher (the `@ApplicationScope`).
 * - `UpdateAuthorizationState(AuthorizationStateClosed)` resets the state to [empty]: ids belong to one TDLib
 *   instance, and the next one announces everything again. It is read from [TdlibClient.updates], not from the
 *   conflated `authState`, which can skip it.
 * - [update] changes the state from elsewhere, e.g. with a request's answer, atomically with the updates.
 *
 * [reduce] runs for every update TDLib sends, so it must be fast and return the same instance for updates it ignores.
 * It must be pure: it runs again for the same update when [update] changed the state in between.
 */
internal class UpdateStore<S : Any>(
    client: TdlibClient,
    scope: CoroutineScope,
    private val empty: S,
    private val reduce: (state: S, update: TdApi.Update) -> S,
) {
    private val mutableState = MutableStateFlow(empty)

    val state: StateFlow<S> = mutableState.asStateFlow()

    init {
        // UNDISPATCHED runs the coroutine right here until it first suspends, which is after collect() registered it
        // with the SharedFlow. Operators that switch context (flowOn, buffer) would subscribe later, elsewhere.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update ->
                mutableState.update { state -> if (update.closesTdlib()) empty else reduce(state, update) }
            }
        }
    }

    /** Replaces the state with [transform] of it; [transform] must be pure, as it may run more than once. */
    fun update(transform: (S) -> S) = mutableState.update(transform)
}

private fun TdApi.Update.closesTdlib() =
    this is TdApi.UpdateAuthorizationState && authorizationState is TdApi.AuthorizationStateClosed
