package dev.kyralabs.pingogram.feature.calls.engine

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The demo's [CallEngine] (`docs/adr/0034-demo-mode-for-store-review.md`): the call screens, Telecom and the service
 * run as in a real call, but nothing connects anywhere and the engine's library never loads. A call "connects" a
 * moment after TDLib's demo says it is ready; the camera, when the user turns it on, shows its real picture in the
 * self-view ([DemoCamera]) and goes nowhere. The microphone is not opened: there is nobody to send it to. The other
 * side sends no video, so their avatar shows.
 */
@Singleton
internal class DemoCallEngine
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CallEngine {
        override suspend fun protocol(): CallProtocol =
            CallProtocol(
                udpP2p = false,
                udpReflector = true,
                minLayer = PROTOCOL_LAYER,
                maxLayer = PROTOCOL_LAYER,
                libraryVersions = persistentListOf(DEMO_VERSION),
            )

        override suspend fun cameras(): List<CallCamera> =
            withContext(Dispatchers.IO) {
                val manager = context.getSystemService(CameraManager::class.java) ?: return@withContext emptyList()
                runCatching {
                    manager.cameraIdList
                        .map { id ->
                            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                            CallCamera(id, facing == CameraCharacteristics.LENS_FACING_FRONT, metadata = id)
                        }.sortedByDescending { it.isFront }
                }.getOrDefault(emptyList())
            }

        override suspend fun connect(
            callId: Int,
            isOutgoing: Boolean,
            connection: CallConnection,
        ): CallSession = DemoCallSession(DemoCamera(context))

        override suspend fun createGroupCall(
            groupCallId: Int,
            muted: Boolean,
        ): GroupCallSession = DemoGroupCallSession(DemoCamera(context))

        override suspend fun createConference(
            muted: Boolean,
            encryption: ConferenceEncryption,
        ): GroupCallSession = DemoGroupCallSession(DemoCamera(context))

        private companion object {
            const val PROTOCOL_LAYER = 92
            const val DEMO_VERSION = "demo"
        }
    }

/** How long a demo call takes to "connect": long enough to show "Connecting…". */
private const val CONNECT_AFTER_MILLIS = 700L

/** A one-to-one demo call: connected after a moment, the camera's picture in the self-view alone. */
private class DemoCallSession(
    private val camera: DemoCamera,
) : CallSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(CallMediaState.Connecting)
    override val state: StateFlow<CallMediaState> = mutableState.asStateFlow()
    override val signalingData: Flow<ByteArray> = emptyFlow()
    override val screenEnded: Flow<Unit> = emptyFlow()
    override val remoteVideo: StateFlow<RemoteVideo> = MutableStateFlow(RemoteVideo.Off)
    override val remoteIsScreen: StateFlow<Boolean> = MutableStateFlow(false)

    init {
        scope.launch {
            delay(CONNECT_AFTER_MILLIS)
            mutableState.compareAndSet(CallMediaState.Connecting, CallMediaState.Connected)
        }
    }

    override suspend fun receiveSignalingData(data: ByteArray) = Unit

    override suspend fun setMuted(muted: Boolean) = Unit

    override suspend fun setOnHold(onHold: Boolean) = Unit

    override suspend fun setCamera(
        camera: CallCamera?,
        quality: VideoQuality,
    ) = this.camera.show(camera, quality)

    // The screen's capture is the user's consent alone in the demo: nothing is captured or sent.
    override suspend fun setScreen(
        on: Boolean,
        quality: VideoQuality,
    ) = Unit

    override suspend fun setVideoSinks(
        local: VideoFrameSink?,
        remote: VideoFrameSink?,
    ) {
        camera.sink = local
    }

    override suspend fun close() {
        camera.show(null, VideoQuality.High)
        mutableState.value = CallMediaState.Closed
        scope.cancel()
    }
}

/** A demo voice chat: connected once joined, the others' voices and videos made up by TDLib's demo. */
@Suppress("TooManyFunctions") // GroupCallSession's, each doing nothing or little in the demo.
private class DemoGroupCallSession(
    private val camera: DemoCamera,
) : GroupCallSession {
    override val joinPayload: String = "{}"
    override val audioSourceId: Int = 1
    private val mutableState = MutableStateFlow(CallMediaState.Connecting)
    override val state: StateFlow<CallMediaState> = mutableState.asStateFlow()
    private val mutableMode = MutableStateFlow<GroupCallMode?>(null)
    override val mode: StateFlow<GroupCallMode?> = mutableMode.asStateFlow()
    override val streamRequests: Flow<StreamRequest> = emptyFlow()
    override val screenEnded: Flow<Unit> = emptyFlow()
    override val unknownSources: Flow<List<Int>> = emptyFlow()

    override suspend fun connect(params: String) {
        mutableMode.value = GroupCallMode.Rtc
        mutableState.value = CallMediaState.Connected
    }

    override suspend fun setMuted(muted: Boolean) = Unit

    override suspend fun setOnHold(onHold: Boolean) = Unit

    override suspend fun sendTime(timeMs: Long) = Unit

    override suspend fun sendSegment(
        request: StreamRequest.Segment,
        result: SegmentResult,
    ) = Unit

    override suspend fun setVideoSink(sink: VideoFrameSink?) = Unit

    override suspend fun setCamera(
        camera: CallCamera?,
        quality: VideoQuality,
    ) = this.camera.show(camera, quality)

    override suspend fun setSelfVideoSink(sink: VideoFrameSink?) {
        camera.sink = sink
    }

    override suspend fun receiveVideos(videos: List<IncomingVideo>) = Unit

    override fun setIncomingVideoSinks(sinks: Map<String, VideoFrameSink>) = Unit

    override suspend fun startPresentation(): Presentation = Presentation(payload = "{}", audioSourceId = 2)

    override suspend fun connectPresentation(
        params: String,
        quality: VideoQuality,
    ) = Unit

    override suspend fun stopPresentation() = Unit

    override suspend fun mapSources(sources: Map<Int, Long>) = Unit

    override suspend fun close() {
        camera.show(null, VideoQuality.High)
        mutableState.value = CallMediaState.Closed
    }
}
