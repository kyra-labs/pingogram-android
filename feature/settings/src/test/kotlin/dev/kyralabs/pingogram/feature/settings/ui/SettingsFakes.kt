package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** A clock at [now] in UTC. */
class FakeWallClock(
    private val now: Instant,
) : WallClock {
    override val minutes = MutableStateFlow(now)

    override fun now(): Instant = now

    override fun zone(): ZoneId = ZoneOffset.UTC
}

/** Fixed English patterns, as the platform gives them for `Locale.US`; [uses24HourClock] is the system setting. */
class FakeTimePatterns(
    private val uses24HourClock: Boolean = false,
) : TimePatterns {
    override fun is24HourClock(): Boolean = uses24HourClock

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = PATTERNS[skeleton] ?: error("No pattern for $skeleton")

    private companion object {
        val PATTERNS =
            mapOf(
                "Hm" to "HH:mm",
                "hm" to "h:mm a",
                "MMMd" to "MMM d",
                "yMd" to "M/d/y",
                "yMMMdhm" to "MMM d, y, h:mm a",
                "yMMMdHm" to "MMM d, y, HH:mm",
            )
    }
}

/** The time of the settings tests: Sunday, October 18, 2026, 9:41:30 AM UTC. */
val TEST_NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")

/** [SettingsTimeFormats] at [now] in UTC, in English, on the 12-hour clock unless [uses24HourClock]. */
fun testTimeFormats(
    now: Instant = TEST_NOW,
    uses24HourClock: Boolean = false,
) = SettingsTimeFormats(FakeWallClock(now), FakeTimePatterns(uses24HourClock)) { Locale.US }

/** [text] with the narrow no-break spaces some JDKs put before AM and PM as plain spaces. */
fun plainSpaces(text: String) = text.replace(' ', ' ').replace(' ', ' ')
