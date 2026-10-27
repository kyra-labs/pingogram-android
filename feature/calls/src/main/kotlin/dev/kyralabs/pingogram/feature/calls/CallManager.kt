package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallCamera
import dev.kyralabs.pingogram.feature.calls.engine.CallEngine
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.CallSession
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import dev.kyralabs.pingogram.feature.calls.engine.VideoQuality
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
import kotlinx.coroutines.flow.getAndUpdate
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
 * One call at a time: an incoming call while the user is in another, or in a voice chat ([CallLine]), is declined, as
 * Telegram's apps do.
 *
 * Every change happens under [mutex], in the order TDLib's updates and the user's actions arrive. The engine's data
 * from the other side that comes before the engine runs (it may: TDLib passes it on as soon as the other side sends
 * it) is kept per call until the engine connects.
 *
 * Video (`docs/adr/0019-video-and-group-calls-scope.md`): the camera, which camera, and where the frames go are kept
 * per call and given to the engine once it connects; the camera sends only from then on. The most it sends follows
 * the network ([VideoQuality]): 720p on Wi-Fi, 480p on mobile data or once the connection was lost in the call, 360p
 * when roaming or with Data Saver; WebRTC lowers it further by itself when the connection cannot carry it.
 */
@Singleton
@Suppress("TooManyFunctions") // One function per action of the user and per transition of the state machine.
class CallManager
    @Inject
    // Injected: TDLib's calls, engine, Telecom, service, tones, clock, names, network, the line voice chats share.
    @Suppress("LongParameterList")
    internal constructor(
        // Every account's calls (`docs/adr/0031-multiple-accounts.md`), with ids that carry the account (CallIds).
        @param:AllAccounts private val callRepository: CallRepository,
        private val engine: CallEngine,
        private val system: CallSystem,
        private val foreground: CallForeground,
        private val tones: CallTones,
        private val clock: CallClock,
        private val names: CallerNames,
        // Lazy: the repository asks Android for the network when it is made, which the first call does off the main
        // thread, not the activities that inject this at their start (`docs/PERFORMANCE.md` §2).
        @param:CallNetwork private val network: dagger.Lazy<NetworkRepository>,
        private val line: CallLine,
        // The conference calls a call becomes (docs/adr/0025-conference-calls.md); lazy, as few calls do.
        private val groupCalls: dagger.Lazy<GroupCallManager>,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val mutableCall = MutableStateFlow<ActiveCall?>(null)

        /** The call the user is in, then every change; null without one. An ended call stays for [ENDED_SHOWN]. */
        val call: StateFlow<ActiveCall?> = mutableCall.asStateFlow()

        private val mutableRating = MutableStateFlow<CallRatingRequest?>(null)

        /**
         * The call that just ended and that Telegram asks to have rated, until the user rates it ([rate]), declines
         * ([dismissRating]) or another call starts; null otherwise. The call screen shows "Rate this call" for it.
         */
        val rating: StateFlow<CallRatingRequest?> = mutableRating.asStateFlow()

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
         * Rates the call of [rating] with [stars] (1 to 5) and what went wrong ([problems]), in the app's scope, as
         * the screen closes; a refusal is not shown, as the user is done with the call.
         */
        fun rate(
            stars: Int,
            problems: Set<CallProblem>,
        ) {
            val request = mutableRating.getAndUpdate { null } ?: return
            scope.launch { callRepository.rateCall(request.callId, stars, problems) }
        }

        /** The user does not rate the call of [rating]. */
        fun dismissRating() {
            mutableRating.value = null
        }

        /**
         * Calls user [userId], [withVideo] with the front camera on. Does nothing while the user is in another call or
         * a voice chat: the screen shows that one. A call that cannot be made ends at once with the reason
         * ([CallEndReason.Failed]).
         */
        suspend fun startCall(
            userId: Long,
            withVideo: Boolean = false,
        ) {
            val call =
                mutex.withLock {
                    if (live != null || line.isBusy) return
                    Live(userId, isOutgoing = true, scope = callScope()).also {
                        it.wantsCamera = withVideo
                        start(it)
                    }
                }
            if (withVideo) loadCameras(call)
            val protocol = protocolOrNull()
            val result = protocol?.let { callRepository.startCall(userId, isVideo = withVideo, protocol = it) }
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

        /** Answers the incoming call that rings, [withVideo] with the front camera on. */
        @Suppress("ReturnCount") // Guards under the lock: no call, no id yet, or nothing to answer.
        suspend fun answer(withVideo: Boolean = false) {
            val call =
                mutex.withLock {
                    val call = live ?: return
                    val callId = call.callId ?: return
                    if (call.isOutgoing || call.answered) return
                    call.answered = true
                    call.wantsCamera = withVideo
                    call.phase = CallPhase.Connecting
                    foreground.start(withMicrophone = true)
                    call.system?.answer()
                    publish()
                    call to callId
                }
            if (withVideo) loadCameras(call.first)
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

        /**
         * Turns the connected call into a conference call with [userIds] invited (`docs/adr/0025-conference-calls.md`),
         * as Telegram's apps do: the call's media end, a new conference call starts ([GroupCallManager.startConference])
         * on the same line with the microphone as it was, the call is discarded with the conference call's link (the
         * other person's app joins it), then [userIds] get invitations. If the conference call cannot be made, the call
         * ends with the reason.
         */
        @Suppress("ReturnCount") // Guards under the lock: no call, not connected, not supported, already moving.
        suspend fun upgradeToConference(userIds: List<Long>) {
            val call =
                mutex.withLock {
                    val call = live ?: return
                    if (call.phase != CallPhase.Connected || !call.groupCallSupported || call.discarding) return
                    call.discarding = true
                    finish(call, CallEndReason.UpgradedToGroupCall, keepLine = true)
                    call
                }
            val callId = call.callId ?: return
            val duration = call.connectedAt?.let { (clock.elapsedRealtimeMillis() - it).milliseconds } ?: Duration.ZERO
            val conferences = groupCalls.get()
            when (val started = conferences.startConference(from = this, muted = call.muted)) {
                is Outcome.Success -> {
                    callRepository.discardCall(callId, false, duration, call.hadVideo, 0, inviteLink = started.value)
                    if (call.wantsCamera) conferences.setCameraOn(true)
                    conferences.inviteAfterUpgrade(userIds)
                }

                is Outcome.Failure -> {
                    line.release(this)
                    callRepository.discardCall(callId, false, duration, call.hadVideo, 0)
                    mutex.withLock {
                        if (live === call) {
                            call.phase = CallPhase.Ended(CallEndReason.Failed(started.error))
                            publish()
                        }
                    }
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

        /** Turns the camera on (the one last used, the front one at first) or off. On, it stops sharing the screen. */
        suspend fun setCameraOn(on: Boolean) {
            val call = mutex.withLock { live?.takeIf { it.phase !is CallPhase.Ended } } ?: return
            if (on) loadCameras(call)
            mutex.withLock {
                if (live !== call || call.wantsCamera == on) return
                call.wantsCamera = on
                if (on && call.wantsScreen) applyScreen(call, on = false)
                applyCamera(call)
            }
        }

        /**
         * Shares the screen in place of the camera (`docs/adr/0026-screen-sharing.md`): the user agreed in Android's
         * dialog, whose result already reached the engine. The call service runs as `mediaProjection` first, as
         * Android asks; where it cannot, nothing is shared.
         */
        suspend fun startScreenSharing() {
            val call = mutex.withLock { live?.takeIf { it.phase !is CallPhase.Ended && !it.wantsScreen } } ?: return
            val running = foreground.setScreenCapture(true)
            mutex.withLock {
                if (running && live === call && call.phase !is CallPhase.Ended) {
                    call.wantsCamera = false
                    applyCamera(call)
                    applyScreen(call, on = true)
                } else if (running) {
                    scope.launch { foreground.setScreenCapture(false) }
                }
            }
        }

        /** Stops sharing the screen: the user's Stop, the notification's, or the capture ending by itself. */
        suspend fun stopScreenSharing() {
            mutex.withLock {
                val call = live ?: return
                if (call.wantsScreen) applyScreen(call, on = false)
            }
        }

        /** Sends the other camera: the back one after the front one, and back. */
        suspend fun switchCamera() {
            mutex.withLock {
                val call = live ?: return
                val next = call.cameras.firstOrNull { it.isFront != call.facingFront } ?: return
                call.facingFront = next.isFront
                applyCamera(call)
            }
        }

        /**
         * Where the call's frames go ([VideoFrameSink]: the self-view and the other side's video), or nowhere with
         * null: the call screen sets them while it shows. Without a remote sink the other side's video is not received.
         * Not suspending, so a screen that goes away can take them back at once.
         */
        fun setVideoSinks(
            local: VideoFrameSink?,
            remote: VideoFrameSink?,
        ) {
            live?.sinks?.value = VideoSinks(local, remote)
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
            if (live == null && !line.isBusy) {
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
            incoming.videoOffered = call.isVideo
            start(incoming)
        }

        /** Makes [call] the user's call and tells Android about it. */
        private fun start(call: Live) {
            clearEnded?.cancel()
            mutableRating.value = null
            line.take(this)
            live = call
            publish()
            foreground.start(withMicrophone = call.isOutgoing)
            call.scope.launch {
                val request =
                    SystemCallRequest.user(
                        names.name(call.callId?.let(CallIds::slotOf), call.userId),
                        call.userId,
                        call.isOutgoing,
                    )
                mutex.withLock {
                    if (live !== call || call.phase is CallPhase.Ended) return@launch
                    val system = system.add(call.scope, request, listener(call))
                    call.system = system
                    if (call.phase is CallPhase.Connected) system.setActive()
                    call.scope.launch { system.audio.collect { audio -> mutex.withLock { updateAudio(call, audio) } } }
                    call.scope.launch {
                        network.get().networkStatus.collect { status -> mutex.withLock { onNetwork(call, status) } }
                    }
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
            if (update.isVideo) {
                call.videoOffered = true
                call.hadVideo = true
            }
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
                    call.groupCallSupported = state.connection.isGroupCallSupported
                    if (call.session == null && !call.connecting) connect(call, update.id, state.connection)
                }

                CallState.HangingUp -> {
                    tones.stop()
                }

                is CallState.Discarded -> {
                    val link = state.upgradeLink
                    if (link != null && call.connectedAt != null && !call.discarding) {
                        followUpgrade(call, link)
                    } else {
                        finish(call, state.reason.toEndReason(call), askRating = state.needsRating)
                    }
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
                // One collector each, so the engine gets the changes in order and the latest wins.
                call.scope.launch { call.sinks.collect { session.setVideoSinks(it.local, it.remote) } }
                call.scope.launch {
                    call.camera.collect { source ->
                        session.setCamera(source?.camera, source?.quality ?: VideoQuality.High)
                    }
                }
                call.scope.launch {
                    session.remoteVideo.collect { video -> mutex.withLock { onRemoteVideo(call, video) } }
                }
                call.scope.launch {
                    call.screen.collect { on -> session.setScreen(on, call.videoQuality()) }
                }
                call.scope.launch {
                    session.remoteIsScreen.collect { isScreen ->
                        mutex.withLock {
                            call.remoteIsScreen = isScreen
                            if (live === call) publish()
                        }
                    }
                }
                call.scope.launch { session.screenEnded.collect { stopScreenSharing() } }
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
                    if (call.connectedAt != null && !call.reconnected) {
                        // Lost once: the rest of the call sends less video, which a weak connection keeps up with.
                        call.reconnected = true
                        applyCamera(call)
                    }
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

        private fun onRemoteVideo(
            call: Live,
            video: RemoteVideo,
        ) {
            if (live !== call || call.remoteVideo == video) return
            call.remoteVideo = video
            if (video != RemoteVideo.Off) {
                call.hadVideo = true
                call.videoOffered = true
            }
            publish()
        }

        /** The camera [call] should send now and how much, which the engine gets once it connects: none while held. */
        private fun applyCamera(call: Live) {
            if (call.wantsCamera) call.hadVideo = true
            call.camera.value = call.sendingCamera()?.let { CameraSource(it, call.videoQuality()) }
            publish()
        }

        /** The network the call goes over; without one the last one's quality stays, as the call reconnects. */
        private fun onNetwork(
            call: Live,
            status: NetworkStatus,
        ) {
            if (live !== call || status.type == NetworkType.None || call.network == status) return
            call.network = status
            applyCamera(call)
        }

        /** Asks the engine for the device's cameras once per call. */
        private suspend fun loadCameras(call: Live) {
            if (call.camerasLoaded) return
            val cameras = camerasOrEmpty()
            mutex.withLock {
                call.cameras = cameras
                call.camerasLoaded = true
                if (cameras.none { it.isFront }) call.facingFront = false
                if (live === call) applyCamera(call)
            }
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
            applyCamera(call)
            call.screen.value = call.sendsScreen()
        }

        /** Shares the screen, or stops: the engine gets it once it connects, and the service drops its type after. */
        private fun applyScreen(
            call: Live,
            on: Boolean,
        ) {
            call.wantsScreen = on
            if (on) call.hadVideo = true
            call.screen.value = call.sendsScreen()
            if (!on) scope.launch { foreground.setScreenCapture(false) }
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
                        isVideo = call.hadVideo,
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

        /**
         * The other person turned the call into a conference call: this side joins it by [link] on the same line, with
         * the microphone and the camera as they were, and the voice chat's screen takes over.
         */
        private fun followUpgrade(
            call: Live,
            link: String,
        ) {
            finish(call, CallEndReason.UpgradedToGroupCall, keepLine = true)
            scope.launch {
                val conferences = groupCalls.get()
                val joined =
                    conferences.joinConference(
                        ConferenceTarget.Link(link),
                        muted = call.muted,
                        from = this@CallManager,
                    )
                if (joined == JoinResult.Joining) {
                    if (call.wantsCamera) conferences.setCameraOn(true)
                } else {
                    line.release(this@CallManager)
                }
            }
        }

        /** Ends [call] here; [keepLine] for a call that becomes a conference call, which takes the line over. */
        private fun finish(
            call: Live,
            reason: CallEndReason,
            askRating: Boolean = false,
            keepLine: Boolean = false,
        ) {
            if (call.phase is CallPhase.Ended) return
            call.phase = CallPhase.Ended(reason)
            if (askRating && reason == CallEndReason.HungUp) askForRating(call)
            call.callId?.let {
                seen += it
                earlySignaling.remove(it)
            }
            if (reason == CallEndReason.Declined && call.isOutgoing) tones.busy() else tones.stop()
            call.system?.end(reason.toSystemCause(call))
            if (!keepLine) line.release(this)
            val session = call.session
            scope.launch {
                session?.close()
                call.scope.cancel()
            }
            // Stopping the service drops its types, the screen's included.
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

        /** Telegram asks for a rating: only for a call that was connected long enough to judge (as Telegram's apps). */
        private fun askForRating(call: Live) {
            val callId = call.callId
            val connectedAt = call.connectedAt
            if (callId != null &&
                connectedAt != null &&
                (clock.elapsedRealtimeMillis() - connectedAt).milliseconds >= RATING_MIN_CONNECTED
            ) {
                mutableRating.value = CallRatingRequest(callId, call.userId, isVideo = call.hadVideo)
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

        /** The device's cameras; none if the engine could not list them, and the call goes on without video. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // A failure only leaves the call without a camera.
        private suspend fun camerasOrEmpty(): List<CallCamera> =
            try {
                engine.cameras()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
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

            /** Both sides can turn the call into a conference call (TDLib's `is_group_call_supported`). */
            var groupCallSupported = false
            var ringbackStarted = false
            var videoOffered = false
            var system: SystemCall? = null
            var session: CallSession? = null

            /** The user wants the camera on (it sends once the engine connects, and not while held). */
            var wantsCamera = false
            var facingFront = true
            var cameras: List<CallCamera> = emptyList()
            var camerasLoaded = false
            var remoteVideo = RemoteVideo.Off
            var remoteIsScreen = false

            /** The user shares the screen (it sends once the engine connects, and not while held). */
            var wantsScreen = false
            val screen = MutableStateFlow(false)

            /** Either side sent video, or the call was made as a video call: TDLib's `is_video` when it ends. */
            var hadVideo = false
            val camera = MutableStateFlow<CameraSource?>(null)
            var network: NetworkStatus? = null
            var reconnected = false
            val sinks = MutableStateFlow(VideoSinks())

            fun videoQuality(): VideoQuality = VideoQuality.forNetwork(network, reconnected)

            fun sendsScreen(): Boolean = wantsScreen && !onHold

            fun sendingCamera(): CallCamera? =
                if (!wantsCamera || onHold) {
                    null
                } else {
                    cameras.firstOrNull { it.isFront == facingFront } ?: cameras.firstOrNull()
                }

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
                    videoOffered = videoOffered,
                    canUpgrade = groupCallSupported && phase == CallPhase.Connected && !discarding,
                    video =
                        CallVideo(
                            camera =
                                sendingCamera()?.let { if (it.isFront) CameraFacing.Front else CameraFacing.Back }
                                    ?: if (wantsCamera && !camerasLoaded) CameraFacing.Front else null,
                            canSwitchCamera = cameras.any { it.isFront } && cameras.any { !it.isFront },
                            remote = remoteVideo,
                            isSharingScreen = wantsScreen,
                            remoteIsScreen = remoteIsScreen,
                        ),
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
                CallEndReason.UpgradedToGroupCall -> SystemEndCause.Local
            }

        private companion object {
            /** How long an ended call stays on screen with why it ended. */
            val ENDED_SHOWN = 2.seconds

            /** A call connected for less is not worth rating, even when Telegram asks. */
            val RATING_MIN_CONNECTED = 5.seconds

            const val EARLY_SIGNALING_MAX = 64
        }
    }

private fun CallState.isRinging() = this is CallState.Pending

private data class VideoSinks(
    val local: VideoFrameSink? = null,
    val remote: VideoFrameSink? = null,
)

/** The camera a call (or a voice chat) sends and the most it sends. */
internal data class CameraSource(
    val camera: CallCamera,
    val quality: VideoQuality,
)
