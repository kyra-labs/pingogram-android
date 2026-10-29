package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import dev.kyralabs.pingogram.feature.chat.ui.VideoNoteEffect
import dev.kyralabs.pingogram.feature.chat.ui.VideoNoteEvent
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEffect
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.attach.ReplyHandoff
import dev.kyralabs.pingogram.feature.chat.ui.videonote.RecordedVideo
import dev.kyralabs.pingogram.feature.chat.ui.videonote.VideoNoteLimits
import dev.kyralabs.pingogram.feature.chat.ui.videonote.VideoNoteRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The composer's voice and round video recording (`REQUIREMENTS.md` FR-C5, `docs/adr/0032-round-video-messages.md`),
 * for `ChatViewModel`: [state] is the recording in progress, null while there is none, and [mode] what the record
 * button records.
 *
 * - **Mode**: the button records voice messages, or video messages once a tap switched it ([VideoNoteEvent.SwitchMode],
 *   kept in [settings] across chats and starts), where the device has a camera ([VideoNoteRecorder.hasCamera], read
 *   once [start] runs). Video needs the camera's and the microphone's permissions, which the screen checks: without
 *   them a switch or a press is [VideoNoteEvent.PermissionNeeded], whose rationale sheet
 *   ([RecordModeUiState.rationale]) comes before the system asks; once the system no longer asks ([savedState], kept
 *   across process death), the sheet offers the settings. Granted while switching, the button switches.
 * - **States**: idle → recording, held under the finger → locked (hands-free), then paused and resumed at will → sent
 *   or cancelled, back to idle. Events run one after the other in the order they came, each once the recorder has done
 *   what the one before asked, so a release right after the press is not lost while the microphone or camera starts.
 * - **Starting** ([VoiceEvent.Start]) shows the recording at once, stops what plays ([onStart]), asks the repository
 *   for its files, and starts the [recorder] (voice, in the format it records in) or the [video] recorder (from the
 *   camera the user last recorded with, the front one at first). A microphone or camera that cannot be used ends it
 *   with [VoiceEffect.RecordingFailed] or [VideoNoteEffect.RecordingFailed]. The microphone's permission for voice is
 *   the screen's to check: without it, the press is [VoiceEvent.PermissionNeeded], which explains it
 *   ([VoiceEffect.ExplainMicrophone]) before the system asks; a refusal offers the settings
 *   ([VoiceEffect.MicrophoneDenied]), a grant says to hold ([VoiceEffect.HoldToRecord]).
 * - **Meter**: while it records, every [TICK_MILLIS] the recorder's level and the length (pauses left out, from
 *   [clock], milliseconds that only go forward) go into the [RecordingMeter], and the level into the levels a voice
 *   message's waveform is made of. This is a UI ticker while recording, not polling: nothing ticks while paused or
 *   idle. A video message is sent once it reaches [VideoNoteLimits.MAX_DURATION_MILLIS], as a release would.
 * - **Flipping** ([VideoNoteEvent.FlipCamera]) moves a video recording to the other camera, in the same file; a camera
 *   that cannot be opened keeps the one before, with [VideoNoteEffect.FlipFailed].
 * - **Sending** ([VoiceEvent.Send]) ends the recording at once and finishes its file; one shorter than
 *   [MIN_DURATION_MILLIS] is thrown away with [VoiceEffect.HoldToRecord], as a tap on the microphone is. Otherwise the
 *   voice message goes out with its length and waveform ([voiceNoteWaveform]), the video message with its length, side
 *   and first frame, taking the composer's reply along ([replies]); a refusal gives the reply back with a snackbar
 *   ([ChatEffect.ShowError]).
 * - **Cancelling** ([VoiceEvent.Cancel]) ends it and deletes its files.
 * - **The screen stops** ([onStopped]: the app goes to the background, or the screen rotates): a voice recording locks
 *   and pauses, as Android gives no microphone to apps in the background, and the user finds it as they left it, to go
 *   on, send or delete. A video recording ends there, releasing the camera and the microphone: the user finds it
 *   locked and stopped ([VideoRecordingUiState.isStopped]), to send or delete. [release], when the view model is
 *   cleared, cancels it. It does not survive process death.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
