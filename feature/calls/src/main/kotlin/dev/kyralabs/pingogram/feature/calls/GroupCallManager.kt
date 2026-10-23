package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallEngine
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.GroupCallMode
import dev.kyralabs.pingogram.feature.calls.engine.GroupCallSession
import dev.kyralabs.pingogram.feature.calls.engine.SegmentResult
import dev.kyralabs.pingogram.feature.calls.engine.StreamQuality
import dev.kyralabs.pingogram.feature.calls.engine.StreamRequest
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import dev.kyralabs.pingogram.feature.calls.system.CallSystem
import dev.kyralabs.pingogram.feature.calls.system.SystemCall
import dev.kyralabs.pingogram.feature.calls.system.SystemCallListener
import dev.kyralabs.pingogram.feature.calls.system.SystemCallRequest
import dev.kyralabs.pingogram.feature.calls.system.SystemEndCause
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the voice chat or live stream the user is in (`docs/adr/0023-group-voice-chats-and-live-streams.md`): TDLib's
 * group calls ([GroupCallRepository]), the engine's group call ([CallEngine.createGroupCall]), Android's Telecom
 * ([CallSystem]) and the foreground service, as one state machine whose state is [call], beside [CallManager] for
 * one-to-one calls. One call at a time ([CallLine]): joining while in another call does nothing.
 *
 * Joining: the engine makes the join payload, TDLib's `joinVideoChat` answers with the call's parameters, the engine
 * connects. The user joins muted; unmuting tells TDLib, which tells the others. A live stream (or a call Telegram
 * streams) is received in segments the engine asks for and this fetches from TDLib.
 *
 * Every change happens under [mutex], in the order TDLib's updates and the user's actions arrive.
 */
