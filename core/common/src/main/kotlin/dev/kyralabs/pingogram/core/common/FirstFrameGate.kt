package dev.kyralabs.pingogram.core.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Holds back start-up work that can wait (binder calls that clean up or re-post) until the app drew its first frame,
 * so it does not compete with the work the first frame waits for (`docs/PERFORMANCE.md` §2). A process that shows no
 * activity, such as one started for a push, goes ahead after [FALLBACK].
 */
@Singleton
class FirstFrameGate
    @Inject
    constructor() {
        private val drawn = CompletableDeferred<Unit>()

        /** The first activity drew its first frame; later calls do nothing. */
        fun open() {
            drawn.complete(Unit)
        }

        /** Suspends until [open] was called, or for at most [FALLBACK]; the gate stays open after either. */
        suspend fun await() {
            if (withTimeoutOrNull(FALLBACK) { drawn.await() } == null) open()
        }

        companion object {
            /** Longer than a cold start to the first frame on a slow phone (`docs/PERFORMANCE.md` §2). */
            val FALLBACK: Duration = 3.seconds
        }
    }