@Suppress(
    "LongParameterList", // The chat, both recorders, where files go, its scope, the reply, the clock and callbacks.
    "TooManyFunctions", // A step per event of the recording, as in ChatComposer, and the ticker they share.
    "LargeClass", // The one state machine of the record button, for voice and video, which share every gesture.
)
internal class ChatRecorder(
    private val chatId: Long,
    private val recorder: VoiceRecorder,
    private val repository: VoiceNoteRepository,
    private val video: VideoNoteRecorder,
    private val videoNotes: VideoNoteRepository,
    private val settings: ComposerSettingsRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val replies: ReplyHandoff,
    private val clock: () -> Long,
    private val onStart: () -> Unit,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
) {
    private val mutableState = MutableStateFlow<VoiceRecordingUiState?>(null)

    /** The recording in progress; null while there is none. */
    val state: StateFlow<VoiceRecordingUiState?> = mutableState.asStateFlow()

    private val hasCamera = MutableStateFlow(false)
    private val rationale = MutableStateFlow<CameraRationale?>(null)

    /** What the record button records, and the rationale sheet of the camera, while it shows. */
    val mode: StateFlow<RecordModeUiState> =
        combine(settings.recordsVideoMessages, hasCamera, rationale) { video, camera, rationale ->
            RecordModeUiState(video = video && camera, canSwitch = camera, rationale = rationale)
        }.stateIn(scope, SharingStarted.Eagerly, RecordModeUiState())

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var session: Session? = null
    private var ticker: Job? = null

    /** The camera the user last recorded a video message with. */
    private var front = true

    /** Whether a grant of the permissions switches the button to video: the sheet came from a switch. */
    private var switchOnGrant = false

    /** Starts carrying out the events, once, and learns whether the device has a camera. */
    fun start() {
        hasCamera.value = video.hasCamera
        scope.launch { for (command in commands) carryOut(command) }
    }

    fun onEvent(event: VoiceEvent) {
        when (event) {
            VoiceEvent.PermissionNeeded -> onEffect(VoiceEffect.ExplainMicrophone)
            is VoiceEvent.PermissionAnswered -> onEffect(answerOf(event.granted))
            else -> commands.trySend(Command.Voice(event))
        }
    }

    fun onEvent(event: VideoNoteEvent) {
        when (event) {
            VideoNoteEvent.SwitchMode -> switchMode()
            is VideoNoteEvent.PermissionNeeded -> explainPermissions(event.switching)
            is VideoNoteEvent.PermissionAnswered -> answerPermissions(event.granted, event.canAskAgain)
            VideoNoteEvent.DismissRationale -> rationale.value = null
            VideoNoteEvent.FlipCamera -> commands.trySend(Command.Flip)
        }
    }

    /** The screen stopped: a voice recording locks and pauses, a video recording ends, to be found as it was left. */
    fun onStopped() {
        commands.trySend(Command.ScreenStopped)
    }

    /** The view model is cleared: a recording ends and its files are deleted, and the recorders are freed. */
    fun release() {
        commands.close()
        ticker?.cancel()
        session?.let(::discard)
        session = null
        recorder.release()
        video.release()
    }

    private fun switchMode() {
        if (session != null || !hasCamera.value) return
        val toVideo = !mode.value.video
        scope.launch {
            val saved = settings.setRecordsVideoMessages(toVideo)
            if (saved is Outcome.Failure) onEffect(ChatEffect.ShowError(saved.error))
        }
    }

    private fun explainPermissions(switching: Boolean) {
        if (!hasCamera.value) return
        switchOnGrant = switching
        rationale.value = CameraRationale(deniedForGood = savedState[KEY_DENIED_FOR_GOOD] ?: false)
    }

    private fun answerPermissions(
        granted: Boolean,
        canAskAgain: Boolean,
    ) {
        if (granted) {
            savedState[KEY_DENIED_FOR_GOOD] = false
            rationale.value = null
            if (switchOnGrant && !mode.value.video) switchMode()
            switchOnGrant = false
        } else {
            savedState[KEY_DENIED_FOR_GOOD] = !canAskAgain
            if (rationale.value != null) rationale.value = CameraRationale(deniedForGood = !canAskAgain)
        }
    }

    private suspend fun carryOut(command: Command) {
        when (command) {
            is Command.Voice -> carryOut(command.event)
            Command.Flip -> flip()
            Command.ScreenStopped -> screenStopped()
            Command.LimitReached -> if (session?.isVideo == true) finish()
        }
    }

    private suspend fun carryOut(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Start -> {
                begin(event.locked)
            }

            VoiceEvent.Lock -> {
                lock()
            }

            VoiceEvent.Pause -> {
                pause()
            }

            VoiceEvent.Resume -> {
                resume()
            }

            VoiceEvent.Send -> {
                finish()
            }

            VoiceEvent.Cancel -> {
                cancel()
            }

            VoiceEvent.PermissionNeeded, is VoiceEvent.PermissionAnswered -> {}
        }
    }

    private suspend fun begin(locked: Boolean) {
        if (session != null) return
        val session = Session(isLocked = locked, isVideo = mode.value.video, isFront = front)
        this.session = session
        onStart()
        publish()
        if (session.isVideo) beginVideo(session) else beginVoice(session)
    }

    private suspend fun beginVoice(session: Session) {
        val format = recorder.format()
        when (val file = repository.newVoiceNoteFile(format.extension)) {
            is Outcome.Success -> {
                record(session, file.value, format)
            }

            is Outcome.Failure -> {
                end()
                onEffect(ChatEffect.ShowError(file.error))
            }
        }
    }

    /** Starts [session] recording into [path]; a microphone that cannot be used ends it, deleting the file. */
    private suspend fun record(
        session: Session,
        path: String,
        format: VoiceFormat,
    ) {
        if (recorder.start(path, format)) {
            session.path = path
            session.resumedAt = clock()
            startTicker(session)
        } else {
            log.warn("Voice recording could not start: $format")
            repository.discardVoiceNote(path)
            end()
            onEffect(VoiceEffect.RecordingFailed)
        }
    }

    /** Starts [session] recording video into new files; a camera or microphone that cannot be used ends it. */
    private suspend fun beginVideo(session: Session) {
        val files =
            when (val made = videoNotes.newVideoNoteFiles()) {
                is Outcome.Success -> {
                    made.value
                }

                is Outcome.Failure -> {
                    end()
                    onEffect(ChatEffect.ShowError(made.error))
                    return
                }
            }
        val started = video.start(files.video, session.isFront)
        if (started == null) {
            log.warn("Video recording could not start")
            videoNotes.discardVideoNote(files.video)
            end()
            onEffect(VideoNoteEffect.RecordingFailed)
            return
        }
        session.path = files.video
        session.thumbnail = files.thumbnail
        session.isFront = started.front
        session.canFlip = started.canFlip
        session.resumedAt = clock()
        publish()
        startTicker(session)
    }

    private fun lock() {
        val current = session?.takeIf { !it.isLocked } ?: return
        current.isLocked = true
        publish()
    }

    private suspend fun pause() {
        val current = session?.takeIf { !it.isPaused && it.path != null } ?: return
        current.accumulatedMillis = current.durationMillis(clock())
        current.isPaused = true
        stopTicker()
        current.meter.update(current.accumulatedMillis, level = null)
        publish()
        if (current.isVideo) video.pause() else recorder.pause()
    }

    private suspend fun resume() {
        val current = session?.takeIf { it.isPaused && it.stopped == null } ?: return
        if (current.isVideo) video.resume() else recorder.resume()
        current.isPaused = false
        current.resumedAt = clock()
        startTicker(current)
        publish()
    }

    private suspend fun flip() {
        val current = session?.takeIf { it.isVideo && it.canFlip && it.path != null && it.stopped == null } ?: return
        if (video.flip(!current.isFront)) {
            current.isFront = !current.isFront
            front = current.isFront
            publish()
        } else {
            onEffect(VideoNoteEffect.FlipFailed)
        }
    }

    /**
     * The screen stopped: a voice recording locks and pauses; a video recording ends, its camera and microphone freed,
     * and waits locked and stopped to be sent or deleted.
     */
    private suspend fun screenStopped() {
        val current = session ?: return
        if (current.isVideo) {
            stopVideo(current)
        } else {
            lock()
            pause()
        }
    }

    /** Ends [current]'s video recording as the screen stopped, keeping it to be sent or deleted. */
    private suspend fun stopVideo(current: Session) {
        val path = current.path
        if (path == null || current.stopped != null) return
        current.accumulatedMillis = current.durationMillis(clock())
        current.isPaused = true
        current.isLocked = true
        stopTicker()
        current.meter.update(current.accumulatedMillis, level = null)
        val recorded = video.stop(checkNotNull(current.thumbnail))
        if (recorded == null) {
            log.warn("Video recording could not be finished as the screen stopped")
            videoNotes.discardVideoNote(path)
            end()
            onEffect(VideoNoteEffect.RecordingFailed)
        } else {
            current.stopped = recorded
            publish()
        }
    }

    private suspend fun finish() {
        val current = session ?: return
        val duration = current.durationMillis(clock())
        end()
        val path = current.path ?: return
        if (current.isVideo) {
            finishVideo(current, path, duration)
        } else {
            finishVoice(current, path, duration)
        }
    }

    private suspend fun finishVoice(
        current: Session,
        path: String,
        duration: Long,
    ) {
        val complete = recorder.stop()
        when {
            duration < MIN_DURATION_MILLIS -> {
                repository.discardVoiceNote(path)
                onEffect(VoiceEffect.HoldToRecord)
            }

            !complete -> {
                log.warn("Voice recording could not be finished")
                repository.discardVoiceNote(path)
                onEffect(VoiceEffect.RecordingFailed)
            }

            else -> {
                send(RecordedVoiceNote(path, seconds(duration), voiceNoteWaveform(current.levels.toArray())))
            }
        }
    }

    private suspend fun finishVideo(
        current: Session,
        path: String,
        duration: Long,
    ) {
        val recorded = current.stopped ?: video.stop(checkNotNull(current.thumbnail))
        when {
            duration < MIN_DURATION_MILLIS -> {
                videoNotes.discardVideoNote(path)
                onEffect(VoiceEffect.HoldToRecord)
            }

            recorded == null -> {
                log.warn("Video recording could not be finished")
                videoNotes.discardVideoNote(path)
                onEffect(VideoNoteEffect.RecordingFailed)
            }

            else -> {
                val seconds = seconds(duration).coerceAtMost(MAX_VIDEO_SECONDS)
                send(RecordedVideoNote(path, seconds, recorded.length, recorded.thumbnail))
            }
        }
    }

    /** Sends [voiceNote] with the composer's reply, in a coroutine of its own so the next recording need not wait. */
    private fun send(voiceNote: RecordedVoiceNote) {
        sendWithReply { replyTo -> repository.sendVoiceNote(chatId, voiceNote, replyTo = replyTo) }
    }

    /** Sends [videoNote] as [send] does a voice note. */
    private fun send(videoNote: RecordedVideoNote) {
        sendWithReply { replyTo -> videoNotes.sendVideoNote(chatId, videoNote, replyTo = replyTo) }
    }

    private fun sendWithReply(sending: suspend (replyTo: ReplyTarget?) -> Outcome<Unit>) {
        val replyTo = replies.takeReply()
        scope.launch {
            val result = sending(replyTo)
            if (result is Outcome.Failure) {
                replyTo?.let(replies::restoreReply)
                onEffect(ChatEffect.ShowError(result.error))
            }
        }
    }

    private fun cancel() {
        val current = session ?: return
        end()
        discard(current)
    }

    /** Ends [current]'s recording, if it records, and deletes its files. */
    private fun discard(current: Session) {
        val path = current.path ?: return
        if (current.isVideo) {
            if (current.stopped == null) video.cancel()
            videoNotes.discardVideoNote(path)
        } else {
            recorder.cancel()
            repository.discardVoiceNote(path)
        }
    }

    /** No recording any more: the ticker stops, and the composer comes back. */
    private fun end() {
        session = null
        stopTicker()
        publish()
    }

    private fun startTicker(session: Session) {
        ticker?.cancel()
        ticker =
            scope.launch {
                while (true) {
                    val level = if (session.isVideo) video.level() else recorder.level()
                    session.levels.add(level)
                    val duration = session.durationMillis(clock())
                    session.meter.update(duration, level)
                    if (session.isVideo && duration >= VideoNoteLimits.MAX_DURATION_MILLIS) {
                        commands.trySend(Command.LimitReached)
                        break
                    }
                    delay(TICK_MILLIS)
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publish() {
        mutableState.value =
            session?.let { session ->
                VoiceRecordingUiState(
                    isLocked = session.isLocked,
                    isPaused = session.isPaused,
                    meter = session.meter,
                    video =
                        if (session.isVideo) {
                            VideoRecordingUiState(
                                isFront = session.isFront,
                                canFlip = session.canFlip && session.stopped == null,
                                isStopped = session.stopped != null,
                            )
                        } else {
                            null
                        },
                )
            }
    }

    /** What the recorder is asked to do, one at a time: the user's events, and what the screen and the ticker say. */
    private sealed interface Command {
        data class Voice(
            val event: VoiceEvent,
        ) : Command

        data object Flip : Command

        data object ScreenStopped : Command

        data object LimitReached : Command
    }

    /**
     * The recording in progress: voice or [isVideo]; its file once there is one (and a video's thumbnail's path), how
     * long it ran, and every level it had; a video's camera, and its finished file once the screen stopped it.
     */
    private inner class Session(
        var isLocked: Boolean,
        val isVideo: Boolean,
        var isFront: Boolean,
    ) {
        var path: String? = null
        var thumbnail: String? = null
        var isPaused = false
        var canFlip = false

        /** A video recording the screen's stop finished, to be sent or deleted as it is. */
        var stopped: RecordedVideo? = null

        /** When it last started or resumed recording, by [clock]; null until it records. */
        var resumedAt: Long? = null

        /** How long it recorded before the last pause. */
        var accumulatedMillis = 0L
        val meter = RecordingMeter()
        val levels = LevelLog()

        fun durationMillis(now: Long): Long {
            val since = resumedAt?.takeIf { !isPaused } ?: return accumulatedMillis
            return accumulatedMillis + (now - since)
        }
    }

    companion object {
        /** How often the level and the length are read while recording: 20 times a second. */
        const val TICK_MILLIS = 50L

        /** Shorter recordings are not sent: most are a tap on the microphone. */
        const val MIN_DURATION_MILLIS = 1_000L

        /** Whether Android no longer asks for video's permissions, kept across process death. */
        private const val KEY_DENIED_FOR_GOOD = "videoNote.deniedForGood"

        private const val MAX_VIDEO_SECONDS = (VideoNoteLimits.MAX_DURATION_MILLIS / 1_000L).toInt()
    }
}

/** What the permission's answer shows: to hold the button now it is [granted], else where to grant it. */
private fun answerOf(granted: Boolean): VoiceEffect =
    if (granted) VoiceEffect.HoldToRecord else VoiceEffect.MicrophoneDenied

/** [millis] as the whole seconds Telegram counts a voice or video note in, rounded, at least 1. */
private fun seconds(millis: Long): Int = ((millis + HALF_SECOND) / SECOND).toInt().coerceAtLeast(1)

private const val SECOND = 1_000L
private const val HALF_SECOND = 500L

/** The levels of a recording, one per tick, in a growing array rather than boxed in a list. */
private class LevelLog {
    private var levels = FloatArray(INITIAL_CAPACITY)
    private var size = 0

    fun add(level: Float) {
        if (size == levels.size) levels = levels.copyOf(size * 2)
        levels[size++] = level
    }

    fun toArray(): FloatArray = levels.copyOf(size)

    private companion object {
        /** A minute of ticks. */
        const val INITIAL_CAPACITY = 1_200
    }
}