@Singleton
@Suppress("TooManyFunctions") // One function per action of the user and per transition of the state machine.
class GroupCallManager
    @Inject
    @Suppress("LongParameterList") // Injected: TDLib's group calls, engine, Telecom, service, clock, titles, line.
    internal constructor(
        private val repository: GroupCallRepository,
        private val engine: CallEngine,
        private val system: CallSystem,
        private val foreground: CallForeground,
        private val clock: CallClock,
        private val titles: ChatTitles,
        private val line: CallLine,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val mutableCall = MutableStateFlow<ActiveGroupCall?>(null)

        /** The voice chat the user is in, then every change; null without one. An ended one stays for [ENDED_SHOWN]. */
        val call: StateFlow<ActiveGroupCall?> = mutableCall.asStateFlow()

        private val mutex = Mutex()

        @Volatile private var live: Live? = null
        private var clearEnded: Job? = null

        /**
         * Joins chat [chatId]'s voice chat, muted. [JoinResult.Busy] while the user is in another call (the screen
         * shows that one), [JoinResult.NoVoiceChat] if the chat has none.
         */
        @Suppress("ReturnCount") // Guards under the lock: this voice chat already, another call, no voice chat.
        suspend fun join(chatId: Long): JoinResult {
            mutex.withLock {
                live?.let { current ->
                    return if (current.chatId == chatId && current.phase !is GroupCallPhase.Ended) {
                        JoinResult.Joining
                    } else {
                        JoinResult.Busy
                    }
                }
                if (line.isBusy) return JoinResult.Busy
            }
            val videoChat = repository.videoChat(chatId).first() ?: return JoinResult.NoVoiceChat
            val call =
                mutex.withLock {
                    if (live != null || !line.take(this)) return JoinResult.Busy
                    Live(chatId, videoChat.groupCallId, videoChat.defaultParticipant, callScope()).also { start(it) }
                }
            connect(call)
            return JoinResult.Joining
        }

        /** Starts a voice chat in chat [chatId], as an administrator who may, and joins it. */
        suspend fun startVoiceChat(chatId: Long): Outcome<JoinResult> {
            if (line.isBusy) return Outcome.Success(JoinResult.Busy)
            return when (val started = repository.startVideoChat(chatId)) {
                is Outcome.Failure -> {
                    started
                }

                is Outcome.Success -> {
                    // TDLib announces the chat's voice chat with the answer; wait for it rather than race it.
                    val announced =
                        withTimeoutOrNull(ANNOUNCE_TIMEOUT) { repository.videoChat(chatId).filterNotNull().first() }
                    Outcome.Success(if (announced == null) JoinResult.NoVoiceChat else join(chatId))
                }
            }
        }

        /** Leaves the voice chat; it goes on for the others. */
        suspend fun leave() {
            mutex.withLock {
                val call = live ?: return
                if (call.phase is GroupCallPhase.Ended) return
                val id = call.groupCallId
                scope.launch { repository.leave(id) }
                finish(call, GroupCallEndReason.Left)
            }
        }

        /** Ends the voice chat for everyone, as one who may manage it. */
        suspend fun endForEveryone(): Outcome<Unit> {
            val call =
                mutex.withLock { live?.takeIf { it.phase !is GroupCallPhase.Ended } } ?: return Outcome.Success(Unit)
            val result = repository.end(call.groupCallId)
            if (result is Outcome.Success) mutex.withLock { if (live === call) finish(call, GroupCallEndReason.Ended) }
            return result
        }

        /**
         * Mutes or unmutes the microphone; unmuting does nothing while an administrator muted the user and did not
         * allow them to unmute (they raise their hand instead). TDLib tells the others.
         */
        suspend fun setMuted(muted: Boolean) {
            mutex.withLock {
                val call = live ?: return
                if (call.isStream || (!muted && !call.canUnmute())) return
                applyMuted(call, muted, tellTdlib = true)
            }
        }

        /** Raises or lowers the user's hand: asking an administrator to let them speak. */
        suspend fun setHandRaised(raised: Boolean) {
            val (call, self) = mutex.withLock { live?.let { it to it.self?.participant } } ?: return
            if (self == null) return
            repository.setHandRaised(call.groupCallId, self, raised)
        }

        fun selectRoute(route: AudioRoute) {
            live?.system?.selectRoute(route)
        }

        /** Where a live stream's video goes, or nowhere with null: the screen sets it while it shows. */
        fun setVideoSink(sink: VideoFrameSink?) {
            live?.sink?.value = SinkHolder(sink)
        }

        // Transitions; all under the mutex unless said.

        /** Makes [call] the user's call and tells Android about it. */
        private fun start(call: Live) {
            clearEnded?.cancel()
            live = call
            publish()
            foreground.start(withMicrophone = false)
            call.scope.launch {
                val request = SystemCallRequest.voiceChat(titles.title(call.chatId), call.chatId)
                mutex.withLock {
                    if (live !== call || call.phase is GroupCallPhase.Ended) return@launch
                    val system = system.add(call.scope, request, listener(call))
                    call.system = system
                    if (call.phase == GroupCallPhase.Connected) system.setActive()
                    call.scope.launch { system.audio.collect { audio -> mutex.withLock { updateAudio(call, audio) } } }
                    // The system's mute (a headset's button, the system UI) after the value it starts with.
                    call.scope.launch {
                        system.isMuted.drop(1).collect { muted ->
                            mutex.withLock { if (muted || call.canUnmute()) applyMuted(call, muted, tellTdlib = true) }
                        }
                    }
                }
            }
            call.scope.launch {
                repository.groupCall(call.groupCallId).collect { info -> mutex.withLock { onGroupCall(call, info) } }
            }
            call.scope.launch {
                repository.participants(call.groupCallId).collect { participants ->
                    mutex.withLock { onSelf(call, participants.firstOrNull { it.isCurrentUser }) }
                }
            }
        }

        /** Creates the engine's call, joins through TDLib, then connects; not under the mutex. */
        private suspend fun connect(call: Live) {
            val session = createSessionOrNull(call.groupCallId)
            if (session == null) {
                mutex.withLock { finish(call, GroupCallEndReason.Failed(ENGINE_FAILED)) }
                return
            }
            val kept =
                mutex.withLock {
                    if (live !== call || call.phase is GroupCallPhase.Ended) {
                        false
                    } else {
                        call.session = session
                        true
                    }
                }
            if (!kept) {
                session.close()
                return
            }
            val joined =
                repository.join(
                    call.groupCallId,
                    call.joinAs,
                    session.audioSourceId,
                    session.joinPayload,
                    isMuted = true,
                )
            when (joined) {
                is Outcome.Failure -> {
                    mutex.withLock { if (live === call) finish(call, GroupCallEndReason.Failed(joined.error)) }
                }

                is Outcome.Success -> {
                    call.scope.launch { session.state.collect { media -> mutex.withLock { onMedia(call, media) } } }
                    call.scope.launch { session.mode.collect { mode -> mutex.withLock { onMode(call, mode) } } }
                    call.scope.launch { session.streamRequests.collect { answer(call, session, it) } }
                    call.scope.launch { call.sink.collect { session.setVideoSink(it.sink) } }
                    session.connect(joined.value)
                    repository.loadParticipants(call.groupCallId, PARTICIPANTS_PAGE)
                }
            }
        }

        private fun onMedia(
            call: Live,
            media: CallMediaState,
        ) {
            if (live !== call || call.phase is GroupCallPhase.Ended) return
            when (media) {
                CallMediaState.Connected -> {
                    if (call.connectedAt == null) {
                        call.connectedAt = clock.elapsedRealtimeMillis()
                        call.system?.setActive()
                    }
                    call.phase = GroupCallPhase.Connected
                }

                CallMediaState.Connecting -> {
                    if (call.connectedAt != null) call.phase = GroupCallPhase.Reconnecting
                }

                CallMediaState.Failed -> {
                    scope.launch { repository.leave(call.groupCallId) }
                    finish(call, GroupCallEndReason.Disconnected)
                }

                CallMediaState.Closed -> {
                    Unit
                }
            }
            publish()
        }

        private fun onMode(
            call: Live,
            mode: GroupCallMode?,
        ) {
            if (live !== call) return
            call.streamed = mode == GroupCallMode.Stream
            publish()
        }

        private fun onGroupCall(
            call: Live,
            info: GroupCall?,
        ) {
            if (live !== call || info == null || call.phase is GroupCallPhase.Ended) return
            call.info = info
            if (!info.isActive && info.scheduledStartDate == 0L) {
                finish(call, GroupCallEndReason.Ended)
                return
            }
            publish()
        }

        private fun onSelf(
            call: Live,
            self: GroupCallParticipant?,
        ) {
            if (live !== call || self == null) return
            call.self = self
            // An administrator muted the user for everyone: the microphone follows.
            if (self.isMutedForAllUsers && !call.muted) applyMuted(call, muted = true, tellTdlib = false)
            publish()
        }

        private fun applyMuted(
            call: Live,
            muted: Boolean,
            tellTdlib: Boolean,
        ) {
            if (call.muted == muted) return
            call.muted = muted
            if (!muted && !call.microphoneStarted) {
                // Android lets the service use the microphone in the background only once the user talks.
                call.microphoneStarted = true
                foreground.start(withMicrophone = true)
            }
            call.session?.let { session -> call.scope.launch { session.setMuted(muted) } }
            val self = call.self?.participant
            if (tellTdlib && self != null) {
                call.scope.launch { repository.setMuted(call.groupCallId, self, muted) }
            }
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

        /** Answers what a live stream asks for from TDLib; not under the mutex, as segments come one after another. */
        private fun answer(
            call: Live,
            session: GroupCallSession,
            request: StreamRequest,
        ) {
            call.scope.launch {
                when (request) {
                    StreamRequest.Time -> {
                        val streams = repository.streams(call.groupCallId)
                        val time = (streams as? Outcome.Success)?.value?.maxOfOrNull { it.timeOffset } ?: 0L
                        session.sendTime(time)
                    }

                    is StreamRequest.Segment -> {
                        val segment =
                            repository.streamSegment(
                                call.groupCallId,
                                timeOffset = request.timeMs,
                                scale = SEGMENT_SCALE,
                                channelId = request.channelId,
                                quality = request.quality?.toVideoQuality(),
                            )
                        session.sendSegment(request, segment.toSegmentResult())
                    }
                }
            }
        }

        private fun listener(call: Live) =
            object : SystemCallListener {
                // A voice chat is never answered: the user joins it.
                override fun onAnswer() = Unit

                override fun onDisconnect() {
                    scope.launch { if (live === call) leave() }
                }

                override fun onHold(onHold: Boolean) {
                    scope.launch {
                        mutex.withLock {
                            if (live ===
                                call
                            ) {
                                call.session?.let { session -> call.scope.launch { session.setOnHold(onHold) } }
                            }
                        }
                    }
                }
            }

        private fun finish(
            call: Live,
            reason: GroupCallEndReason,
        ) {
            if (call.phase is GroupCallPhase.Ended) return
            call.phase = GroupCallPhase.Ended(reason)
            call.system?.end(reason.toSystemCause())
            line.release(this)
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
            mutableCall.value = live?.toActiveGroupCall()
        }

        /** The engine's group call; null if its library could not load or the microphone could not open. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any failure ends the voice chat with a reason.
        private suspend fun createSessionOrNull(groupCallId: Int): GroupCallSession? =
            try {
                engine.createGroupCall(groupCallId, muted = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }

        private fun callScope() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

        /** The voice chat the user is in, as the manager keeps it. */
        private class Live(
            val chatId: Long,
            val groupCallId: Int,
            /** Whom the user joins as: the chat's default (TDLib's choice for null). */
            val joinAs: MessageSender?,
            /** Everything of this voice chat runs in it; cancelled when it ends. */
            val scope: CoroutineScope,
        ) {
            var phase: GroupCallPhase = GroupCallPhase.Joining
            var muted = true
            var microphoneStarted = false
            var audio = CallAudio()
            var connectedAt: Long? = null
            var streamed = false
            var info: GroupCall? = null
            var self: GroupCallParticipant? = null
            var system: SystemCall? = null
            var session: GroupCallSession? = null
            val sink = MutableStateFlow(SinkHolder(null))

            val isStream: Boolean get() = streamed || info?.isRtmpStream == true

            /** Not muted by an administrator, or allowed to unmute: a stream has nothing to unmute. */
            fun canUnmute(): Boolean {
                val self = self
                return when {
                    isStream -> false
                    self == null -> info?.muteNewParticipants != true
                    else -> !self.isMutedForAllUsers || self.canUnmuteSelf
                }
            }

            fun toActiveGroupCall() =
                ActiveGroupCall(
                    chatId = chatId,
                    groupCallId = groupCallId,
                    phase = phase,
                    isStream = isStream,
                    isMuted = muted,
                    canUnmute = canUnmute(),
                    isHandRaised = self?.isHandRaised == true,
                    canEnd = info?.canBeManaged == true,
                    audio = audio,
                    connectedAtMillis = connectedAt,
                )
        }

        private companion object {
            /** How long an ended voice chat stays on screen with why it ended. */
            val ENDED_SHOWN = 2.seconds

            /** How many participants TDLib announces at first; the screen asks for more as it scrolls. */
            const val PARTICIPANTS_PAGE = 50

            /** One-second segments: NTgCalls' `segment_duration_`, TDLib's scale 0. */
            const val SEGMENT_SCALE = 0

            /** How long a voice chat just started may take to reach its chat. */
            val ANNOUNCE_TIMEOUT = 10.seconds

            val ENGINE_FAILED = DomainError.Unknown(0, "The call engine could not start")
        }
    }

/** A sink, or none: a state flow that may hold null and still tell each change apart. */
private class SinkHolder(
    val sink: VideoFrameSink?,
)

private fun GroupCallEndReason.toSystemCause(): SystemEndCause =
    when (this) {
        GroupCallEndReason.Left -> SystemEndCause.Local
        GroupCallEndReason.Ended -> SystemEndCause.Remote
        GroupCallEndReason.Disconnected, is GroupCallEndReason.Failed -> SystemEndCause.Error
    }

private fun StreamQuality.toVideoQuality(): GroupCallVideoQuality =
    when (this) {
        StreamQuality.Thumbnail -> GroupCallVideoQuality.Thumbnail
        StreamQuality.Medium -> GroupCallVideoQuality.Medium
        StreamQuality.Full -> GroupCallVideoQuality.Full
    }

private fun StreamSegment.toSegmentResult(): SegmentResult =
    when (this) {
        is StreamSegment.Data -> SegmentResult.Data(bytes)
        StreamSegment.NotReady -> SegmentResult.NotReady
        StreamSegment.Unavailable -> SegmentResult.ResyncNeeded
    }
