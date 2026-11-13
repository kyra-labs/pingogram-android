package dev.kyralabs.pingogram.core.ui.time

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.theme.spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Picking a birthday (docs/DESIGN_SYSTEM.md §6 "Birthday (Edit profile)"), shared by Edit profile and by suggesting a
// birthday to a contact from their profile ("Birthday suggestions").

/**
 * Picks a birthday: a [DatePicker] in a [DatePickerDialog] (no day after today) with [title] and "Show the year" in its
 * title row ([BirthdateTitle]), as Telegram keeps a birthday with or without its year. A birthday without one opens on
 * [YEARLESS] (a leap year, so February 29 stays), and the headline then leaves the year out. [onRemove], where given
 * and there is a [birthdate], adds "Remove"; [confirm] ("Done" by default) gives [onPick] the date picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdateDialog(
    birthdate: Birthdate?,
    onDismiss: () -> Unit,
    onPick: (Birthdate) -> Unit,
    title: String = stringResource(R.string.birthday_title),
    confirm: String = stringResource(R.string.birthday_done),
    onRemove: (() -> Unit)? = null,
) {
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val initial = remember { birthdate?.let { LocalDate.of(it.year ?: YEARLESS, it.month, it.day) } }
    var showsYear by rememberSaveable { mutableStateOf(birthdate == null || birthdate.year != null) }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial?.utcMillis(),
            initialDisplayedMonthMillis = (initial ?: today.minusYears(DEFAULT_AGE_YEARS)).utcMillis(),
            yearRange = FIRST_YEAR..today.year,
            selectableDates = remember(today) { UpTo(today) },
        )
    val selected = state.selectedDateMillis?.let(::utcDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    selected?.let { onPick(Birthdate(it.dayOfMonth, it.monthValue, it.year.takeIf { showsYear })) }
                },
                enabled = selected != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            BirthdateDismissButtons(onRemove = onRemove?.takeIf { birthdate != null }, onDismiss)
        },
    ) {
        DatePicker(
            state = state,
            title = { BirthdateTitle(title, showsYear = showsYear, onShowYearChange = { showsYear = it }) },
            headline =
                if (showsYear || selected == null) {
                    {
                        DatePickerDefaults.DatePickerHeadline(
                            selectedDateMillis = state.selectedDateMillis,
                            displayMode = state.displayMode,
                            dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                            modifier = Modifier.padding(headlinePadding()),
                        )
                    }
                } else {
                    { YearlessHeadline(selected) }
                },
        )
    }
}

/** "Remove" where there is a birthday to remove ([onRemove]), then "Cancel". */
@Composable
private fun BirthdateDismissButtons(
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row {
        if (onRemove != null) {
            TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.birthday_remove))
            }
        }
        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.birthday_cancel))
        }
    }
}

/** The picked [day] without its year, "February 29", where Material's headline sits. */
@Composable
private fun YearlessHeadline(day: LocalDate) {
    Text(
        text = birthdateText(Birthdate(day.dayOfMonth, day.monthValue, year = null)),
        modifier = Modifier.padding(headlinePadding()),
        maxLines = 1,
    )
}

/** Where `DatePickerDefaults.DatePickerHeadline` places its headline in the picker's own layout. */
@Composable
private fun headlinePadding(): PaddingValues {
    val spacing = MaterialTheme.spacing
    return PaddingValues(start = spacing.extraLarge, end = spacing.medium, bottom = spacing.medium)
}

/**
 * The picker's [title] with "Show the year" and its switch at the end, where Material's title sits
 * (`DatePickerDefaults.DatePickerTitle`'s padding): the dialog is as tall as the picker, so nothing fits below it. The
 * switch's part is one toggleable for screen readers and turns with its haptic.
 */
@Composable
private fun BirthdateTitle(
    title: String,
    showsYear: Boolean,
    onShowYearChange: (Boolean) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
                start = spacing.extraLarge,
                end = spacing.medium,
                top = spacing.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Row(
            modifier =
                Modifier
                    .toggleable(value = showsYear, role = Role.Switch) {
                        onShowYearChange(it)
                        haptics.performToggle(it)
                    }.padding(start = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.birthday_show_year))
            CheckSwitch(checked = showsYear)
        }
    }
}

/** [birthdate] in the language's words: "December 10, 1815", or "December 10" without its year. */
@Composable
fun birthdateText(birthdate: Birthdate): String {
    val locale = LocalLocale.current.platformLocale
    val skeleton = if (birthdate.year == null) "MMMMd" else "yMMMMd"
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return formatter.format(LocalDate.of(birthdate.year ?: YEARLESS, birthdate.month, birthdate.day))
}

/** The days a birthday can be: up to [last], today. */
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

/** The year a birthday without one is shown and picked in: a leap year, so February 29 can be picked. */
private const val YEARLESS = 2000

/** The first year the picker offers. */
private const val FIRST_YEAR = 1900

/** How many years back the picker opens for a new birthday, rather than at today. */
private const val DEFAULT_AGE_YEARS = 25L
