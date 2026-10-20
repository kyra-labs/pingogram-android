package dev.kyralabs.pingogram.feature.chat.ui.voice

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEffect
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.attach.ReplyHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The composer's voice recording (`REQUIREMENTS.md` FR-C5), for `ChatViewModel`: [state] is the recording in progress,
 * null while there is none.
 *
 * - **States**: idle → recording, held under the finger → locked (hands-free), then paused and resumed at will → sent
 *   or cancelled, back to idle. Events run one after the other in the order they came, each once the recorder has done
 *   what the one before asked, so a release right after the press is not lost while the microphone starts.
 * - **Starting** ([VoiceEvent.Start]) shows the recording at once, stops what plays ([onStart]), asks the
 *   [repository] for a file in the format the [recorder] records in, and starts it; a microphone that cannot be used
 *   ends it with [VoiceEffect.RecordingFailed]. The permission is the screen's to check: without it, the press is
 *   [VoiceEvent.PermissionNeeded], which explains it ([VoiceEffect.ExplainMicrophone]) before the system asks; a
 *   refusal offers the settings ([VoiceEffect.MicrophoneDenied]), a grant says to hold ([VoiceEffect.HoldToRecord]).
 * - **Meter**: while it records, every [TICK_MILLIS] the recorder's level and the length (pauses left out, from
 *   [clock], milliseconds that only go forward) go into the [RecordingMeter], and the level into the levels the
 *   waveform is made of. This is a UI ticker while recording, not polling: nothing ticks while paused or idle.
 * - **Sending** ([VoiceEvent.Send]) ends the recording at once and finishes its file; one shorter than
 *   [MIN_DURATION_MILLIS] is thrown away with [VoiceEffect.HoldToRecord], as a tap on the microphone is. Otherwise the
 *   voice message goes out with its length and waveform ([voiceNoteWaveform]), taking the composer's reply along
 *   ([replies]); a refusal gives the reply back with a snackbar ([ChatEffect.ShowError]).
 * - **Cancelling** ([VoiceEvent.Cancel]) ends it and deletes its file.
 * - **The screen stops** ([onStopped]: the app goes to the background, or the screen rotates): the recording locks and
 *   pauses, as Android gives no microphone to apps in the background, and the user finds it as they left it, to go on,
 *   send or delete. [release], when the view model is cleared, cancels it. It does not survive process death.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
@Suppress(
    "LongParameterList", // The chat, the recorder, where files go, its scope, the reply, the clock and callbacks.
    "TooManyFunctions", // A step per event of the recording, as in ChatComposer, and the ticker they share.
)
internal class ChatRecorder(
    private val chatId: Long,
    private val recorder: VoiceRecorder,
    private val repository: AttachmentRepository,
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

    private val commands = Channel<VoiceEvent>(Channel.UNLIMITED)
    private var session: Session? = null
    private var ticker: Job? = null

    /** Starts carrying out the events, once. */
    fun start() {
        scope.launch { for (command in commands) carryOut(command) }
    }

    fun onEvent(event: VoiceEvent) {
        when (event) {
            VoiceEvent.PermissionNeeded -> onEffect(VoiceEffect.ExplainMicrophone)
            is VoiceEvent.PermissionAnswered -> onEffect(answerOf(event.granted))
            else -> commands.trySend(event)
        }
    }

    /** The screen stopped: a recording locks and pauses, to be found as it was left. */
    fun onStopped() {
        commands.trySend(VoiceEvent.Lock)
        commands.trySend(VoiceEvent.Pause)
    }

    /** The view model is cleared: a recording ends and its file is deleted, and the recorder is freed. */
    fun release() {
        commands.close()
        ticker?.cancel()
        session?.path?.let { path ->
            recorder.cancel()
            repository.discardVoiceNote(path)
        }
        session = null
        recorder.release()
    }

    private suspend fun carryOut(command: VoiceEvent) {
        when (command) {
            is VoiceEvent.Start -> {
                begin(command.locked)
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
        val session = Session(isLocked = locked)
        this.session = session
        onStart()
        publish()
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
        recorder.pause()
    }

    private suspend fun resume() {
        val current = session?.takeIf { it.isPaused } ?: return
        recorder.resume()
        current.isPaused = false
        current.resumedAt = clock()
        startTicker(current)
        publish()
    }

    private suspend fun finish() {
        val current = session ?: return
        val duration = current.durationMillis(clock())
        end()
        val path = current.path ?: return
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

    /** Sends [voiceNote] with the composer's reply, in a coroutine of its own so the next recording need not wait. */
    private fun send(voiceNote: RecordedVoiceNote) {
        val replyTo = replies.takeReply()
        scope.launch {
            val result = repository.sendVoiceNote(chatId, voiceNote, replyToMessageId = replyTo)
            if (result is Outcome.Failure) {
                replyTo?.let(replies::restoreReply)
                onEffect(ChatEffect.ShowError(result.error))
            }
        }
    }

    private fun cancel() {
        val current = session ?: return
        end()
        current.path?.let { path ->
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
                    val level = recorder.level()
                    session.levels.add(level)
                    session.meter.update(session.durationMillis(clock()), level)
                    delay(TICK_MILLIS)
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publish() {
        mutableState.value = session?.let { VoiceRecordingUiState(it.isLocked, it.isPaused, it.meter) }
    }

    /** The recording in progress: its file once there is one, how long it ran, and every level it had. */
    private inner class Session(
        var isLocked: Boolean,
    ) {
        var path: String? = null
        var isPaused = false

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
    }
}

/** What the permission's answer shows: to hold the button now it is [granted], else where to grant it. */
private fun answerOf(granted: Boolean): VoiceEffect =
    if (granted) VoiceEffect.HoldToRecord else VoiceEffect.MicrophoneDenied

/** [millis] as the whole seconds Telegram counts a voice note in, rounded, at least 1. */
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
