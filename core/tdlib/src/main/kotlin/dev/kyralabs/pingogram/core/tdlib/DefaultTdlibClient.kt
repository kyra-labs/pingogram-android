package dev.kyralabs.pingogram.core.tdlib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [TdlibClient] on TDLib's JNI client (`docs/TDLIB.md`, Kotlin wrapper).
 *
 * TDLib calls back on its own thread. Updates go from there into an unbounded queue, drained in order by one
 * coroutine on the Default dispatcher, which applies [authState] and [connectionState] and then emits to [updates]:
 * `tryEmit`, or, when slow collectors fill the buffer, log and suspend in `emit`. Nothing is ever dropped, and
 * TDLib's thread never blocks: it also delivers the answers to [send], so blocking it while a collector awaits one
 * would deadlock.
 *
 * On `AuthorizationStateWaitTdlibParameters` it sends the parameters from [TdlibParametersProvider]; on
 * `AuthorizationStateClosed` it creates a new TDLib instance, which starts over with `WaitTdlibParameters`.
 */
@Singleton
class DefaultTdlibClient internal constructor(
    private val native: TdlibNative,
    private val parametersProvider: TdlibParametersProvider,
    private val logSettings: () -> TdlibLogSettings,
    private val log: TdlibLog,
    private val scope: CoroutineScope,
    updateBufferCapacity: Int = UPDATE_BUFFER_CAPACITY,
) : TdlibClient {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        config: TdlibConfig,
        parametersProvider: TdlibParametersProvider,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        native = JniTdlib,
        parametersProvider = parametersProvider,
        // TDLib's log stream is the process's, one for every account: it stays in the first account's directory.
        logSettings = { TdlibLogSettings.of(config.isDebug, TdlibDirectories.of(context.filesDir, config.useTestDc)) },
        log = AndroidTdlibLog(config.isDebug),
        scope = clientScope(dispatcher, AndroidTdlibLog(config.isDebug)),
    )

    private val inbox = Channel<TdApi.Object>(Channel.UNLIMITED)

    private val mutableUpdates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = updateBufferCapacity)
    override val updates: SharedFlow<TdApi.Update> = mutableUpdates.asSharedFlow()

    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = mutableAuthState.asStateFlow()

    private val mutableConnectionState = MutableStateFlow(ConnectionState.Connecting)
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    private val started = AtomicBoolean(false)

    /** The current TDLib instance: null until [start] created it, replaced after `AuthorizationStateClosed`. */
    private val session = MutableStateFlow<Session?>(null)

    /**
     * The first session's parameters, prepared while TDLib's library loads and its client starts (the database key's
     * Keystore unwrap among them), as both lie on the way to the first login step, which the splash screen and the chat
     * list wait for (`docs/PERFORMANCE.md` §2). Taken once; later sessions and a retry with a new key build their own.
     */
    private val preparedParameters = AtomicReference<Deferred<TdApi.SetTdlibParameters>?>(null)

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        preparedParameters.set(scope.async { parametersProvider.parameters() })
        scope.launch { pumpUpdates() }
        scope.launch {
            configureLogging()
            session.value = newSession()
        }
    }

    override suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T> {
        start()
        return session.filterNotNull().first().send(function)
    }

    private suspend fun <T : TdApi.Object> Session.send(function: TdApi.Function<T>): TdResult<T> =
        suspendCancellableCoroutine { continuation ->
            // After cancellation resume() does nothing, which drops the late answer.
            client.send(function) { result -> continuation.resume(result.toTdResult()) }
        }

    private fun configureLogging() {
        // Before the first client exists, so TDLib never logs above this level (REQUIREMENTS.md §6.2).
        val settings = logSettings()
        native.execute(TdApi.SetLogVerbosityLevel(settings.verbosity)).warnOnFailure("SetLogVerbosityLevel")
        settings.file?.let { file ->
            file.parentFile?.mkdirs()
            native
                .execute(TdApi.SetLogStream(TdApi.LogStreamFile(file.path, settings.maxFileSize, false)))
                .warnOnFailure("SetLogStream")
        }
    }

    private fun newSession() =
        Session(
            native.createClient(
                // UNLIMITED: trySend always succeeds, and in order.
                onUpdate = { update -> inbox.trySend(update) },
                onException = { error -> log.warn("A TDLib callback threw", error) },
            ),
        )

    private suspend fun pumpUpdates() {
        for (update in inbox) {
            if (update !is TdApi.Update) continue
            applyState(update)
            if (!mutableUpdates.tryEmit(update)) {
                log.warn("TDLib update buffer is full; waiting for slow collectors")
                mutableUpdates.emit(update)
            }
        }
    }

    private fun applyState(update: TdApi.Update) {
        if (update is TdApi.UpdateAuthorizationState) {
            applyAuthorizationState(update.authorizationState)
        } else if (update is TdApi.UpdateConnectionState) {
            ConnectionState.from(update.state)?.let { mutableConnectionState.value = it }
        }
    }

    private fun applyAuthorizationState(state: TdApi.AuthorizationState) {
        val authState = AuthState.from(state)
        if (authState == null) {
            log.warn("Ignoring unknown authorization state ${state.constructor}")
            return
        }
        log.debug { "Authorization state: $authState" }
        mutableAuthState.value = authState
        if (authState == AuthState.WaitTdlibParameters) {
            scope.launch { sendParameters() }
        } else if (authState == AuthState.Closed) {
            mutableConnectionState.value = ConnectionState.Connecting
            session.value = newSession()
        }
    }

    private suspend fun sendParameters() {
        val session = session.value ?: return
        val parameters = preparedParameters.getAndSet(null)?.await() ?: parametersProvider.parameters()
        val result = session.send(parameters)
        if (result !is TdResult.Failure) return
        val error = result.error
        if (error.code == WRONG_DATABASE_KEY && session.databaseDiscarded.compareAndSet(false, true)) {
            log.warn("TDLib rejected the database key; deleting the local database and starting over")
            parametersProvider.discardDatabase()
            sendParameters()
        } else {
            log.warn("SetTdlibParameters failed: ${error.code} ${error.message}")
        }
    }

    private fun <T> TdResult<T>.warnOnFailure(what: String) {
        if (this is TdResult.Failure) log.warn("$what failed: ${error.code} ${error.message}")
    }

    private class Session(
        val client: NativeClient,
    ) {
        /** Discard the database at most once per instance, so a persistent 401 cannot loop. */
        val databaseDiscarded = AtomicBoolean(false)
    }

    internal companion object {
        /** Large enough to absorb the burst of updates after login without suspending the queue. */
        const val UPDATE_BUFFER_CAPACITY = 4096

        /** `SetTdlibParameters` answers 401 when the database key cannot open the database (td_api.tl). */
        private const val WRONG_DATABASE_KEY = 401

        private fun clientScope(
            dispatcher: CoroutineDispatcher,
            log: TdlibLog,
        ) = CoroutineScope(
            SupervisorJob() + dispatcher +
                CoroutineExceptionHandler { _, error -> log.warn("TDLib client failure", error) },
        )
    }
}

