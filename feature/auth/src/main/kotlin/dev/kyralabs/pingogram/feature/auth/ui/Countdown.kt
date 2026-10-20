package dev.kyralabs.pingogram.feature.auth.ui

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Whole seconds until this deadline, rounded up so a countdown never shows less than is left; 0 once it passed. */
internal fun ComparableTimeMark.secondsLeft(): Long = timeLeft().wholeSecondsRoundedUp()

/** The time until this deadline; zero or negative once it passed. */
internal fun ComparableTimeMark.timeLeft(): Duration = -elapsedNow()

internal fun Duration.wholeSecondsRoundedUp(): Long =
    if (isPositive()) (inWholeNanoseconds + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND else 0

private val NANOS_PER_SECOND = 1.seconds.inWholeNanoseconds

/** The part of a countdown still to run, for its progress: 1 at the start, 0 at the end, 1 while [total] is unknown. */
internal fun countdownFraction(
    secondsLeft: Long,
    total: Long,
): Float = if (total <= 0) 1f else (secondsLeft.toFloat() / total).coerceIn(0f, 1f)
