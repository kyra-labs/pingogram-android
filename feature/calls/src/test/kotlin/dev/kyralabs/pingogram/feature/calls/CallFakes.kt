package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
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
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

val FRONT_CAMERA = CallCamera(id = "1", isFront = true, metadata = "{\"id\":\"1\",\"is_front\":true}")
val BACK_CAMERA = CallCamera(id = "0", isFront = false, metadata = "{\"id\":\"0\",\"is_front\":false}")

val ENGINE_PROTOCOL = CallProtocol(true, true, 65, 92, persistentListOf("13.0.0", "12.0.0"))

/** TDLib's calls as a test scripts them: [calls] holds what `updateCall` announced. */
class FakeCallRepository : CallRepository {
    val callsFlow = MutableStateFlow<ImmutableMap<Int, Call>>(persistentMapOf())
    val signaling = MutableSharedFlow<CallSignalingData>(extraBufferCapacity = 16)

    var startResult: Outcome<Int> = Outcome.Success(STARTED_CALL_ID)

    /** When set, createCall answers only once it completes: Telegram is slow to create the call. */
    var startGate: CompletableDeferred<Unit>? = null
    var acceptResult: Outcome<Unit> = Outcome.Success(Unit)
    var discardResult: Outcome<Unit> = Outcome.Success(Unit)

    val started = mutableListOf<Pair<Long, CallProtocol>>()
    val accepted = mutableListOf<Pair<Int, CallProtocol>>()
    val discarded = mutableListOf<Discard>()

    /** `is_video` of each createCall and discardCall, in order. */
    val startedVideo = mutableListOf<Boolean>()
    val discardedVideo = mutableListOf<Boolean>()
    val sent = mutableListOf<Pair<Int, List<Byte>>>()

    data class Discard(
        val callId: Int,
        val isDisconnected: Boolean,
        val duration: Duration,
    )

    /** What TDLib announces for [call]: its new state. */
    fun update(call: Call) {
        callsFlow.value = (callsFlow.value + (call.id to call)).toImmutable()
    }

    override val calls: Flow<ImmutableMap<Int, Call>> = callsFlow

    override fun call(id: Int): Flow<Call?> = callsFlow.map { it[id] }

    override val signalingData: Flow<CallSignalingData> = signaling

    override suspend fun startCall(
        userId: Long,
        isVideo: Boolean,
        protocol: CallProtocol,
    ): Outcome<Int> {
        started += userId to protocol
        startedVideo += isVideo
        startGate?.await()
        return startResult
    }

    override suspend fun acceptCall(
        callId: Int,
        protocol: CallProtocol,
    ): Outcome<Unit> {
        accepted += callId to protocol
        return acceptResult
    }

    override suspend fun discardCall(
        callId: Int,
        isDisconnected: Boolean,
        duration: Duration,
        isVideo: Boolean,
        connectionId: Long,
    ): Outcome<Unit> {
        discarded += Discard(callId, isDisconnected, duration)
        discardedVideo += isVideo
        return discardResult
    }

    override suspend fun sendSignalingData(
        callId: Int,
        data: ByteArray,
    ): Outcome<Unit> {
        sent += callId to data.toList()
        return Outcome.Success(Unit)
    }

    companion object {
        const val STARTED_CALL_ID = 7
    }
}

private fun Map<Int, Call>.toImmutable(): ImmutableMap<Int, Call> = persistentMapOf<Int, Call>().putAll(this)

class FakeCallEngine : CallEngine {
    var failsToLoad = false
    val sessions = mutableListOf<FakeCallSession>()
    var cameras = listOf(FRONT_CAMERA, BACK_CAMERA)

    override suspend fun protocol(): CallProtocol {
        if (failsToLoad) throw UnsatisfiedLinkError("no libntgcalls")
        return ENGINE_PROTOCOL
    }

    override suspend fun cameras(): List<CallCamera> {
        if (failsToLoad) throw UnsatisfiedLinkError("no libntgcalls")
        return cameras
    }

