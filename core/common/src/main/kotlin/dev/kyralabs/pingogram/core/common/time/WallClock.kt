package dev.kyralabs.pingogram.core.common.time

import dev.kyralabs.pingogram.core.common.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The time of day as the user's device tells it, for what ViewModels show relative to now: message times, online dots
 * that expire (`docs/PERFORMANCE.md` §4). ViewModels take it injected, so tests set the time; `:app` binds
 * [SystemWallClock].
 */
interface WallClock {
    /** The current time. */
    fun now(): Instant

    /** The user's time zone right now; it changes when the user travels or changes the setting. */
    fun zone(): ZoneId

    /**
     * The start of the current minute, then of each next minute as the wall clock reaches it, while collected. One
     * timer serves every collector, and it runs only while someone collects: a screen in the background stops it, so
     * it never wakes the device. A collector that starts gets the current minute at once, never an old one.
     */
    val minutes: SharedFlow<Instant>
}

/**
 * [WallClock] on the system clock. [minutes] waits for each minute boundary of [clock], measured anew after every tick,
 * so a changed system time or a late wake-up is corrected at the next tick at the latest.
 */
@Singleton
class SystemWallClock internal constructor(
    scope: CoroutineScope,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : WallClock {
    @Inject
    constructor(
        @ApplicationScope scope: CoroutineScope,
    ) : this(scope, Clock.systemUTC(), ZoneId::systemDefault)

    override fun now(): Instant = clock.instant()

    override fun zone(): ZoneId = zone.invoke()

    // A shared flow rather than stateIn: a StateFlow would replay the minute it had when its last collector left.
    override val minutes: SharedFlow<Instant> =
        flow {
            while (true) {
                val now = now()
                val minute = now.truncatedTo(ChronoUnit.MINUTES)
                emit(minute)
                delay(millisUntil(now, minute.plus(1, ChronoUnit.MINUTES)))
            }
        }
            // A wake-up that came early repeats the minute.
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
}

/** Whole milliseconds from [now] to [next], rounded up, so a delay of that long never ends before [next]. */
private fun millisUntil(
    now: Instant,
    next: Instant,
): Long = (Duration.between(now, next).toNanos() + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L
