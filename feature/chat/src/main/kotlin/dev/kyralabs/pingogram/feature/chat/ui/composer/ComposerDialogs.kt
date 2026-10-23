package dev.kyralabs.pingogram.feature.chat.ui.composer

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.theme.PingogramSpacing
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Asks for the address the selected text links to (`docs/DESIGN_SYSTEM.md` §6: a custom dialog is a
 * [BasicAlertDialog]): [currentUrl], if the selection is within a link already, can be edited or removed (an empty
 * address for [onConfirm]).
 */
@Composable
internal fun LinkDialog(
    currentUrl: String?,
    onDismiss: () -> Unit,
    onConfirm: (url: String) -> Unit,
) {
    val address = rememberTextFieldState(currentUrl.orEmpty())
    val focus = remember { FocusRequester() }
    val spacing = MaterialTheme.spacing
    val save = { if (address.text.isNotBlank()) onConfirm(address.text.toString()) }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(spacing.large),
            ) {
                Text(
                    text =
                        stringResource(
                            if (currentUrl == null) R.string.composer_link_title else R.string.composer_link_edit_title,
                        ),
                    color = AlertDialogDefaults.titleContentColor,
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    state = address,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(stringResource(R.string.composer_link_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    onKeyboardAction = { save() },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                ) {
                    if (currentUrl != null) {
                        TextButton(onClick = { onConfirm("") }) { Text(stringResource(R.string.composer_link_remove)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_cancel)) }
                    TextButton(onClick = save, enabled = address.text.isNotBlank()) {
                        Text(stringResource(R.string.composer_link_save))
                    }
                }
            }
        }
    }
    LaunchedEffect(focus) { focus.requestFocus() }
}

/**
 * Picks when a scheduled message is sent (`docs/DESIGN_SYSTEM.md` §6: a [DatePicker], then a [TimePicker], in a
 * [DatePickerDialog]): a day from today up to a year ahead, as Telegram allows, then a time, on the device's 12- or
 * 24-hour clock, an hour from [now] at first. A time that is not in the future asks for another. [onSchedule] gets the
 * moment in the device's time zone.
 */
@Composable
internal fun ScheduleDialog(
    now: () -> Instant,
    onDismiss: () -> Unit,
    onSchedule: (Instant) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val opened = remember { now().atZone(zone) }
    val today = opened.toLocalDate()
    val lastDay = today.plusDays(MAX_DAYS_AHEAD)
    // An hour from now at first, on the day that is then (tomorrow, late in the evening).
    val inAnHour = opened.plusHours(1)
    val dateState =
        rememberDatePickerState(
            initialSelectedDateMillis = inAnHour.toLocalDate().utcMillis(),
            yearRange = today.year..lastDay.year,
            selectableDates = remember(today) { ScheduleDates(today, lastDay) },
        )
    val timeState =
        rememberTimePickerState(
            initialHour = inAnHour.hour,
            initialMinute = inAnHour.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var inThePast by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(timeState.hour, timeState.minute) { inThePast = false }
    val confirm = {
        val at =
            dateState.selectedDateMillis
                ?.let(::utcDate)
                ?.atTime(timeState.hour, timeState.minute)
                ?.atZone(zone)
                ?.toInstant()
        when {
            at == null -> Unit
            !pickingTime -> pickingTime = true
            at.isAfter(now()) -> onSchedule(at)
            else -> inThePast = true
        }
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = confirm, enabled = dateState.selectedDateMillis != null) {
                Text(
                    stringResource(
                        if (pickingTime) R.string.composer_schedule_confirm else R.string.composer_schedule_next,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (pickingTime) pickingTime = false else onDismiss() }) {
                Text(stringResource(if (pickingTime) R.string.composer_schedule_back else R.string.composer_cancel))
            }
        },
    ) {
        if (pickingTime) TimeStep(timeState, inThePast) else DateStep(dateState)
    }
}

/** The first step of [ScheduleDialog]: the day. */
@Composable
private fun DateStep(state: DatePickerState) {
    val spacing = MaterialTheme.spacing
    DatePicker(
        state = state,
        title = { Text(stringResource(R.string.composer_schedule), Modifier.titlePadding(spacing)) },
    )
}

/** The second step of [ScheduleDialog]: the time, and why it cannot be [inThePast]. */
@Composable
private fun TimeStep(
    state: TimePickerState,
    inThePast: Boolean,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        Text(
            text = stringResource(R.string.composer_schedule),
            modifier = Modifier.align(Alignment.Start).titlePadding(spacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        TimePicker(state = state, modifier = Modifier.padding(horizontal = spacing.extraLarge))
        if (inThePast) {
            Text(
                text = stringResource(R.string.composer_schedule_past),
                modifier = Modifier.padding(horizontal = spacing.extraLarge),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Where a date picker's title sits, as `DatePickerDefaults.DatePickerTitle` places it. */
private fun Modifier.titlePadding(spacing: PingogramSpacing): Modifier =
    padding(start = spacing.extraLarge, end = spacing.medium, top = spacing.large)

/** Midnight UTC of this day, as a date picker selects it. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The days a message can be scheduled for: from [first] to [last]. */
private class ScheduleDates(
    private val first: LocalDate,
    private val last: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcDate(utcTimeMillis).let { !it.isBefore(first) && !it.isAfter(last) }

    override fun isSelectableYear(year: Int): Boolean = year in first.year..last.year
}

/** The day a date picker's [utcMillis] (midnight UTC of the picked day) stands for. */
private fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** How far ahead a message can be scheduled: Telegram allows up to 367 days (td_api.tl). */
private const val MAX_DAYS_AHEAD = 365L

/** [LinkDialog] for the [selection] of [text]: its link, which it sets or removes; [onDone] once closed. */
@Composable
internal fun TextLinkDialog(
    text: ComposerText,
    selection: TextRange,
    onDone: () -> Unit,
) {
    LinkDialog(
        currentUrl = text.linkAt(selection),
        onDismiss = onDone,
        onConfirm = { url ->
            text.setLink(url, selection)
            onDone()
        },
    )
}
