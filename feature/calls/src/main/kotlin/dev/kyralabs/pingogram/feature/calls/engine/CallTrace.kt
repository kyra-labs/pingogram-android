package dev.kyralabs.pingogram.feature.calls.engine

import android.os.Build
import android.os.Trace

/**
 * Trace sections and counters of a video call's frame path (`docs/PERFORMANCE.md` §9, "Video calls"), which
 * `tools/perfetto/video-call.pbtxt` records. They cost a check of a flag while no trace records the app.
 */
internal object CallTrace {
    /** NTgCalls hands a batch of frames over from its thread, through JNI, to the sinks. */
    const val FRAMES = "callFrames"

    /** A frame copied into a direct buffer and queued for its renderer. */
    const val FRAME_COPY = "callFrameCopy"

    inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}

/**
 * Counts the frames a renderer takes and sets the trace counter "<name>.fps" once a second, while a trace records the
 * app (Android 10+, where apps may set counters). Called from one thread, the engine's.
 */
internal class FrameRateCounter(
    name: String,
) {
    private val counter = "$name.fps"
    private var frames = 0
    private var since = 0L

    fun onFrame(nowNanos: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Trace.isEnabled()) return
        frames++
        if (since == 0L) since = nowNanos
        val elapsed = nowNanos - since
        if (elapsed < SECOND_NANOS) return
        Trace.setCounter(counter, frames * SECOND_NANOS / elapsed)
        frames = 0
        since = nowNanos
    }
}

private const val SECOND_NANOS = 1_000_000_000L
