package dev.kyralabs.pingogram.feature.auth.ui

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/**
 * Time since boot, deep sleep included: a countdown (flood wait, code resend) stays right after the device slept,
 * unlike `TimeSource.Monotonic`, which stops while the CPU sleeps.
 */
internal object ElapsedRealtimeTimeSource : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtimeNanos()
}
