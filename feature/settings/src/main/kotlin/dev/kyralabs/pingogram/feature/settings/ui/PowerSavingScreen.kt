package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/** The power saving page: hoists [PowerSavingViewModel], shows why a change was not saved in a snackbar. */
@Composable
internal fun PowerSavingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PowerSavingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is PowerSavingEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    PowerSavingScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Power saving: "Turn on" as radio rows (Off, With Battery Saver, which says whether Battery Saver is on now, and
 * Always), then "While on" with a switch for each part it turns off: animated stickers and custom emoji, autoplaying
 * videos and GIFs, and the call screens' animations. Reduced motion stays the system's, which a footer says. Nothing
 * shows until the settings are read, a matter of milliseconds.
 */
@Composable
internal fun PowerSavingScreen(
    state: PowerSavingUiState,
    onEvent: (PowerSavingEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_power_saving),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val mode = state.mode ?: return@SettingsPageScaffold
        SettingsGroup(
            title = stringResource(R.string.settings_power_saving_section_mode),
            footer = stringResource(R.string.settings_power_saving_mode_footer),
            rowsModifier = Modifier.selectableGroup(),
        ) {
            PowerSavingMode.entries.forEach { option ->
                item(key = option) { shapes ->
                    RadioItem(
                        label = stringResource(option.label()),
                        selected = option == mode,
                        enabled = true,
                        shapes = shapes,
                        onClick = { onEvent(PowerSavingEvent.SetMode(option)) },
                        summary =
                            if (option == PowerSavingMode.WithBatterySaver) {
                                stringResource(
                                    if (state.isBatterySaverOn) {
                                        R.string.settings_power_saving_battery_saver_on
                                    } else {
                                        R.string.settings_power_saving_battery_saver_off
                                    },
                                )
                            } else {
                                null
                            },
                    )
                }
            }
        }
        SettingsGroup(
            title = stringResource(R.string.settings_power_saving_section_parts),
            footer = stringResource(R.string.settings_power_saving_parts_footer),
        ) {
            PowerSavingPart.entries.forEach { part ->
                item(key = part) { shapes ->
                    SwitchItem(
                        title = stringResource(part.title()),
                        summary = stringResource(part.summary()),
                        checked = part in state.parts,
                        shapes = shapes,
                        onCheckedChange = { onEvent(PowerSavingEvent.SetPart(part, it)) },
                    )
                }
            }
        }
    }
}

@StringRes
private fun PowerSavingMode.label(): Int =
    when (this) {
        PowerSavingMode.Off -> R.string.settings_power_saving_off
        PowerSavingMode.WithBatterySaver -> R.string.settings_power_saving_with_battery_saver
        PowerSavingMode.Always -> R.string.settings_power_saving_always
    }

@StringRes
private fun PowerSavingPart.title(): Int =
    when (this) {
        PowerSavingPart.AnimatedStickers -> R.string.settings_power_saving_stickers
        PowerSavingPart.Autoplay -> R.string.settings_power_saving_autoplay
        PowerSavingPart.CallAnimations -> R.string.settings_power_saving_calls
    }

@StringRes
private fun PowerSavingPart.summary(): Int =
    when (this) {
        PowerSavingPart.AnimatedStickers -> R.string.settings_power_saving_stickers_summary
        PowerSavingPart.Autoplay -> R.string.settings_power_saving_autoplay_summary
        PowerSavingPart.CallAnimations -> R.string.settings_power_saving_calls_summary
    }

@PingogramPreviews
@Composable
private fun PowerSavingScreenPreview() {
    PingogramPreview { PowerSavingScreen(SettingsPreviewData.powerSaving, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PowerSavingScreenOnPreview() {
    PingogramPreview { PowerSavingScreen(SettingsPreviewData.powerSavingOn, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PowerSavingScreenLoadingPreview() {
    PingogramPreview { PowerSavingScreen(PowerSavingUiState(), onEvent = {}, onBack = {}) }
}
