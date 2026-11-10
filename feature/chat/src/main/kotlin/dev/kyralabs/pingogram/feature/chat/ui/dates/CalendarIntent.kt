package dev.kyralabs.pingogram.feature.chat.ui.dates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.provider.CalendarContract
import dev.kyralabs.pingogram.feature.chat.R

/**
 * The calendar app at a new event starting at [unixTime] (Unix time), for the user to save or not; no permission is
 * needed. The snackbar's text where no calendar app is on the device, else nothing.
 */
internal fun Context.addToCalendar(
    unixTime: Long,
    resources: Resources,
): String =
    try {
        startActivity(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, unixTime * MILLIS_PER_SECOND),
        )
        ""
    } catch (_: ActivityNotFoundException) {
        resources.getString(R.string.date_time_no_calendar)
    }

private const val MILLIS_PER_SECOND = 1_000L
