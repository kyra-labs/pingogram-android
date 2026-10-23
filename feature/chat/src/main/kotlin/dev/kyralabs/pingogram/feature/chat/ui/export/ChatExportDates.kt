package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// The day an export starts from, picked in a DatePickerDialog.

/** The day messages are exported from, up to today, in a [DatePickerDialog]. */
@Composable
internal fun SinceDialog(
    initialDay: Long?,
    onDismiss: () -> Unit,
    onPick: (day: Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = (initialDay?.let(LocalDate::ofEpochDay) ?: today).utcMillis(),
            yearRange = MIN_YEAR..today.year,
            selectableDates = remember(today) { UpToDay(today) },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utcDate(it).toEpochDay()) } },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.chat_export_date_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_export_date_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.chat_export_since_dialog),
                    modifier =
                        Modifier.padding(
                            start = MaterialTheme.spacing.extraLarge,
                            end = MaterialTheme.spacing.medium,
                            top = MaterialTheme.spacing.large,
                        ),
                )
            },
        )
    }
}

/** The days a date picker offers: none after [last]. */
internal class UpToDay(
    private val last: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcDate(utcTimeMillis).isAfter(last)

    override fun isSelectableYear(year: Int): Boolean = year <= last.year
}

/** Midnight UTC of this day, as a date picker selects it. */
internal fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day a date picker's [utcMillis] (midnight UTC of the picked day) stands for. */
internal fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** The first year the date picker offers: the year Telegram started. */
internal const val MIN_YEAR = 2013
