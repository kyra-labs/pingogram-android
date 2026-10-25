package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf

/**
 * A voice message, or a round [video] message, being recorded in the composer (`REQUIREMENTS.md` FR-C5,
 * `docs/adr/0032-round-video-messages.md`): held under the finger, or [isLocked] (hands-free) and then maybe
 * [isPaused]. How long and how loud is in its [meter], which changes many times a second without changing this state.
 */
@Immutable
data class VoiceRecordingUiState(
    val isLocked: Boolean = false,
    val isPaused: Boolean = false,
    val meter: RecordingMeter = RecordingMeter(),
    val video: VideoRecordingUiState? = null,
)

/**
 * What a round video message being recorded adds: which camera it records from ([isFront]), whether the other one can
 * take over ([canFlip]), and whether it ended as the screen stopped ([isStopped]), when it can only be sent or deleted.
 */
@Immutable
data class VideoRecordingUiState(
    val isFront: Boolean = true,
    val canFlip: Boolean = false,
    val isStopped: Boolean = false,
)

/**
 * What the composer's record button records (`docs/adr/0032-round-video-messages.md`): voice messages, or [video]
 * messages once a tap switched it, where the device has a camera to switch to ([canSwitch]); and the sheet that says
 * why video needs the camera and the microphone, while it shows ([rationale]).
 */
@Immutable
data class RecordModeUiState(
    val video: Boolean = false,
    val canSwitch: Boolean = false,
    val rationale: CameraRationale? = null,
)

/**
 * Why video messages need the camera and the microphone, before Android asks; once Android no longer asks
 * ([deniedForGood]), the sheet offers the app's settings instead.
 */
@Immutable
data class CameraRationale(
    val deniedForGood: Boolean = false,
)

/**
 * How long a recording has run and how loud it is, in snapshot state of its own (`docs/PERFORMANCE.md` §4): the
 * recording's ticker writes it, and only the timer (once a second, [elapsedMillis]) and the drawing of the level and
 * the live waveform ([level], [recentLevels], read in the draw phase) read it, so a tick recomposes nothing else.
 * Main thread only.
 */
@Stable
class RecordingMeter(
    elapsedMillis: Long = 0L,
    recent: FloatArray = FloatArray(0),
) {
    private val elapsed = mutableLongStateOf(elapsedMillis)
    private val current = mutableFloatStateOf(recent.lastOrNull() ?: 0f)

    /** The last [RECENT_LEVELS] levels, a ring whose next slot is [next]; [version] counts its changes for readers. */
    private val ring = FloatArray(RECENT_LEVELS)
    private var next = 0
    private var count = 0
    private val version = mutableIntStateOf(0)

    init {
        recent.takeLast(RECENT_LEVELS).forEach(::push)
    }

    /** How long it has recorded, pauses left out, in milliseconds. */
    val elapsedMillis: Long get() = elapsed.longValue

    /** How loud the microphone is now, from 0 (silent) to 1; 0 while paused. */
    val level: Float get() = current.floatValue

    /** The newest [max] levels at most (one per tick), oldest first. */
    fun recentLevels(max: Int): FloatArray {
        // Read for its changes: a reader in the draw phase draws again on the next tick.
        version.intValue
        val size = minOf(max, count)
        return FloatArray(size) { index -> ring[(next - size + index).mod(RECENT_LEVELS)] }
    }

    /** A tick: the recording is [elapsedMillis] long, and as loud as [level] (null while paused: no new level). */
    internal fun update(
        elapsedMillis: Long,
        level: Float?,
    ) {
        elapsed.longValue = elapsedMillis
        current.floatValue = level ?: 0f
        if (level != null) {
            push(level)
            version.intValue++
        }
    }

    private fun push(level: Float) {
        ring[next] = level
        next = (next + 1) % RECENT_LEVELS
        count = minOf(count + 1, RECENT_LEVELS)
    }

    companion object {
        /** Enough levels for the live waveform on the widest composer. */
        const val RECENT_LEVELS = 128
    }
}
