package dev.kyralabs.pingogram.core.ui.trace

import android.os.Build
import android.os.Trace

/**
 * The trace sections of the media viewer's way in and out (`docs/PERFORMANCE.md` §9, "Opening and closing media"):
 *
 * - `openMedia`: from the tap on a photo, video or GIF in a conversation ([beginOpen], `feature:chat`'s route, as it
 *   hands the tap to the app's navigation) until the viewer has composed the page of that media and drawn the frame
 *   that holds it ([endOpen], `feature:media`). The container transform that grows the media into the page starts in
 *   the frame after ([OverlayTrace.ENTER]).
 * - `closeMedia`: from the gesture or button that closes the viewer ([beginClose]: back, a swipe, the back button)
 *   until the viewer has left the composition ([endClose]), after its exit transition ([OverlayTrace.EXIT]).
 *
 * Async sections, as they span frames; `OpenMediaBenchmark` measures them and `tools/perfetto/media-viewer.sql` lists
 * them with the frames inside them. Each costs a check of the API level while no trace records the app. Main thread
 * only.
 */
object MediaViewerTrace {
    const val OPEN_SECTION = "openMedia"
    const val CLOSE_SECTION = "closeMedia"

    private var opening = false
    private var closing = false

    /** Media was tapped; an opening still waiting for its page is given up (its section ends here). */
    fun beginOpen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (opening) Trace.endAsyncSection(OPEN_SECTION, COOKIE)
        opening = true
        Trace.beginAsyncSection(OPEN_SECTION, COOKIE)
    }

    /** The viewer has drawn the page of the opened media; once per [beginOpen]. */
    fun endOpen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !opening) return
        opening = false
        Trace.endAsyncSection(OPEN_SECTION, COOKIE)
    }

    /** The viewer was told to close; a closing still under way is given up (its section ends here). */
    fun beginClose() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (closing) Trace.endAsyncSection(CLOSE_SECTION, COOKIE)
        closing = true
        Trace.beginAsyncSection(CLOSE_SECTION, COOKIE)
    }

    /** The viewer has left the composition; once per [beginClose]. */
    fun endClose() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !closing) return
        closing = false
        Trace.endAsyncSection(CLOSE_SECTION, COOKIE)
    }

    /** One viewer opens or closes at a time, so one cookie per section. */
    private const val COOKIE = 0
}

/**
 * The trace sections of an overlay destination's transitions (`OverlayHost`, `core:ui`): [ENTER] from the frame its
 * content starts coming in (after `PostponeOverlayEnter` released it) until the transition settled, and [EXIT] from
 * the frame it starts going out until it is gone. Every frame inside them runs the container transform of the media
 * viewer, the one overlay destination; `tools/perfetto/media-viewer.sql` reads their frames. Main thread only.
 */
object OverlayTrace {
    const val ENTER = "overlayEnter"
    const val EXIT = "overlayExit"

    private val running = HashSet<String>()

    fun begin(section: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!running.add(section)) Trace.endAsyncSection(section, COOKIE)
        Trace.beginAsyncSection(section, COOKIE)
    }

    fun end(section: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !running.remove(section)) return
        Trace.endAsyncSection(section, COOKIE)
    }

    private const val COOKIE = 0
}
