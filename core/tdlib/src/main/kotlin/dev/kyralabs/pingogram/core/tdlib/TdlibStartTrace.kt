package dev.kyralabs.pingogram.core.tdlib

import android.os.Build
import android.os.Trace

/**
 * The trace sections of the first account's TDLib start (`docs/PERFORMANCE.md` §2), which `StartupBenchmark` reports
 * beside `PG:loginKnown`, so a trace shows where the wait for the first login step goes. Async sections, as each spans
 * threads; behind a seam, as JVM unit tests have no [Trace].
 */
internal interface TdlibStartTrace {
    fun begin(section: String)

    fun end(section: String)

    /** No sections: the other accounts' clients, whose start no benchmark measures. */
    object None : TdlibStartTrace {
        override fun begin(section: String) = Unit

        override fun end(section: String) = Unit
    }

    /** Async sections from Android 10, which added them; nothing before. */
    object Platform : TdlibStartTrace {
        override fun begin(section: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(section, COOKIE)
        }

        override fun end(section: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(section, COOKIE)
        }
    }

    companion object {
        /**
         * From [TdlibClient.start] until TDLib's client exists: the wait for the `Default` dispatcher, `libtdjni`'s
         * load, TDLib's log settings and the client's creation.
         */
        const val CREATE = "tdlib.create"

        /** From [TdlibClient.start] until the first parameters are built: mostly the database key's Keystore unwrap. */
        const val PARAMETERS = "tdlib.parameters"

        /** From sending the first parameters until TDLib reports the login step on its thread: opening its database. */
        const val OPEN = "tdlib.open"

        /** From TDLib reporting that step until [TdlibClient.authState] holds it: the wait for the update queue. */
        const val STEP_QUEUED = "tdlib.stepQueued"

        // Each section runs once per process, so one cookie keeps them apart.
        private const val COOKIE = 0
    }
}