/**
 * TDLib's own logging, into a rotating file on the device ([file], moved to `<name>.old` above [maxFileSize]):
 * verbosity 1 (errors) and 1 MB in release, whose last lines the feedback report shows, redacted, for the user to share
 * (`docs/TESTING_GUIDE.md`); 3 and 10 MB in debug builds. Nothing reads it but that report; it never leaves the device
 * by itself.
 */
internal data class TdlibLogSettings(
    val verbosity: Int,
    val file: File?,
    val maxFileSize: Long = DEBUG_MAX_FILE_SIZE,
) {
    companion object {
        private const val RELEASE_VERBOSITY = 1
        private const val DEBUG_VERBOSITY = 3
        private const val RELEASE_MAX_FILE_SIZE = 1L * 1024 * 1024
        private const val DEBUG_MAX_FILE_SIZE = 10L * 1024 * 1024

        fun of(
            isDebug: Boolean,
            directories: TdlibDirectories,
        ) = if (isDebug) {
            TdlibLogSettings(DEBUG_VERBOSITY, directories.logFile, DEBUG_MAX_FILE_SIZE)
        } else {
            TdlibLogSettings(RELEASE_VERBOSITY, directories.logFile, RELEASE_MAX_FILE_SIZE)
        }
    }
}

/** TDLib answers a `Function<T>` with a `T` or a `TdApi.Error` (td_api.tl). */
@Suppress("UNCHECKED_CAST") // Guaranteed by TDLib as above; the cast is erased, so it cannot fail here anyway.
private fun <T : TdApi.Object> TdApi.Object.toTdResult(): TdResult<T> =
    if (this is TdApi.Error) TdResult.Failure(TdError(code, message)) else TdResult.Success(this as T)
