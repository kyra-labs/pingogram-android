package dev.kyralabs.pingogram.feature.chat.ui

import android.content.Intent
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The other apps' screens a found date or address opens ([calendarIntent], [mapsIntent]): the calendar's new event
 * filled in, an hour long or the whole day; a maps search for the address.
 */
@RunWith(RobolectricTestRunner::class)
class FoundTextIntentsTest {
    @Test
    fun `the calendar's new event has the title, description and an hour from the start`() {
        val intent = calendarIntent(ReplyEffect.AddToCalendar("Party!", "Party! Friday 19:00", START, isAllDay = false))

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Party!", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Party! Friday 19:00", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals(START, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(START + HOUR, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
    }

    @Test
    fun `without a start the calendar sets none, for the user to choose`() {
        val intent = calendarIntent(ReplyEffect.AddToCalendar("Party!", "Party! tomorrow", null, isAllDay = false))

        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME))
    }

    @Test
    fun `an address opens a maps search`() {
        val intent = mapsIntent("10 Downing Street, London")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:0,0?q=10%20Downing%20Street%2C%20London", intent.dataString)
    }

    private companion object {
        const val START = 1_763_665_200_000L
        const val HOUR = 3_600_000L
    }
}
