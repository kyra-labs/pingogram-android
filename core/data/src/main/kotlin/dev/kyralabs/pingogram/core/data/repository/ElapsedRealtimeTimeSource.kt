package dev.kyralabs.pingogram.core.data.repository

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/**
 * Time since boot, deep sleep included, so deadlines taken from TDLib's timeouts stay right after the device slept
 * (`TimeSource.Monotonic` stops while the CPU sleeps). `feature:auth` keeps its own copy for its countdowns.
 */
internal object ElapsedRealtimeTimeSource : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtimeNanos()
}
