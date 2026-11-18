package dev.kyralabs.pingogram.feature.chat.ui

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.net.toUri
import java.time.Duration

// The other apps a message's found date or address opens in (`REQUIREMENTS.md` FR-X3): the calendar's new event, which
// the user saves there, and a maps app. Neither needs a permission: they are the other app's own screens.

/**
 * The calendar's new event for [effect] (`CalendarContract` insert): its title and description, and where it has a
 * start, an hour from it, or the whole day.
 */
internal fun calendarIntent(effect: ReplyEffect.AddToCalendar): Intent =
    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, effect.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, effect.description)
        .apply {
            val start = effect.startMillis ?: return@apply
            val length = if (effect.isAllDay) Duration.ofDays(1) else Duration.ofHours(1)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + length.toMillis())
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, effect.isAllDay)
        }

/** A maps app searching for [address] (a `geo:` link with a query, no coordinates). */
internal fun mapsIntent(address: String): Intent =
    Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())
