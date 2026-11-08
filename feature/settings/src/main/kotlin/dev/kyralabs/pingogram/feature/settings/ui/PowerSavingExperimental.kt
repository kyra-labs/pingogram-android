package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
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
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

// The experimental Power saving page's top (docs/DESIGN_SYSTEM.md §13.10).

/**
 * The experimental look's top of the page (`docs/DESIGN_SYSTEM.md` §13.10): a status card saying whether Battery Saver
 * is on, what the chosen mode does and whether power saving is in effect now, then "Turn on" as connected buttons.
 */
@Composable
internal fun ExperimentalPowerSavingMode(
    state: PowerSavingUiState,
    mode: PowerSavingMode,
    onEvent: (PowerSavingEvent) -> Unit,
) {
    SettingsStatusCard(
        kicker = stringResource(R.string.settings_power_saving_now),
        title =
            stringResource(
                if (state.isBatterySaverOn) {
                    R.string.settings_power_saving_card_battery_saver_on
                } else {
                    R.string.settings_power_saving_card_battery_saver_off
                },
            ),
        summary = stringResource(mode.cardSummary()),
        status =
            stringResource(
                if (state.isOn) {
                    R.string.settings_power_saving_card_saving
                } else {
                    R.string.settings_power_saving_card_normal
                },
            ),
        icon = R.drawable.ic_energy_savings_leaf,
    )
    SettingsGroup(
        title = stringResource(R.string.settings_power_saving_section_mode),
        footer = stringResource(R.string.settings_power_saving_mode_footer),
    ) {
        item { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = PowerSavingModes,
                    selected = mode,
                    label = { stringResource(it.label()) },
                    onSelect = { onEvent(PowerSavingEvent.SetMode(it)) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.large),
                )
            }
        }
    }
}

private val PowerSavingModes = PowerSavingMode.entries.toImmutableList()

@StringRes
private fun PowerSavingMode.cardSummary(): Int =
    when (this) {
        PowerSavingMode.Off -> R.string.settings_power_saving_card_off
        PowerSavingMode.WithBatterySaver -> R.string.settings_power_saving_card_with_battery_saver
        PowerSavingMode.Always -> R.string.settings_power_saving_card_always
    }
