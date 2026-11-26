package dev.kyralabs.pingogram.feature.settings.ui.notifications

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.focus.appLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Daily digest" (`REQUIREMENTS.md` FR-X23) on the Notifications page: its switch, with what it is under it, then
 * "Time", around which it comes, in the language's short style, dimmed while it is off; a tap opens Material 3's time
 * picker. Nothing shows until the settings are read.
 */
@Composable
internal fun DailyDigestGroup(
    state: DailyDigestUiState,
    onEvent: (DailyDigestEvent) -> Unit,
) {
    val settings = state.settings ?: return
    SettingsGroup(title = stringResource(R.string.settings_daily_digest)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_daily_digest),
                summary = stringResource(R.string.settings_daily_digest_summary),
                checked = settings.enabled,
                shapes = shapes,
                onCheckedChange = { onEvent(DailyDigestEvent.SetEnabled(it)) },
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_daily_digest_time),
                shapes = shapes,
                supportingContent = { Text(digestTime(settings.time)) },
                onClick = { onEvent(DailyDigestEvent.PickTime) },
                enabled = settings.enabled,
            )
        }
    }
    if (state.isPickingTime) DigestTimeDialog(settings, onEvent)
}

/** Material 3's time picker, at the digest's time, in the phone's 12- or 24-hour clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigestTimeDialog(
    settings: DailyDigestSettings,
    onEvent: (DailyDigestEvent) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = settings.time.hour,
            initialMinute = settings.time.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    TimePickerDialog(
        onDismissRequest = { onEvent(DailyDigestEvent.DismissTime) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(DailyDigestEvent.SetTime(LocalTime.of(state.hour, state.minute))) },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.settings_focus_time_done)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(DailyDigestEvent.DismissTime) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_dialog_dismiss))
            }
        },
        title = { Text(stringResource(R.string.settings_daily_digest_time)) },
    ) {
        TimePicker(state = state)
    }
}

/** [time] in the language's short style ("8:30 AM", "08:30"). */
@Composable
private fun digestTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale()))

/** Shows the Daily digest's [effects], settings not saved, in the Notifications page's [snackbarHostState]. */
@Composable
internal fun DailyDigestEffects(
    effects: Flow<DailyDigestEffect>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is DailyDigestEffect.ShowError -> {
                    // Snackbars queue up; later effects must not wait for them.
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
}

@PingogramPreviews
@Composable
private fun DailyDigestGroupPreview() {
    PingogramPreview {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            DailyDigestGroup(DailyDigestUiState(DailyDigestSettings(enabled = true)), onEvent = {})
        }
    }
}
