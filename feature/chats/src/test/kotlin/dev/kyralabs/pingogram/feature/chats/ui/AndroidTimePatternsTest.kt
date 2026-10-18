package dev.kyralabs.pingogram.feature.chats.ui

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** The platform's time patterns: the 12/24-hour system setting, and the best pattern for a skeleton by language. */
@RunWith(RobolectricTestRunner::class)
class AndroidTimePatternsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val patterns = AndroidTimePatterns(context)

    @Test
    fun followsThe24HourSystemSetting() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertTrue(patterns.is24HourClock())

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertFalse(patterns.is24HourClock())
    }

    @Test
    fun dayAndMonthFollowTheLanguage() {
        assertEquals("MMM d", patterns.bestPattern(Locale.US, "MMMd"))
        assertEquals("d MMM", patterns.bestPattern(Locale.UK, "MMMd"))
    }

    @Test
    fun timesFollowTheClock() {
        assertEquals("HH:mm", patterns.bestPattern(Locale.US, "Hm"))
        assertEquals("h:mm a", patterns.bestPattern(Locale.US, "hm"))
    }
}
