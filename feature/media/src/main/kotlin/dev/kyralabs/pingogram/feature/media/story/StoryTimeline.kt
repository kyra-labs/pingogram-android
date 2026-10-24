package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.runtime.mutableFloatStateOf
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How far the story on screen has played, for [StoryViewerViewModel]: the segment of the story fills over its length,
 * and the viewer moves on when it is full ([onEnd]).
 *
 * - **A still** ([TimelineTarget.Still]: a photo, and what the viewer cannot show) lasts its duration on [timeSource]'s
 *   clock, counted only while it plays.
 * - **A video** ([TimelineTarget.Video]) plays on [player], once, and lasts as long as the player plays it; its
 *   progress is the player's position. A file the player cannot play goes to [onFailed], which shows something else.
 * - **Pausing** ([setPaused]: a finger held on the story, a sheet or the keyboard up, the screen stopped) stops the
 *   clock and the player; resuming carries on from there. [restart] starts the story over.
 * - **Progress** goes into snapshot state every [TICK] while it plays ([TimelineState.progress]), read in the draw
 *   phase by the segments, so a tick re-emits nothing: a UI ticker while a story plays, not polling.
 * - [release] when the view model is cleared.
 *
 * Main thread only, like the player.
 */
internal class StoryTimeline(
    private val player: VideoPlayer,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val onEnd: () -> Unit,
    private val onFailed: () -> Unit,
) {
    private val mutableState = MutableStateFlow<TimelineState?>(null)

    /** The story in the timeline; null when none is. */
    val state: StateFlow<TimelineState?> = mutableState.asStateFlow()

    private var session: Session? = null
    private var isPaused = true
    private var ticker: Job? = null

    private val listener =
        object : VideoPlayer.Listener {
            // The player pausing itself (audio focus) leaves the story where it is: it resumes with the next tap.
            override fun onPlayingChanged(isPlaying: Boolean) = Unit

            override fun onFirstFrame() {
                val current = session?.takeUnless { it.showsFrame } ?: return
                current.showsFrame = true
                publish()
            }

            override fun onEnded() {
                val current = session ?: return
                current.progress.floatValue = 1f
                finish(current)
            }

            override fun onError() {
                stop()
                onFailed()
            }
        }

    init {
        player.listener = listener
    }

    /**
     * Puts [target] in the timeline, the story on screen, or nothing for null. The same target again changes nothing;
     * another one starts from its beginning, playing at once unless paused.
     */
    fun show(target: TimelineTarget?) {
        if (target != null && session?.target == target) return
        stop()
        if (target == null) return
        val session = Session(target)
        this.session = session
        if (!isPaused) start(session) else publish()
    }

    /** Pauses or resumes the story in the timeline. */
    fun setPaused(paused: Boolean) {
        if (paused == isPaused) return
        isPaused = paused
        val current = session ?: return
        if (paused) pause(current) else start(current)
    }

    /** Starts the story in the timeline over, from its beginning. */
    fun restart() {
        val target = session?.target ?: return
        stop()
        show(target)
    }

    fun release() {
        stop()
        player.listener = null
        player.release()
    }

    private fun start(session: Session) {
        if (session.isFinished || session.isPlaying) return
        session.isPlaying = true
        when (val target = session.target) {
            is TimelineTarget.Still -> {
                session.mark = timeSource.markNow()
            }

            is TimelineTarget.Video -> {
                if (session.hasStarted) player.resume() else player.play(PlaySource.File(target.path), VideoKind.Video)
            }
        }
        session.hasStarted = true
        ticker?.cancel()
        ticker =
            scope.launch {
                while (true) {
                    val progress = progressOf(session)
                    session.progress.floatValue = progress
                    if (progress >= 1f && session.target is TimelineTarget.Still) {
                        finish(session)
                        break
                    }
                    delay(TICK)
                }
            }
        publish()
    }

    private fun pause(session: Session) {
        if (!session.isPlaying) return
        session.progress.floatValue = progressOf(session)
        session.played += session.mark?.elapsedNow() ?: Duration.ZERO
        session.mark = null
        session.isPlaying = false
        ticker?.cancel()
        if (session.target is TimelineTarget.Video) player.pause()
        publish()
    }

    private fun finish(session: Session) {
        if (session.isFinished) return
        session.isFinished = true
        session.isPlaying = false
        ticker?.cancel()
        publish()
        onEnd()
    }

    private fun stop() {
        val current = session ?: return
        session = null
        ticker?.cancel()
        ticker = null
        if (current.target is TimelineTarget.Video && current.hasStarted) player.stop()
        publish()
    }

    private fun progressOf(session: Session): Float {
        val length = session.target.durationMillis
        if (length <= 0) return 1f
        val playedMillis =
            when (session.target) {
                is TimelineTarget.Still -> {
                    (session.played + (session.mark?.elapsedNow() ?: Duration.ZERO)).inWholeMilliseconds
                }

                is TimelineTarget.Video -> {
                    player.positionMillis
                }
            }
        val total = if (session.target is TimelineTarget.Video) player.durationMillis ?: length else length
        return (playedMillis.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun publish() {
        mutableState.value = session?.let { TimelineState(it.target, it.isPlaying, it.showsFrame, it.readProgress) }
    }

    /**
     * [target] in the timeline: whether it plays, how long it played before the last pause ([played]) and since when it
     * plays again ([mark], for a still), whether a video was given to the player ([hasStarted]) and shows its first
     * frame, and its progress, in snapshot state of its own, so a story that leaves keeps no reader.
     */
    private class Session(
        val target: TimelineTarget,
    ) {
        val progress = mutableFloatStateOf(0f)
        val readProgress: () -> Float = { progress.floatValue }
        var isPlaying = false
        var isFinished = false
        var hasStarted = false
        var showsFrame = false
        var played: Duration = Duration.ZERO
        var mark: TimeMark? = null
    }

    private companion object {
        /** How often a playing story's progress is read: about every second frame at 60 Hz. */
        val TICK = 32.milliseconds
    }
}

/** What the timeline plays: a still for [durationMillis], or a video file at [path]. [storyId] tells stories apart. */
internal sealed interface TimelineTarget {
    val storyId: Int
    val durationMillis: Long

    data class Still(
        override val storyId: Int,
        override val durationMillis: Long,
    ) : TimelineTarget

    data class Video(
        override val storyId: Int,
        val path: String,
        override val durationMillis: Long,
    ) : TimelineTarget {
        // The path ends in the file's name.
        override fun toString() = "Video(storyId=$storyId, durationMillis=$durationMillis, …)"
    }
}

/** The story in the timeline: whether it plays, whether a video shows its first frame, and its progress (0–1). */
internal data class TimelineState(
    val target: TimelineTarget,
    val isPlaying: Boolean,
    val showsFrame: Boolean,
    val progress: () -> Float,
)
