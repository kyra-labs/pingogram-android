package dev.kyralabs.pingogram.core.data.updates

import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The one collector of a [TdlibClient]'s updates (`docs/ARCHITECTURE.md`, "TDLib updates"; `docs/PERFORMANCE.md`
 * §3): it takes each update once and hands it only to those that asked for its type, so a repository wakes for the
 * few kinds of update it handles rather than for each of TDLib's thousands. Nothing else collects
 * [TdlibClient.updates] (`TdlibUpdatesSubscriptionTest`).
 *
 * - [onUpdates] calls a handler with each update of its types, in TDLib's order, inline in the router's collector:
 *   as [TdlibClient.updates]' own collectors were, so a handler must be quick and must not suspend or block
 *   (`UpdateStore`'s reducers, a flag set, a `trySend`). One that throws is dropped, its error reported on the scope.
 * - [updates] is a flow of the updates of its types for pipelines, buffered per collector; a full buffer holds the
 *   router back, as a slow collector held [TdlibClient.updates] back, so nothing is lost. A collector cancelled
 *   midway leaves at once.
 * - [onSessionEnd] calls a handler as the session ends: `AuthorizationStateLoggingOut`, then
 *   `AuthorizationStateClosed`, read from the updates, as `authState` is conflated and may skip them.
 *
 * Each sees its updates in TDLib's order, across its types, and only those that come after it subscribed: as with
 * [TdlibClient.updates], which replays nothing. The router subscribes as it is made ([of]), before TDLib starts.
 */
internal class TdlibUpdateRouter private constructor(
    private val scope: CoroutineScope,
) {
    private class Handler(
        val types: Set<Class<*>>,
        val onUpdate: (TdApi.Update) -> Unit,
    )

    private class Subscriber(
        val types: Set<Class<*>>,
        val channel: Channel<TdApi.Update>,
    )

    private val handlers = CopyOnWriteArrayList<Handler>()
    private val subscribers = CopyOnWriteArrayList<Subscriber>()
    private val sessionEndHandlers = CopyOnWriteArrayList<() -> Unit>()

    /** Calls [onUpdate] with each update of [types] from now on, inline, until the returned handle is disposed. */
    fun onUpdates(
        types: Set<KClass<out TdApi.Update>>,
        onUpdate: (TdApi.Update) -> Unit,
    ): DisposableHandle {
        val handler = Handler(types.javaClasses(), onUpdate)
        handlers += handler
        return DisposableHandle { handlers -= handler }
    }

    /** The updates of [types] from when it is collected, in TDLib's order; collected with `UNDISPATCHED`, at once. */
    fun updates(types: Set<KClass<out TdApi.Update>>): Flow<TdApi.Update> {
        val classes = types.javaClasses()
        return flow {
            val subscriber = Subscriber(classes, Channel(BUFFER_CAPACITY))
            subscribers += subscriber
            try {
                for (update in subscriber.channel) emit(update)
            } finally {
                subscribers -= subscriber
                subscriber.channel.cancel()
            }
        }
    }

    /** Calls [onEnd] as the session ends (logging out, closing), inline, until the returned handle is disposed. */
    fun onSessionEnd(onEnd: () -> Unit): DisposableHandle {
        sessionEndHandlers += onEnd
        return DisposableHandle { sessionEndHandlers -= onEnd }
    }

    private fun start(client: TdlibClient) {
        // UNDISPATCHED: subscribed when this returns, so the caches made next miss nothing TDLib sends.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { client.updates.collect(::route) }
    }

    private suspend fun route(update: TdApi.Update) {
        val type = update.javaClass
        for (handler in handlers) {
            if (type in handler.types) call(handler.onUpdate, update) { handlers -= handler }
        }
        if (update.endsSession()) {
            for (onEnd in sessionEndHandlers) call({ onEnd() }, update) { sessionEndHandlers -= onEnd }
        }
        for (subscriber in subscribers) {
            if (type in subscriber.types) deliver(subscriber, update)
        }
    }

    /** Calls [handler]; one that throws is dropped ([drop]) and its error reported on [scope], as a collector's was. */
    private inline fun call(
        handler: (TdApi.Update) -> Unit,
        update: TdApi.Update,
        drop: () -> Unit,
    ) {
        // A handler's bug must not stop the others' updates.
        runCatching { handler(update) }.onFailure { error ->
            drop()
            scope.launch { throw error }
        }
    }

    private suspend fun deliver(
        subscriber: Subscriber,
        update: TdApi.Update,
    ) {
        if (subscriber.channel.trySend(update).isSuccess) return
        // Fails when the subscriber was cancelled meanwhile, its channel with it: the update was its, so nothing is
        // lost. A failure because the router itself was cancelled goes on.
        if (runCatching { subscriber.channel.send(update) }.isFailure) currentCoroutineContext().ensureActive()
    }

    companion object {
        /** Updates a [updates] collector may fall behind by before the router waits for it. */
        private const val BUFFER_CAPACITY = 256

        private val routers = WeakHashMap<TdlibClient, TdlibUpdateRouter>()

        /**
         * The router of [client], made and subscribed to its updates on first use, in [scope]: the account's scope,
         * which every repository of one client shares. Keyed by the client, so each client has exactly one.
         */
        fun of(
            client: TdlibClient,
            scope: CoroutineScope,
        ): TdlibUpdateRouter =
            synchronized(routers) {
                routers.getOrPut(client) { TdlibUpdateRouter(scope).also { it.start(client) } }
            }
    }
}

/** The router of this client ([TdlibUpdateRouter.of]). */
internal fun TdlibClient.updateRouter(scope: CoroutineScope): TdlibUpdateRouter = TdlibUpdateRouter.of(this, scope)

private fun Set<KClass<out TdApi.Update>>.javaClasses(): Set<Class<*>> = mapTo(HashSet()) { it.java }

private fun TdApi.Update.endsSession(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        (
            authorizationState is TdApi.AuthorizationStateLoggingOut ||
                authorizationState is TdApi.AuthorizationStateClosed
        )
