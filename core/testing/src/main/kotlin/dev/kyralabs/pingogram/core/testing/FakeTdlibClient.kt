package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibServers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * [TdlibClient] for JVM tests of repositories and ViewModels (`docs/TESTING.md`: never mock TDLib's `Client`).
 *
 * - [send] records the function ([sent]) and answers with the handler registered for its class ([on]); without
 *   one it fails with [NO_RESPONSE]. Handlers may suspend, e.g. on a `CompletableDeferred`, to test loading states.
 * - [emit] delivers scripted updates. Like the real client, [updates] has no replay: subscribe first. [emit]
 *   suspends until every subscriber has taken the update, which keeps tests deterministic.
 * - [authState] and [connectionState] can be set directly; [emitAuthorizationState] and [emitConnectionState] set
 *   them and emit the matching update, in the real client's order.
 * - [useServers] switches [servers] at once, without a restart's updates, and records each switch ([serverSwitches]).
 */
class FakeTdlibClient(
    authState: AuthState = AuthState.Initializing,
    connectionState: ConnectionState = ConnectionState.Connecting,
) : TdlibClient {
    override val authState = MutableStateFlow(authState)
    override val connectionState = MutableStateFlow(connectionState)
    override val servers = MutableStateFlow(TdlibServers.Production)

    private val switches = CopyOnWriteArrayList<TdlibServers>()

    /** The servers of every [useServers] that switched, in order. */
    val serverSwitches: List<TdlibServers> get() = switches.toList()

    private val mutableUpdates = MutableSharedFlow<TdApi.Update>()
    override val updates: SharedFlow<TdApi.Update> = mutableUpdates.asSharedFlow()

    private val handlers = ConcurrentHashMap<Class<*>, suspend (TdApi.Function<*>) -> TdResult<TdApi.Object>>()
    private val sentFunctions = CopyOnWriteArrayList<TdApi.Function<*>>()

    /** Every function passed to [send], in order. */
    val sent: List<TdApi.Function<*>> get() = sentFunctions.toList()

    var isStarted: Boolean = false
        private set

    override fun start() {
        isStarted = true
    }

    /** Answers every [function] request with [handler]; replaces an earlier registration. */
    fun <R : TdApi.Object, F : TdApi.Function<R>> on(
        function: KClass<F>,
        handler: suspend (F) -> TdResult<R>,
    ) {
        // Only functions of this class are passed to it, and TdResult is covariant, so these casts hold.
        @Suppress("UNCHECKED_CAST")
        handlers[function.java] = { sentFunction -> handler(sentFunction as F) }
    }

    /** Answers every [function] request with [result]. */
    fun <R : TdApi.Object, F : TdApi.Function<R>> on(
        function: KClass<F>,
        result: TdResult<R>,
    ) = on(function) { result }

    override suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T> {
        sentFunctions += function
        val handler =
            handlers[function.javaClass]
                ?: return TdResult.Failure(
                    TdError(NO_RESPONSE, "FakeTdlibClient has no response for ${function.javaClass.simpleName}"),
                )
        // The handler was registered for this function class, so its result has the function's result type.
        @Suppress("UNCHECKED_CAST")
        return handler(function) as TdResult<T>
    }

    override suspend fun useServers(servers: TdlibServers) {
        if (this.servers.value == servers) return
        switches += servers
        this.servers.value = servers
    }

    /** The sent functions of type [F]. */
    inline fun <reified F : TdApi.Function<*>> sentOf(): List<F> = sent.filterIsInstance<F>()

    suspend fun emit(update: TdApi.Update) {
        mutableUpdates.emit(update)
    }

    suspend fun emitAuthorizationState(state: TdApi.AuthorizationState) {
        authState.value = checkNotNull(AuthState.from(state)) { "Unknown authorization state ${state.constructor}" }
        emit(TdApi.UpdateAuthorizationState(state))
    }

    suspend fun emitConnectionState(state: TdApi.ConnectionState) {
        connectionState.value =
            checkNotNull(ConnectionState.from(state)) { "Unknown connection state ${state.constructor}" }
        emit(TdApi.UpdateConnectionState(state))
    }

    companion object {
        /** [TdError.code] of the failure for a function without a registered response. */
        const val NO_RESPONSE = 599
    }
}
