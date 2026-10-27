package dev.kyralabs.pingogram.core.ui.trace

import android.os.Build
import android.os.Trace

/**
 * The trace section `sendMessage` (`docs/PERFORMANCE.md` §1, "Send text message ≤ 50 ms, optimistic"): from the tap
 * that sends a text ([begin], the composer of `feature:chat`) until the conversation has drawn its bubble, still on its
 * way to Telegram ([end]). An async section, as it spans frames; `SendMessageBenchmark` measures it. It costs a check
 * of the API level while no trace records the app. Main thread only.
 */
object SendMessageTrace {
    const val SECTION = "sendMessage"

    /** A text was sent and its bubble is not drawn yet. */
    private var sending = false

    /** A text was sent; one still waiting for its bubble is given up (its section ends here). */
    fun begin() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (sending) Trace.endAsyncSection(SECTION, COOKIE)
        sending = true
        Trace.beginAsyncSection(SECTION, COOKIE)
    }

    /** The bubble of a message on its way has been drawn; once per [begin]. */
    fun end() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !sending) return
        sending = false
        Trace.endAsyncSection(SECTION, COOKIE)
    }

    /** One text is traced at a time, so one cookie. */
    private const val COOKIE = 0
}
