package dev.kyralabs.pingogram.startup

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * The async trace sections the main view model opens as it is created and closes as each state the splash screen
 * waits for becomes known ([StartupTrace.LOGIN_KNOWN], [StartupTrace.APPEARANCE_KNOWN], [StartupTrace.LOCK_KNOWN]).
 */
internal interface SplashStateTrace {
    fun begin(name: String)

    fun end(name: String)

    /** Traces nothing: for tests. */
    object None : SplashStateTrace {
        override fun begin(name: String) = Unit

        override fun end(name: String) = Unit
    }

    /** On the platform's trace (Android 10+), a cookie per view model: a flag check while no trace records the app. */
    class Platform : SplashStateTrace {
        private val cookie = cookies.incrementAndGet()

        override fun begin(name: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, cookie)
        }

        override fun end(name: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, cookie)
        }

        private companion object {
            val cookies = AtomicInteger()
        }
    }
}
