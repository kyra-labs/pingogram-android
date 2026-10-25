package dev.kyralabs.pingogram.startup

import android.os.Build
import android.os.Trace

/**
 * The trace sections of the app's start (`docs/PERFORMANCE.md` §2, §9), which `tools/perfetto/startup.pbtxt` records
 * and `StartupBenchmark` measures. They cost a check of a flag while no trace records the app.
 */
internal object StartupTrace {
    /** Hilt injects `PingogramApplication`'s fields: TDLib's client and the caching repositories. */
    const val INJECT = "app.inject"

    /** TDLib's client is started (its work runs on a background dispatcher). */
    const val TDLIB_START = "app.tdlibStart"

    /** What follows TDLib's state in every process is made and started, in the background. */
    const val STARTERS = "app.starters"

    /** `MainActivity.onCreate`, up to `setContent`. */
    const val ACTIVITY_CREATE = "main.onCreate"

    /**
     * From `MainActivity.onCreate` until the settings the first frame needs are read (the Appearance settings, the
     * passcode lock, the language), while the splash screen stays: an async section, as it spans frames.
     */
    const val AWAIT_SETTINGS = "main.awaitSettings"

    /**
     * The settings file's first read, which `PingogramApplication` starts so the first activity finds the settings in
     * memory: an async section, as it runs on a background dispatcher.
     */
    const val READ_SETTINGS = "app.readSettings"

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

/** [name] around [block], as an async section (Android 10+), for work that suspends or runs off the main thread. */
internal suspend inline fun <T> asyncTraceSection(
    name: String,
    block: () -> T,
): T {
    val traced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    if (traced) Trace.beginAsyncSection(name, 0)
    try {
        return block()
    } finally {
        if (traced) Trace.endAsyncSection(name, 0)
    }
}

/** [StartupTrace.AWAIT_SETTINGS] of one activity: [begin] in its `onCreate`, [end] at its first frame with content. */
internal class AwaitSettingsTrace {
    private var open = false

    fun begin() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        open = true
        Trace.beginAsyncSection(StartupTrace.AWAIT_SETTINGS, 0)
    }

    fun end() {
        if (!open || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        open = false
        Trace.endAsyncSection(StartupTrace.AWAIT_SETTINGS, 0)
    }
}
