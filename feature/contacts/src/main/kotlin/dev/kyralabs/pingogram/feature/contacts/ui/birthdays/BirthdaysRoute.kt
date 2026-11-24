package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.contacts.R
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Birthdays page (`REQUIREMENTS.md` FR-X18), pushed above the Contacts tab: hoists its [BirthdaysViewModel]; a row
 * opens the chat with the contact through [onOpenChat] (a private chat's id is its user's), "Add to calendar" hands the
 * calendar app a yearly all-day event, and [onBack] leaves. A reminder that cannot be saved, or a phone without a
 * calendar app, says so in a snackbar.
 */
@Composable
internal fun BirthdaysRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BirthdaysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BirthdaysEffect.OpenChat -> {
                    currentOnOpenChat(effect.userId)
                }

                is BirthdaysEffect.AddToCalendar -> {
                    val title = resources.getString(R.string.birthdays_calendar_event, effect.name.resolve(resources))
                    val date = LocalDate.of(effect.year, effect.month, effect.day)
                    if (!context.addYearlyEvent(title, date)) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.birthdays_no_calendar))
                    }
                }

                is BirthdaysEffect.ReminderFailed -> {
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.birthdays_reminder_failed,
                            errorMessage(resources, effect.error, showDetails),
                        ),
                    )
                }
            }
        }
    }
    BirthdaysScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Opens the calendar app's new event, all day on [date] and every year after, titled [title]; false without a calendar
 * app. Nothing is written by Pingogram: the user saves it there. Internal for its test.
 */
internal fun Context.addYearlyEvent(
    title: String,
    date: LocalDate,
): Boolean {
    // All-day events are given in UTC midnights, as the calendar provider keeps them.
    val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
