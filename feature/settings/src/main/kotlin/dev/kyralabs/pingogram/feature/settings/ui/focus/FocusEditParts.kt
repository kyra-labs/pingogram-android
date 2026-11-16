package dev.kyralabs.pingogram.feature.settings.ui.focus

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.shape
import dev.kyralabs.pingogram.core.ui.component.symbol
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.collections.immutable.persistentListOf
import java.time.format.TextStyle
import java.util.Locale

// The parts of the Focus profile editor (FocusEditScreen), board 31.

private const val MINUTES_PER_HOUR = 60

/** The name in the rounded tonal field, user content, or why "Save" was refused without one. */
@Composable
internal fun FocusNameField(
    state: FocusEditUiState,
    draft: FocusDraft,
    onEvent: (FocusEditEvent) -> Unit,
) {
    TextField(
        value = draft.name,
        onValueChange = { onEvent(FocusEditEvent.Name(it)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
        label = { Text(stringResource(R.string.settings_focus_name)) },
        supportingText =
            if (state.nameMissing) {
                { Text(stringResource(R.string.settings_focus_name_required)) }
            } else {
                null
            },
        isError = state.nameMissing,
        singleLine = true,
        textStyle = LocalTextStyle.current.userContent,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** The profile's icon, one choice of six symbols, each on its shape while chosen; radio buttons for screen readers. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FocusIconGroup(
    draft: FocusDraft,
    enabled: Boolean,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Every symbol on its own shape (board 31b), the chosen one filled in `primary`.
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    SettingsGroup(title = stringResource(R.string.settings_focus_icon)) {
        item { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.padding(MaterialTheme.spacing.small).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                    FocusIcon.entries.forEach { icon ->
                        val label = stringResource(icon.label())
                        val checked = icon == draft.icon
                        IconToggleButton(
                            checked = checked,
                            onCheckedChange = {
                                if (!checked) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onEvent(FocusEditEvent.Icon(icon))
                                }
                            },
                            enabled = enabled,
                            shapes =
                                IconButtonDefaults.toggleableShapes(
                                    shape = icon.shape(),
                                    checkedShape = icon.shape(),
                                ),
                            colors = colors,
                            modifier =
                                Modifier.semantics {
                                    role = Role.RadioButton
                                    selected = checked
                                    contentDescription = label
                                },
                        ) {
                            Icon(
                                painterResource(icon.symbol()),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Allow folders": the user's chat folders as filter chips, the allowed ones selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusFoldersGroup(
    state: FocusEditUiState,
    draft: FocusDraft,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val folders = state.folders ?: return
    val haptics = LocalHapticFeedback.current
    SettingsGroup(
        title = stringResource(R.string.settings_focus_allow_folders),
        footer = stringResource(R.string.settings_focus_allow_folders_footer),
    ) {
        item { shapes ->
            SettingsSegment(shapes) {
                if (folders.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_focus_no_folders),
                        modifier = Modifier.padding(MaterialTheme.spacing.large),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        folders.forEach { folder ->
                            val allowed = folder.id in draft.folderIds
                            FilterChip(
                                selected = allowed,
                                onClick = {
                                    haptics.performToggle(!allowed)
                                    onEvent(FocusEditEvent.Folder(folder.id, !allowed))
                                },
                                label = { Text(folder.title, style = LocalTextStyle.current.userContent) },
                                enabled = !state.isBusy,
                                shapes = FilterChipDefaults.shapes(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Turns on": on a schedule or by hand (connected buttons), then, on a schedule, the days as toggle buttons in the
 * language's week (checkboxes with the days' full names for screen readers), "From" and "To", each opening the time
 * picker; an end at or before the start reads "next day".
 */
@Composable
internal fun FocusScheduleGroup(
    draft: FocusDraft,
    enabled: Boolean,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val locale = appLocale()
    SettingsGroup(title = stringResource(R.string.settings_focus_turns_on)) {
        item(key = MODE) { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = ScheduleModes,
                    selected = draft.scheduled,
                    label = {
                        stringResource(
                            if (it) R.string.settings_focus_on_schedule else R.string.settings_focus_by_hand_choice,
                        )
                    },
                    onSelect = { onEvent(FocusEditEvent.Scheduled(it)) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.small),
                    enabled = enabled,
                )
            }
        }
        if (draft.scheduled) {
            item(key = DAYS) { shapes -> FocusDays(draft, enabled, shapes, onEvent) }
            item(key = TIMES) { shapes -> FocusTimes(draft, enabled, shapes, locale, onEvent) }
        }
    }
}

/** The days of a schedule as toggle buttons in the language's week; checkboxes named in full for screen readers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusDays(
    draft: FocusDraft,
    enabled: Boolean,
    shapes: ListItemShapes,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val locale = appLocale()
    val haptics = LocalHapticFeedback.current
    // One row of the days' narrow names sharing the width (board 31b); from large font scales their short names wrap.
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    SettingsSegment(shapes) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            maxItemsInEachRow = if (large) Int.MAX_VALUE else DAYS_IN_WEEK,
        ) {
            weekDays(locale).forEach { day ->
                val name = day.getDisplayName(TextStyle.FULL, locale)
                ToggleButton(
                    checked = day in draft.days,
                    onCheckedChange = {
                        haptics.performToggle(it)
                        onEvent(FocusEditEvent.Day(day, it))
                    },
                    enabled = enabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier =
                        (if (large) Modifier else Modifier.weight(1f)).semantics {
                            role = Role.Checkbox
                            contentDescription = name
                        },
                ) {
                    Text(day.getDisplayName(if (large) TextStyle.SHORT else TextStyle.NARROW, locale), maxLines = 1)
                }
            }
        }
    }
}

/**
 * "From" and "To" as two tonal tiles side by side, each its label over the time and opening the time picker (board
 * 31b); stacked from large font scales. An end at or before the start reads "next day".
 */
@Composable
private fun FocusTimes(
    draft: FocusDraft,
    enabled: Boolean,
    shapes: ListItemShapes,
    locale: Locale,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val end = focusTime(draft.endMinute, locale)
    val endText =
        if (draft.endMinute <=
            draft.startMinute
        ) {
            stringResource(R.string.settings_focus_next_day, end)
        } else {
            end
        }
    val from: @Composable (Modifier) -> Unit = { modifier ->
        TimeTile(R.string.settings_focus_from, focusTime(draft.startMinute, locale), enabled, modifier) {
            onEvent(FocusEditEvent.Pick(FocusTimeEnd.Start))
        }
    }
    val to: @Composable (Modifier) -> Unit = { modifier ->
        TimeTile(
            R.string.settings_focus_to,
            endText,
            enabled,
            modifier,
        ) { onEvent(FocusEditEvent.Pick(FocusTimeEnd.End)) }
    }
    SettingsSegment(shapes) {
        val spacing = MaterialTheme.spacing
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
            Column(Modifier.padding(spacing.small), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                from(Modifier.fillMaxWidth())
                to(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(spacing.small), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                from(Modifier.weight(1f))
                to(Modifier.weight(1f))
            }
        }
    }
}

/** One end of the schedule: its [label] over its [time], a tonal tile that opens the time picker; one button. */
@Composable
private fun TimeTile(
    @StringRes label: Int,
    time: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(time, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** "Allow calls from contacts", a switch row. */
@Composable
internal fun FocusCallsGroup(
    draft: FocusDraft,
    onEvent: (FocusEditEvent) -> Unit,
) {
    SettingsGroup {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_focus_calls),
                summary = stringResource(R.string.settings_focus_calls_summary),
                checked = draft.allowsCalls,
                shapes = shapes,
                onCheckedChange = { onEvent(FocusEditEvent.Calls(it)) },
            )
        }
    }
}

/** Material's time picker for one end of the schedule, on the device's 12- or 24-hour clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusTimeDialog(
    end: FocusTimeEnd,
    minute: Int,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = minute / MINUTES_PER_HOUR,
            initialMinute = minute % MINUTES_PER_HOUR,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    TimePickerDialog(
        onDismissRequest = { onEvent(FocusEditEvent.Pick(null)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(FocusEditEvent.Time(end, state.hour * MINUTES_PER_HOUR + state.minute)) },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.settings_focus_time_done)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FocusEditEvent.Pick(null)) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_dialog_dismiss))
            }
        },
        title = {
            Text(
                stringResource(
                    if (end == FocusTimeEnd.Start) R.string.settings_focus_from else R.string.settings_focus_to,
                ),
            )
        },
    ) {
        TimePicker(state = state)
    }
}

private val ScheduleModes = persistentListOf(true, false)
private const val MODE = "mode"
private const val DAYS = "days"
private const val TIMES = "times"

/** From this font scale on, the days wrap and the times stack. */
private const val LARGE_FONT_SCALE = 1.5f

private const val DAYS_IN_WEEK = 7
