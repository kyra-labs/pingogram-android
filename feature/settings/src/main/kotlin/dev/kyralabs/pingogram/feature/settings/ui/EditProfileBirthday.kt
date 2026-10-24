package dev.kyralabs.pingogram.feature.settings.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Edit profile's birthday (FR-S1, Account): the date, who may see it, and the dialog that picks it.

/**
 * The [birthdate] in a row ending in an "Add" or "Change" [TextButton] that opens [BirthdateDialog] (the dialog's
 * "Remove" removes it), then, with a birthday, "Who can see it" with [privacy]'s choice, which opens the privacy
 * setting ([onOpenPrivacy]). The change is saved with the rest of the page ([onPick] only edits it).
 */
@Composable
internal fun BirthdaySection(
    birthdate: Birthdate?,
    privacy: PrivacyRules?,
    enabled: Boolean,
    onPick: (Birthdate?) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FieldGroup(title = stringResource(R.string.settings_section_profile_birthday)) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            val count = if (birthdate == null) 1 else 2
            BirthdateRow(birthdate, enabled, count, onEdit = { picking = true })
            if (birthdate != null) {
                SettingsRow(
                    title = stringResource(R.string.settings_edit_profile_birthday_privacy),
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
                    supportingContent = privacy?.let { { Text(privacySummary(it)) } },
                    onClick = onOpenPrivacy,
                )
            }
        }
    }
    if (picking) {
        BirthdateDialog(
            birthdate = birthdate,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onPick(it)
            },
        )
    }
}

/**
 * The [birthdate] ("No birthday" without one) with "Add" or "Change" ([onEdit]) at the end; the first of [count] rows.
 */
@Composable
private fun BirthdateRow(
    birthdate: Birthdate?,
    enabled: Boolean,
    count: Int,
    onEdit: () -> Unit,
) {
    val text = birthdate?.let { birthdateText(it) }
    val label =
        if (text == null) {
            stringResource(R.string.settings_edit_profile_birthday_add_label)
        } else {
            stringResource(R.string.settings_edit_profile_birthday_change_label, text.bidiIsolated())
        }
    val action =
        if (text ==
            null
        ) {
            R.string.settings_edit_profile_birthday_add
        } else {
            R.string.settings_edit_profile_birthday_change
        }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
        colors = settingsRowColors(),
        trailingContent = {
            TextButton(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(action))
            }
        },
    ) {
        if (text == null) {
            Text(
                stringResource(R.string.settings_edit_profile_birthday_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text, style = MaterialTheme.typography.bodyLarge.userContent)
        }
    }
}

/**
 * Picks a birthday: a [DatePicker] in a [DatePickerDialog] (no day after today) with "Show the year" in its title
 * ([BirthdateTitle]), as Telegram keeps a birthday with or without its year. A birthday without one opens on [YEARLESS]
 * (a leap year, so February 29 stays), and the headline then leaves the year out. "Remove" (with a birthday) gives
 * [onPick] null; "Done" the date picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdateDialog(
    birthdate: Birthdate?,
    onDismiss: () -> Unit,
    onPick: (Birthdate?) -> Unit,
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
                Text(stringResource(R.string.settings_edit_profile_birthday_done))
            }
        },
        dismissButton = {
            BirthdateDismissButtons(
                canRemove = birthdate != null,
                onRemove = { onPick(null) },
                onDismiss,
            )
        },
    ) {
        DatePicker(
            state = state,
            title = { BirthdateTitle(showsYear = showsYear, onShowYearChange = { showsYear = it }) },
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

/** "Remove" where there is a birthday to remove, then "Cancel". */
@Composable
private fun BirthdateDismissButtons(
    canRemove: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row {
        if (canRemove) {
            TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_edit_profile_birthday_remove))
            }
        }
        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_edit_profile_birthday_cancel))
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
 * The picker's title, "Birthday", with "Show the year" and its switch at the end, where Material's title sits
 * (`DatePickerDefaults.DatePickerTitle`'s padding): the dialog is as tall as the picker, so nothing fits below it. The
 * switch's part is one toggleable for screen readers and turns with its haptic.
 */
@Composable
private fun BirthdateTitle(
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
        Text(stringResource(R.string.settings_section_profile_birthday), modifier = Modifier.weight(1f))
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
            Text(stringResource(R.string.settings_edit_profile_birthday_show_year))
            CheckSwitch(checked = showsYear)
        }
    }
}

/** [birthdate] in the language's words: "December 10, 1815", or "December 10" without its year. */
@Composable
private fun birthdateText(birthdate: Birthdate): String {
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
