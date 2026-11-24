package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import android.content.ComponentName
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.ZoneOffset

/** "Add to calendar" (`REQUIREMENTS.md` FR-X18): the calendar app's new event, all day and yearly, from the date. */
@RunWith(RobolectricTestRunner::class)
class BirthdaysCalendarTest {
    @Test
    fun `hands the calendar app a yearly all-day event titled for the birthday`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager)
            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
        // The page's own context, an activity, as the route uses it.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val date = LocalDate.of(2026, 11, 24)

        assertTrue(activity.addYearlyEvent("Ada's birthday", date))

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Ada's birthday", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals("FREQ=YEARLY", intent.getStringExtra(CalendarContract.Events.RRULE))
        assertEquals(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0),
        )
    }
}
