package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
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
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks the day to jump to (`docs/DESIGN_SYSTEM.md` §6, "In-chat search"): a [DatePicker] in a [DatePickerDialog], as
 * scheduling a message does, today selected at first and no day after it, since no message is sent later. [onPick]
 * gets the day as days since 1970-01-01, in the device's time zone.
 */
@Composable
internal fun SearchDateDialog(
    onDismiss: () -> Unit,
    onPick: (epochDay: Long) -> Unit,
    today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) {
    val day = remember { today() }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = day.utcMillis(),
            yearRange = FIRST_YEAR..day.year,
            selectableDates = remember(day) { UpTo(day) },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utcDate(it).toEpochDay()) } },
                enabled = state.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.search_date_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.search_date_cancel))
            }
        },
    ) {
        val spacing = MaterialTheme.spacing
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.search_date_title),
                    // Where DatePickerDefaults.DatePickerTitle places its title.
                    modifier = Modifier.padding(start = spacing.extraLarge, end = spacing.medium, top = spacing.large),
                )
            },
        )
    }
}

/** The days a chat can have messages of: up to [last], today. */
private class UpTo(
    private val last: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcDate(utcTimeMillis).isAfter(last)

    override fun isSelectableYear(year: Int): Boolean = year <= last.year
}

/** Midnight UTC of this day, as a date picker selects it. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day a date picker's [utcMillis] (midnight UTC of the picked day) stands for. */
private fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** The first year the picker offers: Telegram started in 2013. */
private const val FIRST_YEAR = 2013
