package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallEngine
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.CallSession
import dev.kyralabs.pingogram.feature.calls.system.CallSystem
import dev.kyralabs.pingogram.feature.calls.system.SystemCall
import dev.kyralabs.pingogram.feature.calls.system.SystemCallListener
import dev.kyralabs.pingogram.feature.calls.system.SystemCallRequest
import dev.kyralabs.pingogram.feature.calls.system.SystemEndCause
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the user's call (`REQUIREMENTS.md` FR-V1, `docs/ARCHITECTURE.md`, Calls): TDLib's signaling ([CallRepository]),
 * the engine that moves the audio ([CallEngine]), Android's Telecom ([CallSystem]), the foreground service and the
 * tones, as one state machine whose state is [call]. Started with the app ([start]) so it sees incoming calls whenever
 * TDLib announces them, in the background too.
 *
 * One call at a time: an incoming call while the user is in another is declined, as Telegram's apps do.
 *
 * Every change happens under [mutex], in the order TDLib's updates and the user's actions arrive. The engine's data
 * from the other side that comes before the engine runs (it may: TDLib passes it on as soon as the other side sends
 * it) is kept per call until the engine connects.
 */
@Singleton
@Suppress("TooManyFunctions") // One function per action of the user and per transition of the state machine.
class CallManager
    @Inject
    @Suppress("LongParameterList") // Injected: TDLib's calls, engine, Telecom, service, tones, clock, names, scope.
    internal constructor(
        private val callRepository: CallRepository,
        private val engine: CallEngine,
        private val system: CallSystem,
        private val foreground: CallForeground,
        private val tones: CallTones,
        private val clock: CallClock,
        private val names: CallerNames,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val mutableCall = MutableStateFlow<ActiveCall?>(null)

        /** The call the user is in, then every change; null without one. An ended call stays for [ENDED_SHOWN]. */
        val call: StateFlow<ActiveCall?> = mutableCall.asStateFlow()

        private val mutex = Mutex()

        @Volatile private var live: Live? = null

        /** Ids of the calls already handled (taken, declined as busy, or ended): an update never starts them again. */
        private val seen = mutableSetOf<Int>()
        private val earlySignaling = mutableMapOf<Int, MutableList<ByteArray>>()
        private var clearEnded: Job? = null

        private var started = false

        /** Starts following TDLib's calls; `PingogramApplication` calls it once, before TDLib starts. */
        fun start() {
            if (started) return
            started = true
            scope.launch { callRepository.calls.collect { calls -> mutex.withLock { onCalls(calls) } } }
            scope.launch { callRepository.signalingData.collect { data -> mutex.withLock { onSignaling(data) } } }
        }

        /**
         * Calls user [userId]. Does nothing while the user is in another call: the screen shows that one. A call that
         * cannot be made ends at once with the reason ([CallEndReason.Failed]).
         */
        suspend fun startCall(userId: Long) {
            val call =
                mutex.withLock {
                    if (live != null) return
                    Live(userId, isOutgoing = true, scope = callScope()).also { start(it) }
                }
            val protocol = protocolOrNull()
            val result = protocol?.let { callRepository.startCall(userId, isVideo = false, protocol = it) }
            mutex.withLock {
                if (live !== call) {
                    // Hung up and gone from the screen before Telegram created it: end it there too.
                    if (result is Outcome.Success) {
                        scope.launch {
                            callRepository.discardCall(
                                result.value,
                                false,
                                Duration.ZERO,
                                false,
                                0,
                            )
                        }
                    }
                    return
                }
                when (result) {
                    null -> {
                        finish(
                            call,
                            CallEndReason.Failed(DomainError.Unknown(0, "The call engine could not start")),
                        )
                    }

                    is Outcome.Failure -> {
                        finish(call, CallEndReason.Failed(result.error))
                    }

                    is Outcome.Success -> {
                        adopt(call, result.value)
                    }
                }
            }
        }

        /** Answers the incoming call that rings. */
        @Suppress("ReturnCount") // Guards under the lock: no call, no id yet, or nothing to answer.
        suspend fun answer() {
            val call =
                mutex.withLock {
                    val call = live ?: return
                    val callId = call.callId ?: return
                    if (call.isOutgoing || call.answered) return
                    call.answered = true
                    call.phase = CallPhase.Connecting
                    foreground.start(withMicrophone = true)
                    call.system?.answer()
                    publish()
                    call to callId
                }
            val protocol = protocolOrNull()
            val result = protocol?.let { callRepository.acceptCall(call.second, it) }
            if (result !is Outcome.Success) {
                mutex.withLock {
                    if (live !== call.first) return
                    val error =
                        (result as? Outcome.Failure)?.error ?: DomainError.Unknown(0, "The call engine could not start")
                    discard(call.first, isDisconnected = false)
                    finish(call.first, CallEndReason.Failed(error))
                }
            }
        }

        /** Declines the incoming call that rings, cancels the outgoing one, or hangs up. */
        @Suppress("ReturnCount") // Guards under the lock: no call, already ended, not created yet.
        suspend fun hangUp() {
            mutex.withLock {
                val call = live ?: return
                if (call.phase is CallPhase.Ended) return
                if (call.callId == null) {
                    // Telegram has not created it yet: it is dropped when TDLib answers (startCall sees it ended).
                    finish(call, CallEndReason.Missed)
                    return
                }
                discard(call, isDisconnected = false)
            }
        }

        suspend fun setMuted(muted: Boolean) {
            mutex.withLock {
                val call = live ?: return
                applyMuted(call, muted)
            }
        }

        /** Puts the call on hold, or takes it back: Android's Telecom tells the other calls and the system UI. */
        suspend fun setOnHold(onHold: Boolean) {
            mutex.withLock {
                val call = live ?: return
                if (call.phase !is CallPhase.Connected && call.phase !is CallPhase.Reconnecting) return
                if (onHold) call.system?.setInactive() else call.system?.setActive()
                applyHold(call, onHold)
            }
        }

        fun selectRoute(route: AudioRoute) {
            live?.system?.selectRoute(route)
        }

        // Transitions; all under the mutex.

        private fun onCalls(calls: ImmutableMap<Int, Call>) {
            val current = live
            if (current != null) adoptAnnounced(current, calls)
            current?.callId?.let { id -> calls[id]?.let { onCall(current, it) } }
            calls.values
                .filter { !it.isOutgoing && it.id !in seen && it.state is CallState.Pending }
                .forEach(::onIncoming)
        }

        /** An outgoing call whose id TDLib announced before createCall answered. */
        private fun adoptAnnounced(
            current: Live,
            calls: ImmutableMap<Int, Call>,
        ) {
            if (!current.isOutgoing || current.callId != null) return
            calls.values
                .firstOrNull { it.isOutgoing && it.userId == current.userId && it.id !in seen && it.state.isRinging() }
                ?.let { adopt(current, it.id) }
        }

        private fun onIncoming(call: Call) {
            seen += call.id
            if (live == null) {
                ring(call)
            } else {
                // Busy: TDLib tells the caller it was declined.
                scope.launch { callRepository.discardCall(call.id, false, Duration.ZERO, false, 0) }
            }
        }

        private fun ring(call: Call) {
            val incoming = Live(call.userId, isOutgoing = false, scope = callScope())
            incoming.callId = call.id
            incoming.phase = CallPhase.Ringing(reachedDevice = true)
            start(incoming)
        }

        /** Makes [call] the user's call and tells Android about it. */
        private fun start(call: Live) {
            clearEnded?.cancel()
            live = call
            publish()
            foreground.start(withMicrophone = call.isOutgoing)
            call.scope.launch {
                val request = SystemCallRequest(names.name(call.userId), call.userId, call.isOutgoing)
                mutex.withLock {
                    if (live !== call || call.phase is CallPhase.Ended) return@launch
                    val system = system.add(call.scope, request, listener(call))
                    call.system = system
                    if (call.phase is CallPhase.Connected) system.setActive()
                    call.scope.launch { system.audio.collect { audio -> mutex.withLock { updateAudio(call, audio) } } }
                    // The system's mute (a headset's button, the system UI) after the value it starts with.
                    call.scope.launch {
                        system.isMuted.drop(1).collect { muted ->
                            mutex.withLock { applyMuted(call, muted) }
                        }
                    }
                }
            }
        }

        private fun adopt(
            call: Live,
            callId: Int,
        ) {
            if (call.callId != null) return
            call.callId = callId
            seen += callId
            if (call.phase is CallPhase.Ended) {
                // Hung up before Telegram created it.
                scope.launch { callRepository.discardCall(callId, false, Duration.ZERO, false, 0) }
                return
            }
            call.phase = CallPhase.Ringing(reachedDevice = false)
            publish()
        }

        private fun onCall(
            call: Live,
            update: Call,
        ) {
            if (call.phase is CallPhase.Ended) return
            when (val state = update.state) {
                is CallState.Pending -> {
                    if (call.isOutgoing) {
                        if (state.isReceived && !call.ringbackStarted) {
                            call.ringbackStarted = true
                            tones.ringback()
                        }
                        call.phase = CallPhase.Ringing(reachedDevice = state.isReceived)
                    }
                }

                CallState.ExchangingKeys -> {
                    tones.stop()
                    call.phase = CallPhase.Connecting
                }

                is CallState.Ready -> {
                    tones.stop()
                    call.emojis = state.connection.emojis
                    if (call.session == null && !call.connecting) connect(call, update.id, state.connection)
                }

                CallState.HangingUp -> {
                    tones.stop()
                }

                is CallState.Discarded -> {
                    finish(call, state.reason.toEndReason(call))
                }

                is CallState.Failed -> {
                    finish(call, CallEndReason.Failed(state.error))
                }
            }
            publish()
        }

        private fun connect(
            call: Live,
            callId: Int,
            connection: CallConnection,
        ) {
            call.connecting = true
            if (call.phase !is CallPhase.Connected) call.phase = CallPhase.Connecting
            call.scope.launch {
                val session = engine.connect(callId, call.isOutgoing, connection)
                val early =
                    mutex.withLock {
                        if (live !== call || call.phase is CallPhase.Ended) {
                            session.close()
                            return@launch
                        }
                        call.session = session
                        earlySignaling.remove(callId).orEmpty()
                    }
                for (data in early) session.receiveSignalingData(data)
                if (call.muted) session.setMuted(true)
                call.scope.launch {
                    session.signalingData.collect { data -> callRepository.sendSignalingData(callId, data) }
                }
                call.scope.launch { session.state.collect { media -> mutex.withLock { onMedia(call, media) } } }
            }
        }

        private fun onMedia(
            call: Live,
            media: CallMediaState,
        ) {
            if (live !== call || call.phase is CallPhase.Ended) return
            when (media) {
                CallMediaState.Connected -> {
                    if (call.connectedAt == null) {
                        call.connectedAt = clock.elapsedRealtimeMillis()
                        call.system?.setActive()
                    }
                    call.phase = CallPhase.Connected
                }

                CallMediaState.Connecting -> {
                    call.phase = if (call.connectedAt == null) CallPhase.Connecting else CallPhase.Reconnecting
                }

                CallMediaState.Failed -> {
                    // NTgCalls gave up finding a way to the other side: hang up as disconnected.
                    discard(call, isDisconnected = true)
                }

                CallMediaState.Closed -> {
                    Unit
                }
            }
            publish()
        }

        private fun onSignaling(data: CallSignalingData) {
            val call = live
            val session = call?.session
            if (session != null && call.callId == data.callId) {
                call.scope.launch { session.receiveSignalingData(data.data()) }
            } else if (data.callId !in seen || call?.callId == data.callId) {
                // Before the engine of the call runs; a few packets at most.
                val queue = earlySignaling.getOrPut(data.callId) { mutableListOf() }
                if (queue.size < EARLY_SIGNALING_MAX) queue += data.data()
            }
        }

        private fun applyMuted(
            call: Live,
            muted: Boolean,
        ) {
            if (call.muted == muted) return
            call.muted = muted
            call.session?.let { session -> call.scope.launch { session.setMuted(muted) } }
            publish()
        }

        private fun applyHold(
            call: Live,
            onHold: Boolean,
        ) {
            if (call.onHold == onHold) return
            call.onHold = onHold
            call.session?.let { session -> call.scope.launch { session.setOnHold(onHold) } }
            publish()
        }

        private fun updateAudio(
            call: Live,
            audio: CallAudio,
        ) {
            if (live !== call) return
            call.audio = audio
            publish()
        }

        private fun listener(call: Live) =
            object : SystemCallListener {
                override fun onAnswer() {
                    scope.launch { if (live === call) answer() }
                }

                override fun onDisconnect() {
                    scope.launch { if (live === call) hangUp() }
                }

                override fun onHold(onHold: Boolean) {
                    scope.launch { mutex.withLock { if (live === call) applyHold(call, onHold) } }
                }
            }

        /** Asks TDLib to end [call]; [onCall] finishes it once TDLib says it is discarded. */
        private fun discard(
            call: Live,
            isDisconnected: Boolean,
        ) {
            val callId = call.callId ?: return
            if (call.discarding) return
            call.discarding = true
            val duration = call.connectedAt?.let { (clock.elapsedRealtimeMillis() - it).milliseconds } ?: Duration.ZERO
            scope.launch {
                val result =
                    callRepository.discardCall(
                        callId,
                        isDisconnected,
                        duration,
                        isVideo = false,
                        connectionId = 0,
                    )
                if (result is Outcome.Failure) {
                    // TDLib could not tell Telegram (no network): end here anyway; Telegram ends it on its side too.
                    mutex.withLock {
                        if (live ===
                            call
                        ) {
                            finish(call, if (isDisconnected) CallEndReason.Disconnected else CallEndReason.HungUp)
                        }
                    }
                }
            }
        }

        private fun finish(
            call: Live,
            reason: CallEndReason,
        ) {
            if (call.phase is CallPhase.Ended) return
            call.phase = CallPhase.Ended(reason)
            call.callId?.let {
                seen += it
                earlySignaling.remove(it)
            }
            if (reason == CallEndReason.Declined && call.isOutgoing) tones.busy() else tones.stop()
            call.system?.end(reason.toSystemCause(call))
            val session = call.session
            scope.launch {
                session?.close()
                call.scope.cancel()
            }
            foreground.stop()
            publish()
            clearEnded =
                scope.launch {
                    delay(ENDED_SHOWN)
                    mutex.withLock {
                        if (live === call) {
                            live = null
                            mutableCall.value = null
                        }
                    }
                }
        }

        private fun publish() {
            mutableCall.value = live?.toActiveCall()
        }

        /** The engine's protocol; null if its library could not load (UnsatisfiedLinkError): the call fails then. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any failure to load ends the call with a reason.
        private suspend fun protocolOrNull(): CallProtocol? =
            try {
                engine.protocol()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }

        private fun callScope() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

        /** The call the user is in, as the manager keeps it. */
        private class Live(
            val userId: Long,
            val isOutgoing: Boolean,
            /** Everything of this call runs in it; cancelled when it ends. */
            val scope: CoroutineScope,
        ) {
            var callId: Int? = null
            var phase: CallPhase = CallPhase.Requesting
            var emojis: ImmutableList<String> = persistentListOf()
            var muted = false
            var onHold = false
            var audio = CallAudio()
            var connectedAt: Long? = null
            var answered = false
            var connecting = false
            var discarding = false
            var ringbackStarted = false
            var system: SystemCall? = null
            var session: CallSession? = null

            fun toActiveCall() =
                ActiveCall(
                    userId = userId,
                    callId = callId,
                    isOutgoing = isOutgoing,
                    phase = phase,
                    emojis = emojis,
                    isMuted = muted,
                    isOnHold = onHold,
                    audio = audio,
                    connectedAtMillis = connectedAt,
                )
        }

        private fun CallDiscardReason.toEndReason(call: Live): CallEndReason =
            when (this) {
                CallDiscardReason.Missed -> {
                    CallEndReason.Missed
                }

                CallDiscardReason.Declined -> {
                    CallEndReason.Declined
                }

                CallDiscardReason.Disconnected -> {
                    CallEndReason.Disconnected
                }

                CallDiscardReason.HungUp, CallDiscardReason.UpgradedToGroupCall, CallDiscardReason.Unknown -> {
                    // Hung up before it connected: for the user, a call that did not happen.
                    if (call.connectedAt == null && !call.answered &&
                        !call.isOutgoing
                    ) {
                        CallEndReason.Missed
                    } else {
                        CallEndReason.HungUp
                    }
                }
            }

        private fun CallEndReason.toSystemCause(call: Live): SystemEndCause =
            when (this) {
                CallEndReason.HungUp -> if (call.discarding) SystemEndCause.Local else SystemEndCause.Remote
                CallEndReason.Missed -> SystemEndCause.Missed
                CallEndReason.Declined -> SystemEndCause.Rejected
                CallEndReason.Disconnected, is CallEndReason.Failed -> SystemEndCause.Error
            }

        private companion object {
            /** How long an ended call stays on screen with why it ended. */
            val ENDED_SHOWN = 2.seconds

            const val EARLY_SIGNALING_MAX = 64
        }
    }

private fun CallState.isRinging() = this is CallState.Pending