    override suspend fun connect(
        callId: Int,
        isOutgoing: Boolean,
        connection: CallConnection,
    ): CallSession = FakeCallSession(callId, isOutgoing, connection).also { sessions += it }
}

class FakeCallSession(
    val callId: Int,
    val isOutgoing: Boolean,
    val connection: CallConnection,
) : CallSession {
    override val state = MutableStateFlow(CallMediaState.Connecting)
    private val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
    override val signalingData: Flow<ByteArray> = outgoing.receiveAsFlow()

    val received = mutableListOf<List<Byte>>()
    var muted = false
    var onHold = false
    var closed = false

    /** Every camera the session was told to send, in order (null: none). */
    val cameras = mutableListOf<CallCamera?>()

    /** The quality each camera above was sent at. */
    val qualities = mutableListOf<VideoQuality>()
    var localSink: VideoFrameSink? = null
    var remoteSink: VideoFrameSink? = null
    override val remoteVideo = MutableStateFlow(RemoteVideo.Off)

    override suspend fun setCamera(
        camera: CallCamera?,
        quality: VideoQuality,
    ) {
        cameras += camera
        qualities += quality
    }

    override suspend fun setVideoSinks(
        local: VideoFrameSink?,
        remote: VideoFrameSink?,
    ) {
        localSink = local
        remoteSink = remote
    }

    fun send(data: ByteArray) {
        outgoing.trySend(data)
    }

    override suspend fun receiveSignalingData(data: ByteArray) {
        received += data.toList()
    }

    override suspend fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    override suspend fun setOnHold(onHold: Boolean) {
        this.onHold = onHold
    }

    override suspend fun close() {
        closed = true
        state.value = CallMediaState.Closed
    }
}

class FakeCallSystem : CallSystem {
    val calls = mutableListOf<FakeSystemCall>()

    override fun add(
        scope: CoroutineScope,
        request: SystemCallRequest,
        listener: SystemCallListener,
    ): SystemCall = FakeSystemCall(request, listener).also { calls += it }
}

class FakeSystemCall(
    val request: SystemCallRequest,
    val listener: SystemCallListener,
) : SystemCall {
    override val audio = MutableStateFlow(CallAudio())
    override val isMuted = MutableStateFlow(false)
    var answered = false
    var active = false
    var routes = mutableListOf<AudioRoute>()
    var ended: SystemEndCause? = null

    override fun answer() {
        answered = true
    }

    override fun setActive() {
        active = true
    }

    override fun setInactive() {
        active = false
    }

    override fun selectRoute(route: AudioRoute) {
        routes += route
    }

    override fun end(cause: SystemEndCause) {
        ended = ended ?: cause
    }
}

class FakeCallForeground : CallForeground {
    /** Each start, with whether it asked for the microphone; then "stop". */
    val events = mutableListOf<String>()

    override fun start(withMicrophone: Boolean) {
        events += if (withMicrophone) "start+mic" else "start"
    }

    override fun stop() {
        events += "stop"
    }
}

class FakeCallTones : CallTones {
    val played = mutableListOf<String>()

    override fun ringback() {
        played += "ringback"
    }

    override fun busy() {
        played += "busy"
    }

    override fun stop() {
        played += "stop"
    }
}

class FakeCallClock(
    var now: Long = 1_000_000,
) : CallClock {
    override fun elapsedRealtimeMillis(): Long = now
}

/** Starts [block] right here until it first suspends, as a call from the screen would. */
fun CoroutineScope.launchNow(block: suspend CoroutineScope.() -> Unit) =
    launch(start = CoroutineStart.UNDISPATCHED, block = block)

/** The network a call goes over: Wi-Fi without Data Saver until a test changes [networkStatus]. */
class FakeNetworkRepository : NetworkRepository {
    override val networkStatus =
        MutableStateFlow(NetworkStatus(NetworkType.WiFi, isMetered = false, isDataSaverOn = false))
}
