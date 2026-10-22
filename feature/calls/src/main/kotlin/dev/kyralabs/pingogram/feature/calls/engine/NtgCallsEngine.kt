// NTgCalls reports every failure as one of many unrelated RuntimeExceptions (ConnectionException, MediaException,
// RTCException, SignalingException, …), which have no common base of their own.
@file:Suppress("TooGenericExceptionCaught")

package dev.kyralabs.pingogram.feature.calls.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
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

        private var instance: NTgCalls? = null

        /** NTgCalls, created on [engineThread] with its callbacks the first time a call needs it. */
        private fun calls(): NTgCalls =
            instance ?: run {
                // WebRTC's Java side (audio, the application context of NTgCalls' helpers) needs the context first.
                ContextUtils.initialize(context.applicationContext)
                NTgCalls().also { engine ->
                    engine.onSignalingData { id, data -> sessions[id]?.emitSignaling(data) }
                    engine.onConnectionChange { id, info -> sessions[id]?.onState(info.state.toMediaState()) }
                    instance = engine
                }
            }

        override suspend fun protocol(): CallProtocol =
            withContext(engineThread) { NTgCalls.getProtocol().toCallProtocol() }

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
                    engine.setStreamSources(
                        id,
                        StreamMode.CAPTURE,
                        MediaDescription(microphone(isCapture = true), null, null, null),
                    )
                    engine.setStreamSources(
                        id,
                        StreamMode.PLAYBACK,
                        MediaDescription(null, microphone(isCapture = false), null, null),
                    )
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

        private inner class NtgCallsSession(
            private val id: Long,
        ) : CallSession {
            private val mutableState = MutableStateFlow(CallMediaState.Connecting)
            override val state: StateFlow<CallMediaState> = mutableState.asStateFlow()

            // Kept until the call manager collects it: NTgCalls sends its first packets while connectP2p runs.
            // Signaling is small and rare (a few packets a second at most), and never blocks NTgCalls' threads.
            private val signaling = Channel<ByteArray>(Channel.UNLIMITED)
            override val signalingData: Flow<ByteArray> = signaling.receiveAsFlow()

            @Volatile private var muted = false

            @Volatile private var onHold = false

            fun emitSignaling(data: ByteArray) {
                signaling.trySend(data)
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

        private companion object {
            const val TAG = "NtgCallsEngine"

            /** WebRTC's own rate; Oboe resamples from the device's where they differ. */
            const val SAMPLE_RATE = 48_000
            const val CHANNELS = 1
        }
    }
