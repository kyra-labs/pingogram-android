package dev.kyralabs.pingogram.feature.settings.ui.focus

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
                            shapes = IconButtonDefaults.toggleableShapes(checkedShape = icon.shape()),
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
            item(key = FROM) { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_focus_from),
                    shapes = shapes,
                    supportingContent = { Text(focusTime(draft.startMinute, locale)) },
                    onClick = { onEvent(FocusEditEvent.Pick(FocusTimeEnd.Start)) },
                    enabled = enabled,
                )
            }
            item(key = TO) { shapes -> FocusEndRow(draft, enabled, shapes, onEvent) }
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
    SettingsSegment(shapes) {
        FlowRow(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
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
                    modifier =
                        Modifier.semantics {
                            role = Role.Checkbox
                            contentDescription = name
                        },
                ) {
                    Text(day.getDisplayName(TextStyle.SHORT, locale))
                }
            }
        }
    }
}

/** "To", the end of the schedule, "next day" when it is at or before the start. */
@Composable
private fun FocusEndRow(
    draft: FocusDraft,
    enabled: Boolean,
    shapes: ListItemShapes,
    onEvent: (FocusEditEvent) -> Unit,
) {
    val end = focusTime(draft.endMinute, appLocale())
    SettingsRow(
        title = stringResource(R.string.settings_focus_to),
        shapes = shapes,
        supportingContent = {
            Text(
                if (draft.endMinute <=
                    draft.startMinute
                ) {
                    stringResource(R.string.settings_focus_next_day, end)
                } else {
                    end
                },
            )
        },
        onClick = { onEvent(FocusEditEvent.Pick(FocusTimeEnd.End)) },
        enabled = enabled,
    )
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
private const val FROM = "from"
private const val TO = "to"
