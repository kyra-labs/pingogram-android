// NTgCalls reports every failure as one of many unrelated RuntimeExceptions (ConnectionException, MediaException,
// RTCException, SignalingException, …), which have no common base of their own.
@file:Suppress("TooGenericExceptionCaught")

package dev.kyralabs.pingogram.feature.calls.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import io.github.pytgcalls.ConnectionMode
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamDevice
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.models.Frame
import io.github.pytgcalls.models.MediaSegmentPartStatus
import io.github.pytgcalls.models.SsrcGroup
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.ContextUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CallEngine] on NTgCalls (`docs/adr/0015-call-media-engine.md`), one instance per process, created with the first
 * call: loading its library (about 21 MB) is not something to do at app start.
 *
 * NTgCalls' Java API blocks until its own worker threads are done, so every call into it runs on one thread of its
 * own ([engineThread]), never on the main thread. Its callbacks come on its threads and are keyed by the id each
 * connection was created with: the TDLib call id here.
 *
 * Audio: NTgCalls opens Oboe streams for voice communication (usage `VoiceCommunication`, input preset
 * `VoiceCommunication`), so Android's echo canceller and noise suppressor work on them, and Telecom's routing
 * (earpiece, speaker, Bluetooth, wired headset) applies.
 *
 * Video (`docs/adr/0019-video-and-group-calls-scope.md`): NTgCalls opens the camera itself (Camera2 through WebRTC), at
 * the size of the [VideoQuality] asked for, landscape as cameras capture (each frame carries its rotation), and
 * hands every frame, its own camera's and the other side's, to `onFrames` as a packed I420 array, which goes to the
 * session's [VideoFrameSink]s as it is. The other side's video comes at the size it is sent (at most 720p from
 * Telegram's apps): NTgCalls would scale it to a fixed size whatever its shape, so the screen scales it instead.
 */
@Singleton
class NtgCallsEngine
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CallEngine {
        private val engineThread: ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { Thread(it, "ntgcalls") }.asCoroutineDispatcher()

        private val sessions = ConcurrentHashMap<Long, NtgCallsSession>()

        /** Voice chats by their NTgCalls id ([groupKey]), apart from [sessions]: TDLib's two kinds of ids may meet. */
        private val groupSessions = ConcurrentHashMap<Long, NtgGroupCallSession>()

        private var instance: NTgCalls? = null

        /** NTgCalls, created on [engineThread] with its callbacks the first time a call needs it. */
        private fun calls(): NTgCalls =
            instance ?: run {
                // WebRTC's Java side (audio, the application context of NTgCalls' helpers) needs the context first.
                ContextUtils.initialize(context.applicationContext)
                NTgCalls().also { engine ->
                    engine.onSignalingData { id, data -> sessions[id]?.emitSignaling(data) }
                    engine.onConnectionChange { id, info ->
                        sessions[id]?.onState(info.state.toMediaState())
                        groupSessions[id]?.onConnection(info.state.toMediaState())
                    }
                    engine.onRemoteSourceChange { id, source ->
                        if (source.device == StreamDevice.CAMERA) {
                            sessions[id]?.onRemoteVideo(source.state.toRemoteVideo())
                        }
                    }
                    engine.onFrames { id, mode, device, frames ->
                        if (device == StreamDevice.CAMERA) {
                            CallTrace.section(CallTrace.FRAMES) {
                                sessions[id]?.onFrames(mode, frames)
                                groupSessions[id]?.onFrames(mode, frames)
                            }
                        }
                    }
                    engine.onRequestBroadcastTimestamp { id -> groupSessions[id]?.request(StreamRequest.Time) }
                    engine.onRequestBroadcastPart { id, part -> groupSessions[id]?.request(part.toStreamRequest()) }
                    instance = engine
                }
            }

        override suspend fun protocol(): CallProtocol =
            withContext(engineThread) { NTgCalls.getProtocol().toCallProtocol() }

        override suspend fun cameras(): List<CallCamera> =
            withContext(engineThread) {
                calls()
                NTgCalls
                    .getMediaDevices()
                    .camera
                    .orEmpty()
                    .mapNotNull { it.metadata?.toCamera() }
                    .sortedByDescending { it.isFront }
            }

        /** NTgCalls' camera metadata: `{"id": "1", "is_front": true}`. */
        private fun String.toCamera(): CallCamera? =
            try {
                val json = JSONObject(this)
                CallCamera(id = json.getString("id"), isFront = json.optBoolean("is_front"), metadata = this)
            } catch (e: JSONException) {
                Log.w(TAG, "Unreadable camera: ${e.javaClass.simpleName}")
                null
            }

        override suspend fun connect(
            callId: Int,
            isOutgoing: Boolean,
            connection: CallConnection,
        ): CallSession =
            withContext(engineThread) {
                val id = callId.toLong()
                val engine = calls()
                val session = NtgCallsSession(id)
                sessions[id] = session
                try {
                    engine.createP2pCall(id)
                    engine.setStreamSources(id, StreamMode.CAPTURE, session.capture())
                    engine.setStreamSources(id, StreamMode.PLAYBACK, session.playback())
                    engine.skipExchange(id, connection.encryptionKey(), isOutgoing)
                    engine.connectP2p(
                        id,
                        connection.servers.map { it.toRtcServer() },
                        connection.protocol.libraryVersions,
                        connection.allowP2p,
                        connection.customParameters.takeIf { it.isNotBlank() },
                    )
                } catch (e: RuntimeException) {
                    // NTgCalls reports every failure as an exception (no matching version, no audio device, …).
                    Log.w(TAG, "Could not connect the call: ${e.javaClass.simpleName}")
                    session.onState(CallMediaState.Failed)
                }
                session
            }

        override suspend fun createGroupCall(
            groupCallId: Int,
            muted: Boolean,
        ): GroupCallSession =
            withContext(engineThread) {
                val id = groupKey(groupCallId)
                val engine = calls()
                val payload = engine.createCall(id)
                val session = NtgGroupCallSession(id, payload)
                groupSessions[id] = session
                try {
                    engine.setStreamSources(id, StreamMode.CAPTURE, session.capture())
                    engine.setStreamSources(id, StreamMode.PLAYBACK, session.playback())
                    if (muted) engine.mute(id)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not open the voice chat's media: ${e.javaClass.simpleName}")
                    session.onConnection(CallMediaState.Failed)
                }
                session
            }

        /** The device's microphone (capture) or speaker (playback) through Oboe; Telecom picks which speaker. */
        private fun microphone(isCapture: Boolean) =
            AudioDescription(MediaSource.DEVICE, SAMPLE_RATE, CHANNELS, deviceMetadata(isCapture), true)

        private fun deviceMetadata(isCapture: Boolean): String =
            NTgCalls
                .getMediaDevices()
                .let { if (isCapture) it.microphone else it.speaker }
                .firstOrNull()
                ?.metadata
                .orEmpty()

        private fun CallCamera.toVideoSource(quality: VideoQuality) =
            VideoDescription(MediaSource.DEVICE, quality.width, quality.height, quality.fps, metadata, true)

        // CallSession's members, and what NTgCalls' callbacks hand each session.
        @Suppress("TooManyFunctions")
        private inner class NtgCallsSession(
            private val id: Long,
        ) : CallSession {
            private val mutableState = MutableStateFlow(CallMediaState.Connecting)
            override val state: StateFlow<CallMediaState> = mutableState.asStateFlow()

            // Kept until the call manager collects it: NTgCalls sends its first packets while connectP2p runs.
            // Signaling is small and rare (a few packets a second at most), and never blocks NTgCalls' threads.
            private val signaling = Channel<ByteArray>(Channel.UNLIMITED)
            override val signalingData: Flow<ByteArray> = signaling.receiveAsFlow()

            private val mutableRemoteVideo = MutableStateFlow(RemoteVideo.Off)
            override val remoteVideo: StateFlow<RemoteVideo> = mutableRemoteVideo.asStateFlow()

            @Volatile private var localSink: VideoFrameSink? = null

            @Volatile private var remoteSink: VideoFrameSink? = null

            private val microphoneSource = microphone(isCapture = true)
            private val speakerSource = microphone(isCapture = false)

            @Volatile private var camera: CallCamera? = null

            @Volatile private var quality = VideoQuality.High

            @Volatile private var muted = false

            @Volatile private var onHold = false

            fun emitSignaling(data: ByteArray) {
                signaling.trySend(data)
            }

            fun onRemoteVideo(video: RemoteVideo) {
                mutableRemoteVideo.value = video
            }

            fun onFrames(
                mode: StreamMode,
                frames: List<Frame>,
            ) {
                val sink = (if (mode == StreamMode.CAPTURE) localSink else remoteSink) ?: return
                for (frame in frames) {
                    val data = frame.frame_data ?: continue
                    sink.onFrame(frame.data, data.width, data.height, data.rotation.toDegrees())
                }
            }

            /** The microphone, and the camera while one sends. */
            fun capture() = MediaDescription(microphoneSource, null, camera?.toVideoSource(quality), null)

            /**
             * The speaker, and the other side's video while a sink takes it: NTgCalls negotiates receiving video again
             * with each change, so a call nobody watches (a voice call, the screen away) receives no frames.
             */
            fun playback() = MediaDescription(null, speakerSource, REMOTE_VIDEO.takeIf { remoteSink != null }, null)

            override suspend fun setCamera(
                camera: CallCamera?,
                quality: VideoQuality,
            ) {
                if (this.camera == camera && (camera == null || this.quality == quality)) return
                this.camera = camera
                this.quality = quality
                engineCall("set the camera") { calls().setStreamSources(id, StreamMode.CAPTURE, capture()) }
            }

            override suspend fun setVideoSinks(
                local: VideoFrameSink?,
                remote: VideoFrameSink?,
            ) {
                localSink = local
                val receives = (remoteSink != null) != (remote != null)
                remoteSink = remote
                if (receives) {
                    engineCall("receive video") { calls().setStreamSources(id, StreamMode.PLAYBACK, playback()) }
                }
            }

            fun onState(state: CallMediaState) {
                if (mutableState.value != CallMediaState.Closed) mutableState.value = state
            }

            override suspend fun receiveSignalingData(data: ByteArray) =
                engineCall("receive signaling") { calls().sendSignalingData(id, data) }

            override suspend fun setMuted(muted: Boolean) {
                this.muted = muted
                applyMicrophone()
            }

            override suspend fun setOnHold(onHold: Boolean) {
                this.onHold = onHold
                engineCall("hold") { if (onHold) calls().pause(id) else calls().resume(id) }
                applyMicrophone()
            }

            private suspend fun applyMicrophone() =
                engineCall("mute") { if (muted || onHold) calls().mute(id) else calls().unmute(id) }

            override suspend fun close() {
                if (mutableState.value == CallMediaState.Closed) return
                withContext(engineThread) {
                    try {
                        calls().stop(id)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "NTgCalls failed to stop: ${e.javaClass.simpleName}")
                    }
                }
                mutableState.value = CallMediaState.Closed
                mutableRemoteVideo.value = RemoteVideo.Off
                localSink = null
                remoteSink = null
                sessions.remove(id)
                signaling.close()
            }

            /** Runs [action] on the engine's thread; a failure is logged, as there is nothing the caller could do. */
            private suspend fun engineCall(
                what: String,
                action: () -> Unit,
            ) = withContext(engineThread) {
                if (mutableState.value == CallMediaState.Closed) return@withContext
                try {
                    action()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "NTgCalls failed to $what: ${e.javaClass.simpleName}")
                }
            }
        }

        /** A voice chat or live stream: NTgCalls' group call, keyed by [id]. */
        @Suppress("TooManyFunctions")
        private inner class NtgGroupCallSession(
            private val id: Long,
            override val joinPayload: String,
        ) : GroupCallSession {
            override val audioSourceId: Int = joinPayload.audioSource()

            private val mutableState = MutableStateFlow(CallMediaState.Connecting)
            override val state: StateFlow<CallMediaState> = mutableState.asStateFlow()

            private val mutableMode = MutableStateFlow<GroupCallMode?>(null)
            override val mode: StateFlow<GroupCallMode?> = mutableMode.asStateFlow()

            // A stream asks for a few segments a second at most; never blocks NTgCalls' threads.
            private val requests = Channel<StreamRequest>(Channel.UNLIMITED)
            override val streamRequests: Flow<StreamRequest> = requests.receiveAsFlow()

            @Volatile private var videoSink: VideoFrameSink? = null

            @Volatile private var selfSink: VideoFrameSink? = null

            private val microphoneSource = microphone(isCapture = true)
            private val speakerSource = microphone(isCapture = false)

            @Volatile private var muted = false

            @Volatile private var onHold = false

            private var camera: CallCamera? = null
            private var quality = VideoQuality.High

            /** The other participants' videos received, by endpoint, with the SSRC their frames carry. */
            private val received = mutableMapOf<String, Long>()

            /** SSRC to endpoint, read on NTgCalls' threads. */
            @Volatile private var endpoints: Map<Long, String> = emptyMap()

            @Volatile private var incomingSinks: Map<String, VideoFrameSink> = emptyMap()

            /** The microphone, and the camera while one sends (`docs/adr/0024-video-in-voice-chats.md`). */
            fun capture() = MediaDescription(microphoneSource, null, camera?.toVideoSource(quality), null)

            /** The speaker, and a stream's video while a sink takes it. */
            fun playback() = MediaDescription(null, speakerSource, REMOTE_VIDEO.takeIf { videoSink != null }, null)

            fun onConnection(state: CallMediaState) {
                if (mutableState.value != CallMediaState.Closed) mutableState.value = state
            }

            fun request(request: StreamRequest) {
                requests.trySend(request)
            }

            fun onFrames(
                mode: StreamMode,
                frames: List<Frame>,
            ) {
                if (mode == StreamMode.PLAYBACK && endpoints.isNotEmpty()) {
                    onIncomingFrames(frames)
                    return
                }
                val sink = (if (mode == StreamMode.CAPTURE) selfSink else videoSink) ?: return
                for (frame in frames) {
                    val data = frame.frame_data ?: continue
                    sink.onFrame(frame.data, data.width, data.height, data.rotation.toDegrees())
                }
            }

            override suspend fun connect(params: String) =
                withContext(engineThread) {
                    if (mutableState.value == CallMediaState.Closed) return@withContext
                    try {
                        val engine = calls()
                        engine.connect(id, params, false)
                        mutableMode.value =
                            when (engine.getConnectionMode(id)) {
                                ConnectionMode.STREAM, ConnectionMode.RTMP -> GroupCallMode.Stream
                                else -> GroupCallMode.Rtc
                            }
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Could not connect the voice chat: ${e.javaClass.simpleName}")
                        mutableState.value = CallMediaState.Failed
                    }
                }

            override suspend fun setMuted(muted: Boolean) {
                this.muted = muted
                applyMicrophone()
            }

            override suspend fun setOnHold(onHold: Boolean) {
                this.onHold = onHold
                engineCall("hold") { if (onHold) calls().pause(id) else calls().resume(id) }
                applyMicrophone()
            }

            private suspend fun applyMicrophone() =
                engineCall("mute") { if (muted || onHold) calls().mute(id) else calls().unmute(id) }

            override suspend fun sendTime(timeMs: Long) =
                engineCall("send the stream's time") { calls().sendBroadcastTimestamp(id, timeMs) }

            override suspend fun sendSegment(
                request: StreamRequest.Segment,
                result: SegmentResult,
            ) = engineCall("send a stream segment") {
                val (status, data) =
                    when (result) {
                        is SegmentResult.Data -> MediaSegmentPartStatus.SUCCESS to result.bytes
                        SegmentResult.NotReady -> MediaSegmentPartStatus.NOT_READY to null
                        SegmentResult.ResyncNeeded -> MediaSegmentPartStatus.RESYNC_NEEDED to null
                    }
                calls().sendBroadcastPart(id, request.segmentId, request.partId, status, false, data)
            }

            override suspend fun setVideoSink(sink: VideoFrameSink?) {
                val receives = (videoSink != null) != (sink != null)
                videoSink = sink
                if (receives) {
                    engineCall("receive the stream's video") {
                        calls().setStreamSources(id, StreamMode.PLAYBACK, playback())
                    }
                }
            }

            override suspend fun setCamera(
                camera: CallCamera?,
                quality: VideoQuality,
            ) {
                if (this.camera == camera && (camera == null || this.quality == quality)) return
                this.camera = camera
                this.quality = quality
                engineCall("set the camera") { calls().setStreamSources(id, StreamMode.CAPTURE, capture()) }
            }

            override suspend fun setSelfVideoSink(sink: VideoFrameSink?) {
                selfSink = sink
            }

            /** The other participants' frames, each to its endpoint's sink; a stream's frames to [videoSink]. */
            private fun onIncomingFrames(frames: List<Frame>) {
                val sinks = incomingSinks
                for (frame in frames) {
                    val data = frame.frame_data
                    val sink = endpoints[frame.ssrc]?.let(sinks::get) ?: videoSink
                    if (data != null && sink != null) {
                        sink.onFrame(frame.data, data.width, data.height, data.rotation.toDegrees())
                    }
                }
            }

            override suspend fun receiveVideos(videos: List<IncomingVideo>) =
                engineCall("receive the participants' videos") {
                    val wanted = videos.associateBy { it.endpointId }
                    for (endpoint in received.keys - wanted.keys) {
                        calls().removeIncomingVideo(id, endpoint)
                        received.remove(endpoint)
                    }
                    for ((endpoint, video) in wanted) {
                        if (endpoint in received) continue
                        val groups = video.sourceGroups.map { SsrcGroup(it.semantics, it.sourceIds) }
                        val ssrc = calls().addIncomingVideo(id, video.participantId, endpoint, groups)
                        // An SSRC is an unsigned 32-bit number: NTgCalls returns an int, frames carry a long.
                        received[endpoint] = ssrc.toLong() and UNSIGNED_INT_MASK
                    }
                    endpoints = received.entries.associate { (endpoint, ssrc) -> ssrc to endpoint }
                }

            override fun setIncomingVideoSinks(sinks: Map<String, VideoFrameSink>) {
                incomingSinks = sinks
            }

            override suspend fun close() {
                if (mutableState.value == CallMediaState.Closed) return
                withContext(engineThread) {
                    try {
                        calls().stop(id)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "NTgCalls failed to stop the voice chat: ${e.javaClass.simpleName}")
                    }
                }
                mutableState.value = CallMediaState.Closed
                videoSink = null
                selfSink = null
                incomingSinks = emptyMap()
                endpoints = emptyMap()
                groupSessions.remove(id)
                requests.close()
            }

            private suspend fun engineCall(
                what: String,
                action: () -> Unit,
            ) = withContext(engineThread) {
                if (mutableState.value == CallMediaState.Closed) return@withContext
                try {
                    action()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "NTgCalls failed to $what: ${e.javaClass.simpleName}")
                }
            }
        }

        private companion object {
            const val TAG = "NtgCallsEngine"

            /** WebRTC's own rate; Oboe resamples from the device's where they differ. */
            const val SAMPLE_RATE = 48_000
            const val CHANNELS = 1

            /** The other side's video, as it comes: width, height and frame rate 0 are NTgCalls' "as sent". */
            val REMOTE_VIDEO = VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", true)

            const val UNSIGNED_INT_MASK = 0xFFFF_FFFFL
        }
    }
