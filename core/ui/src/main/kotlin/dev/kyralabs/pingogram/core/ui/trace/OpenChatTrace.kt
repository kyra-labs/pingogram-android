package dev.kyralabs.pingogram.core.ui.trace

import android.os.Build
import android.os.Trace

/**
 * The trace section `openChat` (`docs/PERFORMANCE.md` §1, "Open chat ≤ 150 ms"): from a tap on a chat in the chat list
 * ([begin], `:app`) until the conversation has drawn its first messages ([end], `feature:chat`). An async section, as
 * it spans frames; `OpenChatBenchmark` measures it, and `tools/perfetto/startup.sql` lists it. It costs a check of the
 * API level while no trace records the app. Main thread only.
 */
object OpenChatTrace {
    const val SECTION = "openChat"

    /** The chat whose opening is being traced, null when none is. */
    private var opening: Long? = null

    /** Chat [chatId] was tapped; a chat still opening is given up (its section ends here). */
    fun begin(chatId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (opening != null) Trace.endAsyncSection(SECTION, COOKIE)
        opening = chatId
        Trace.beginAsyncSection(SECTION, COOKIE)
    }

    /** Chat [chatId]'s conversation has drawn its first messages, or its empty or error state; once per [begin]. */
    fun end(chatId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || opening != chatId) return
        opening = null
        Trace.endAsyncSection(SECTION, COOKIE)
    }

    /** One chat opens at a time, so one cookie. */
    private const val COOKIE = 0
}
