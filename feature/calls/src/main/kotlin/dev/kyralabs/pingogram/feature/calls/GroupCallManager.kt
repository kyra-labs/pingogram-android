package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoinParameters
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipantVideo
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.ConferenceCallRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallCamera
import dev.kyralabs.pingogram.feature.calls.engine.CallEngine
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.GroupCallMode
import dev.kyralabs.pingogram.feature.calls.engine.GroupCallSession
import dev.kyralabs.pingogram.feature.calls.engine.IncomingVideo
import dev.kyralabs.pingogram.feature.calls.engine.SegmentResult
import dev.kyralabs.pingogram.feature.calls.engine.StreamQuality
import dev.kyralabs.pingogram.feature.calls.engine.StreamRequest
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import dev.kyralabs.pingogram.feature.calls.engine.VideoQuality
import dev.kyralabs.pingogram.feature.calls.system.CallSystem
import dev.kyralabs.pingogram.feature.calls.system.SystemCall
import dev.kyralabs.pingogram.feature.calls.system.SystemCallListener
import dev.kyralabs.pingogram.feature.calls.system.SystemCallRequest
import dev.kyralabs.pingogram.feature.calls.system.SystemEndCause
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
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
    // Injected: TDLib's group calls, engine, Telecom, service, clock, titles, line, network.
    @Suppress("LongParameterList")
    internal constructor(
        private val repository: GroupCallRepository,
        private val conferences: ConferenceCallRepository,
        private val engine: CallEngine,
        private val system: CallSystem,
        private val foreground: CallForeground,
        private val clock: CallClock,
        private val titles: ChatTitles,
        private val line: CallLine,
        // Lazy, as in CallManager: made only once a camera turns on, off the main thread.
        @param:CallNetwork private val network: dagger.Lazy<NetworkRepository>,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val mutableCall = MutableStateFlow<ActiveGroupCall?>(null)

        /** The voice chat the user is in, then every change; null without one. An ended one stays for [ENDED_SHOWN]. */
        val call: StateFlow<ActiveGroupCall?> = mutableCall.asStateFlow()

        private val mutableUpgradeInvites = MutableStateFlow<Outcome<ConferenceInvites>?>(null)

        /** What came of inviting people into a call that was one-to-one ([inviteAfterUpgrade]); null once said. */
        val upgradeInvites: StateFlow<Outcome<ConferenceInvites>?> = mutableUpgradeInvites.asStateFlow()

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

        /**
         * Joins the conference call of [target] (`docs/adr/0025-conference-calls.md`: a `t.me/call` link, an
         * invitation), [muted] (muted unless it continues a one-to-one call). [JoinResult.Busy] while the user is in
         * another call; [from], the one-to-one call that became this conference call, hands its line over.
         */
        suspend fun joinConference(
            target: ConferenceTarget,
            muted: Boolean = true,
            from: Any? = null,
        ): JoinResult {
            val call =
                mutex.withLock {
                    if (live != null || !takeLine(from)) return JoinResult.Busy
                    Live(chatId = 0, groupCallId = 0, joinAs = null, scope = callScope(), conference = target)
                        .also {
                            it.muted = muted
                            start(it)
                        }
                }
            connect(call)
            return JoinResult.Joining
        }

        /**
         * Starts a new conference call that one-to-one call [from] becomes (`docs/adr/0025-conference-calls.md`),
         * taking its line over, and joins it [muted]: the call's link once TDLib announced it, which the one-to-one
         * call is discarded with so that the other person joins too. A failure ends it with the reason.
         */
        suspend fun startConference(
            from: Any,
            muted: Boolean,
        ): Outcome<String> {
            val call =
                mutex.withLock {
                    if (live != null || !line.handOver(from, this)) return Outcome.Failure(DomainError.NotFound)
                    Live(chatId = 0, groupCallId = 0, joinAs = null, scope = callScope(), startsConference = true)
                        .also {
                            it.muted = muted
                            start(it)
                        }
                }
            connect(call)
            val id =
                mutex.withLock {
                    when (val phase = call.phase) {
                        is GroupCallPhase.Ended -> {
                            val reason = phase.reason
                            return Outcome.Failure(
                                (reason as? GroupCallEndReason.Failed)?.error ?: DomainError.NotFound,
                            )
                        }

                        else -> {
                            call.groupCallId
                        }
                    }
                }
            val link =
                withTimeoutOrNull(LINK_TIMEOUT) {
                    repository.groupCall(id).mapNotNull { it?.inviteLink?.takeIf(String::isNotEmpty) }.first()
                }
            return if (link != null) {
                Outcome.Success(link)
            } else {
                // Telegram made the call but did not name its link: nobody else could join it.
                mutex.withLock { if (live === call) finish(call, GroupCallEndReason.Failed(DomainError.NotFound)) }
                Outcome.Failure(DomainError.NotFound)
            }
        }

        /**
         * Invites [userIds] into the conference call a one-to-one call just became, for the voice chat's screen to
         * say what came of it once ([upgradeInvites]); nothing while no one was picked.
         */
        suspend fun inviteAfterUpgrade(userIds: Collection<Long>) {
            if (userIds.isEmpty()) return
            mutableUpgradeInvites.value = invite(userIds)
        }

        /** The screen said what came of [inviteAfterUpgrade]. */
        fun consumeUpgradeInvites() {
            mutableUpgradeInvites.value = null
        }

        /** The line for a new call: taken, or handed over from the one-to-one call it continues ([from]). */
        private fun takeLine(from: Any?): Boolean = if (from != null) line.handOver(from, this) else line.take(this)

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

        /**
         * Invites [userIds] into the conference call the user is in (`docs/adr/0025-conference-calls.md`): TDLib sends
         * each an invitation message in their private chat, to a video call while the user's camera is on. Those
         * already in the call count as invited; those whose privacy refuses it, or who were removed, come back in
         * [ConferenceInvites.refused]. A failure only when nobody could be invited for an error (no network, a flood
         * wait): the first one.
         */
        suspend fun invite(userIds: Collection<Long>): Outcome<ConferenceInvites> {
            val call =
                mutex.withLock { live?.takeIf { it.canInvite() } } ?: return Outcome.Failure(DomainError.NotFound)
            var invited = 0
            val refused = mutableListOf<Long>()
            var error: DomainError? = null
            for (userId in userIds) {
                when (val result = conferences.invite(call.groupCallId, userId, isVideo = call.wantsCamera)) {
                    is Outcome.Success -> {
                        when (result.value) {
                            is ConferenceInviteResult.Invited, ConferenceInviteResult.AlreadyParticipant -> invited++
                            ConferenceInviteResult.PrivacyRestricted, ConferenceInviteResult.Banned -> refused += userId
                        }
                    }

                    is Outcome.Failure -> {
                        refused += userId
                        if (error == null) error = result.error
                    }
                }
            }
            val failure = error
            return if (invited == 0 && failure != null) {
                Outcome.Failure(failure)
            } else {
                Outcome.Success(ConferenceInvites(invited, refused.toImmutableList()))
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

        /**
         * Sends the user's camera in the voice chat, or stops (`docs/adr/0024-video-in-voice-chats.md`): only where
         * Telegram allows it ([GroupCall.canEnableVideo]) and not in a stream. The front camera first; none while
         * the call is held. TDLib tells the others.
         */
        @Suppress("ReturnCount") // Guards: no voice chat, video not allowed, nothing to change.
        suspend fun setCameraOn(on: Boolean) {
            val call = mutex.withLock { live?.takeIf { it.phase !is GroupCallPhase.Ended } } ?: return
            if (on && !call.canSendVideo()) return
            if (on) loadCameras(call)
            mutex.withLock {
                if (live !== call || call.wantsCamera == on) return
                call.wantsCamera = on
                if (on) followNetwork(call)
                applyCamera(call)
                call.scope.launch { repository.setMyVideoEnabled(call.groupCallId, on) }
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
         * Shares the user's screen as the voice chat's presentation (`docs/adr/0026-screen-sharing.md`): the user
         * agreed in Android's dialog, whose result reached the engine. The call service runs as `mediaProjection`
         * first; then the engine opens the presentation, TDLib joins it (`startGroupCallScreenSharing`) and the screen
         * goes out beside the camera. Not in a stream; where a step fails, nothing is shared.
         */
        @Suppress("ReturnCount") // Guards: nothing to share in, the service's type refused.
        suspend fun startScreenSharing() {
            val call =
                mutex.withLock {
                    live?.takeIf { it.phase == GroupCallPhase.Connected && !it.isStream && !it.sharingScreen }
                } ?: return
            val session = call.session ?: return
            if (!foreground.setScreenCapture(true)) return
            val presentation = session.startPresentation()
            val joined =
                presentation?.let { repository.startScreenSharing(call.groupCallId, it.audioSourceId, it.payload) }
            if (joined is Outcome.Success) {
                session.connectPresentation(joined.value, call.videoQuality())
                mutex.withLock {
                    call.sharingScreen = true
                    if (live === call) publish()
                }
            } else {
                session.stopPresentation()
                foreground.setScreenCapture(false)
            }
        }

        /** Stops sharing the screen: the user's Stop, the notification's, or the capture ending by itself. */
        suspend fun stopScreenSharing() {
            val call =
                mutex.withLock {
                    live?.takeIf { it.sharingScreen }?.also {
                        it.sharingScreen = false
                        publish()
                    }
                } ?: return
            call.session?.stopPresentation()
            repository.endScreenSharing(call.groupCallId)
            foreground.setScreenCapture(false)
        }

        /** Where the user's own camera's frames go (the self-view), or nowhere: the screen sets it while it shows. */
        fun setSelfVideoSink(sink: VideoFrameSink?) {
            live?.selfSink?.value = SinkHolder(sink)
        }

        /**
         * Pins [participant]'s video, on this device only, or unpins with null: a pinned video is received first,
         * whatever the others do (`docs/adr/0024-video-in-voice-chats.md`).
         */
        suspend fun setPinned(participant: MessageSender?) {
            mutex.withLock {
                val call = live ?: return
                if (call.pinned == participant) return
                call.pinned = participant
                chooseVideos(call)
            }
        }

        /**
         * Where the frames of the participant's video at [endpointId] go, or nowhere with null: the screen sets each
         * while its tile shows. Not suspending, so a tile that goes away takes its sink back at once.
         */
        fun setParticipantVideoSink(
            endpointId: String,
            sink: VideoFrameSink?,
        ) {
            val sinks = live?.videoSinks ?: return
            sinks.update { current -> if (sink == null) current - endpointId else current + (endpointId to sink) }
        }

        // Transitions; all under the mutex unless said.

        /** Makes [call] the user's call and tells Android about it. */
        private fun start(call: Live) {
            clearEnded?.cancel()
            live = call
            publish()
            // A conference call that continues an unmuted one-to-one call talks from the start.
            call.microphoneStarted = !call.muted
            foreground.start(withMicrophone = !call.muted)
            call.scope.launch {
                val title = titles.title(call.chatId)
                val request =
                    if (call.isConference) {
                        SystemCallRequest.conference(title)
                    } else {
                        SystemCallRequest.voiceChat(title, call.chatId)
                    }
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
            // A conference call's id comes with its join: it is followed from there (connect).
            if (!call.isConference) follow(call)
        }

        /** Follows [call]'s group call and participants as TDLib announces them. */
        private fun follow(call: Live) {
            call.scope.launch {
                repository.groupCall(call.groupCallId).collect { info -> mutex.withLock { onGroupCall(call, info) } }
            }
            call.scope.launch {
                repository.participants(call.groupCallId).collect { participants ->
                    mutex.withLock {
                        if (live === call) {
                            call.participants = participants
                            chooseVideos(call)
                        }
                        onSelf(call, participants.firstOrNull { it.isCurrentUser })
                    }
                }
            }
        }

        /** Creates the engine's call, joins through TDLib, then connects; not under the mutex. */
        private suspend fun connect(call: Live) {
            val session =
                if (call.isConference) {
                    createConferenceOrNull(call)
                } else {
                    createSessionOrNull(call.groupCallId)
                }
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
            val joined = join(call, session)
            when (joined) {
                is Outcome.Failure -> {
                    mutex.withLock { if (live === call) finish(call, GroupCallEndReason.Failed(joined.error)) }
                }

                is Outcome.Success -> {
                    call.scope.launch { session.state.collect { media -> mutex.withLock { onMedia(call, media) } } }
                    call.scope.launch { session.mode.collect { mode -> mutex.withLock { onMode(call, mode) } } }
                    call.scope.launch { session.streamRequests.collect { answer(call, session, it) } }
                    call.scope.launch { call.sink.collect { session.setVideoSink(it.sink) } }
                    call.scope.launch { call.selfSink.collect { session.setSelfVideoSink(it.sink) } }
                    call.scope.launch { call.incoming.collect { session.receiveVideos(it) } }
                    call.scope.launch { call.videoSinks.collect { session.setIncomingVideoSinks(it) } }
                    call.scope.launch { session.screenEnded.collect { stopScreenSharing() } }
                    call.scope.launch {
                        call.camera.collect { source ->
                            session.setCamera(source?.camera, source?.quality ?: VideoQuality.High)
                        }
                    }
                    if (call.isConference) {
                        call.scope.launch { session.unknownSources.collect { mutex.withLock { mapSources(call, it) } } }
                    }
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
                    if (call.connectedAt != null) {
                        call.phase = GroupCallPhase.Reconnecting
                        if (!call.reconnected) {
                            // Lost once: the rest of the call sends less video, as one-to-one calls do.
                            call.reconnected = true
                            applyCamera(call)
                        }
                    }
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
            chooseVideos(call)
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
            if (call.wantsCamera && !call.canSendVideo()) {
                // Telegram took video back (an administrator, or the call became a stream): the camera stops.
                call.wantsCamera = false
                applyCamera(call)
            }
            // Who speaks changes which videos come first.
            chooseVideos(call)
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
                            if (live === call) {
                                call.onHold = onHold
                                call.session?.let { session -> call.scope.launch { session.setOnHold(onHold) } }
                                applyCamera(call)
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

        /**
         * The others' videos [call] receives: at most [MAX_RECEIVED_VIDEOS], the pinned participant's first (their
         * shared screen before their camera), then shared screens, then the cameras of those speaking, then by TDLib's
         * order (`docs/adr/0024-video-in-voice-chats.md`, `docs/adr/0026-screen-sharing.md`); none in a stream.
         * Publishes.
         */
        private fun chooseVideos(call: Live) {
            val speaking =
                call.info
                    ?.recentSpeakers
                    .orEmpty()
                    .filter { it.isSpeaking }
                    .mapTo(HashSet()) { it.participant }
            val chosen =
                if (call.isStream) {
                    emptyList()
                } else {
                    call.participants
                        .filter { !it.isCurrentUser }
                        .flatMap { participant ->
                            listOfNotNull(
                                participant.screen?.let { ShownVideo(participant, it, isScreen = true) },
                                participant.video?.let { ShownVideo(participant, it, isScreen = false) },
                            )
                        }.sortedWith(
                            compareByDescending<ShownVideo> { it.participant.participant == call.pinned }
                                .thenByDescending { it.isScreen }
                                .thenByDescending {
                                    it.participant.isSpeaking || it.participant.participant in speaking
                                },
                        ).take(MAX_RECEIVED_VIDEOS)
                }
            call.shownVideos = chosen
            call.incoming.value =
                chosen.map { (participant, video) ->
                    IncomingVideo(participant.participant.id(), video.endpointId, video.sourceGroups)
                }
            publish()
        }

        /** The camera [call] should send now and how much: none while held or not wanted. */
        private fun applyCamera(call: Live) {
            call.camera.value = call.sendingCamera()?.let { CameraSource(it, call.videoQuality()) }
            publish()
        }

        /** Follows the network once the camera first turns on, for the most it sends. */
        private fun followNetwork(call: Live) {
            if (call.followsNetwork) return
            call.followsNetwork = true
            call.scope.launch {
                network.get().networkStatus.collect { status ->
                    mutex.withLock {
                        if (live === call && status.type != NetworkType.None && call.network != status) {
                            call.network = status
                            applyCamera(call)
                        }
                    }
                }
            }
        }

        /** Asks the engine for the device's cameras once per voice chat; not under the mutex. */
        private suspend fun loadCameras(call: Live) {
            if (call.camerasLoaded) return
            val cameras = camerasOrEmpty()
            mutex.withLock {
                call.cameras = cameras
                call.camerasLoaded = true
                if (cameras.none { it.isFront }) call.facingFront = false
            }
        }

        private fun publish() {
            mutableCall.value = live?.toActiveGroupCall()
        }

        /** The device's cameras; none if the engine cannot list them, which leaves the camera off. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any failure means no camera to send.
        private suspend fun camerasOrEmpty(): List<CallCamera> =
            try {
                engine.cameras()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }

        /**
         * Joins [call] through TDLib with [session]'s payload, answering the engine's connection parameters: a voice
         * chat by its id, a conference call by its link or invitation, whose id then comes with the answer and is
         * followed from there.
         */
        private suspend fun join(
            call: Live,
            session: GroupCallSession,
        ): Outcome<String> {
            val target = call.conference
            if (!call.isConference) {
                return repository.join(call.groupCallId, call.joinAs, session.audioSourceId, session.joinPayload, true)
            }
            val parameters = ConferenceJoinParameters(session.audioSourceId, session.joinPayload, call.muted, false)
            val joined = if (target != null) conferences.join(target, parameters) else conferences.create(parameters)
            return joined.map { joined ->
                mutex.withLock {
                    call.groupCallId = joined.groupCallId
                    call.encryption?.groupCallId = joined.groupCallId
                    if (live === call) {
                        publish()
                        follow(call)
                    }
                }
                joined.params
            }
        }

        /**
         * Tells the engine whose the audio sources [ssrcs] of conference [call] are, from the participants TDLib
         * announced; those it does not know yet are asked for again by the engine.
         */
        private suspend fun mapSources(
            call: Live,
            ssrcs: List<Int>,
        ) {
            val session = call.session ?: return
            val users =
                call.participants
                    .filter { it.audioSourceId in ssrcs }
                    .mapNotNull { participant ->
                        (participant.participant as? MessageSender.User)?.let { participant.audioSourceId to it.userId }
                    }.toMap()
            if (users.isNotEmpty()) session.mapSources(users)
        }

        /** The engine's conference call; null as for [createSessionOrNull]. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any failure ends the call with a reason.
        private suspend fun createConferenceOrNull(call: Live): GroupCallSession? {
            val encryption = TdlibConferenceEncryption(conferences)
            call.encryption = encryption
            return try {
                engine.createConference(muted = call.muted, encryption = encryption)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
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
            /** The voice chat's chat; 0 for a conference call, which has none. */
            val chatId: Long,
            /** TDLib's id of the call; a conference call's once its join answered, 0 before. */
            var groupCallId: Int,
            /** Whom the user joins as: the chat's default (TDLib's choice for null). */
            val joinAs: MessageSender?,
            /** Everything of this voice chat runs in it; cancelled when it ends. */
            val scope: CoroutineScope,
            /** How a conference call is reached; null for a chat's voice chat and a call [startsConference]. */
            val conference: ConferenceTarget? = null,
            /** A new conference call, which the user starts (`createGroupCall`) as a one-to-one call becomes it. */
            val startsConference: Boolean = false,
        ) {
            /** A conference call: end-to-end encrypted, outside any chat. */
            val isConference: Boolean get() = conference != null || startsConference

            /** A conference call's encryption, which needs the call's id once it is known. */
            var encryption: TdlibConferenceEncryption? = null

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
            val selfSink = MutableStateFlow(SinkHolder(null))
            var onHold = false

            /** The user wants the camera on (it sends once the engine connects, and not while held). */
            var wantsCamera = false
            var facingFront = true
            var cameras: List<CallCamera> = emptyList()
            var camerasLoaded = false
            var network: NetworkStatus? = null
            var followsNetwork = false
            var reconnected = false
            val camera = MutableStateFlow<CameraSource?>(null)

            /** The user's screen goes out as the presentation. */
            var sharingScreen = false

            /** Everyone TDLib announced, in its order. */
            var participants: List<GroupCallParticipant> = emptyList()

            /** The participant the user pinned on this device. */
            var pinned: MessageSender? = null

            /** The others' videos received, in the order they show. */
            var shownVideos: List<ShownVideo> = emptyList()
            val incoming = MutableStateFlow<List<IncomingVideo>>(emptyList())
            val videoSinks = MutableStateFlow<Map<String, VideoFrameSink>>(emptyMap())

            val isStream: Boolean get() = streamed || info?.isRtmpStream == true

            fun canSendVideo(): Boolean = !isStream && info?.canEnableVideo == true

            fun videoQuality(): VideoQuality = VideoQuality.forNetwork(network, reconnected)

            fun sendingCamera(): CallCamera? =
                if (!wantsCamera || onHold) {
                    null
                } else {
                    cameras.firstOrNull { it.isFront == facingFront } ?: cameras.firstOrNull()
                }

            /** A conference call that is joined and not over: people can be invited into it. */
            fun canInvite(): Boolean = isConference && groupCallId != 0 && phase !is GroupCallPhase.Ended

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
                    isConference = isConference,
                    phase = phase,
                    isStream = isStream,
                    isMuted = muted,
                    canUnmute = canUnmute(),
                    isHandRaised = self?.isHandRaised == true,
                    canEnd = info?.canBeManaged == true,
                    canInvite = canInvite() && phase == GroupCallPhase.Connected,
                    audio = audio,
                    connectedAtMillis = connectedAt,
                    video =
                        GroupCallVideo(
                            canSend = canSendVideo(),
                            camera =
                                sendingCamera()?.let { if (it.isFront) CameraFacing.Front else CameraFacing.Back }
                                    ?: if (wantsCamera && !camerasLoaded) CameraFacing.Front else null,
                            canSwitchCamera = cameras.any { it.isFront } && cameras.any { !it.isFront },
                            remote =
                                shownVideos
                                    .map { (participant, video, isScreen) ->
                                        RemoteParticipantVideo(
                                            participant.participant,
                                            video.endpointId,
                                            video.isPaused,
                                            isScreen,
                                        )
                                    }.toImmutableList(),
                            pinned = pinned,
                            canShareScreen = phase == GroupCallPhase.Connected && !isStream,
                            isSharingScreen = sharingScreen,
                        ),
                )
        }

        private companion object {
            /** How long an ended voice chat stays on screen with why it ended. */
            val ENDED_SHOWN = 2.seconds

            /** How long a new conference call waits for TDLib to name its link. */
            val LINK_TIMEOUT = 10.seconds

            /** The most videos of others received at once (`docs/adr/0024-video-in-voice-chats.md`). */
            const val MAX_RECEIVED_VIDEOS = 4

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

private fun MessageSender.id(): Long =
    when (this) {
        is MessageSender.User -> userId
        is MessageSender.Chat -> chatId
    }

/** A video a voice chat receives: [participant]'s camera or shared screen ([isScreen]). */
private data class ShownVideo(
    val participant: GroupCallParticipant,
    val video: GroupCallParticipantVideo,
    val isScreen: Boolean,
)
