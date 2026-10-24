package dev.kyralabs.pingogram.core.ui.time

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Whole seconds until this deadline, rounded up so a countdown never shows less than is left; 0 once it passed. */
fun ComparableTimeMark.secondsLeft(): Long = timeLeft().wholeSecondsRoundedUp()

/** The time until this deadline; zero or negative once it passed. */
fun ComparableTimeMark.timeLeft(): Duration = -elapsedNow()

fun Duration.wholeSecondsRoundedUp(): Long =
    if (isPositive()) (inWholeNanoseconds + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND else 0

private val NANOS_PER_SECOND = 1.seconds.inWholeNanoseconds

/** The part of a countdown still to run, for its progress: 1 at the start, 0 at the end, 1 while [total] is unknown. */
fun countdownFraction(
    secondsLeft: Long,
    total: Long,
): Float = if (total <= 0) 1f else (secondsLeft.toFloat() / total).coerceIn(0f, 1f)

/**
 * Whole seconds of a countdown as screen readers hear them: under a minute rounded up to 15-second steps, so a live
 * region speaks at most every 15 seconds; `durationText` already rounds longer times up to minutes and hours. Rounding
 * up never claims less time than is left.
 */
fun announcedCountdownSeconds(seconds: Long): Long =
    if (seconds in 1 until SECONDS_PER_MINUTE) {
        (seconds + ANNOUNCEMENT_STEP_SECONDS - 1) / ANNOUNCEMENT_STEP_SECONDS * ANNOUNCEMENT_STEP_SECONDS
    } else {
        seconds
    }

private const val SECONDS_PER_MINUTE = 60L
private const val ANNOUNCEMENT_STEP_SECONDS = 15L
