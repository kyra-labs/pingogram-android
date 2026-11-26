package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.nestedItemColors
import kotlinx.collections.immutable.toImmutableList

/**
 * Cleanup suggestions' settings (`REQUIREMENTS.md` FR-X24): the switch, then "Not opened for" with its months as
 * [ChoiceRow]'s connected buttons (its radio list where they do not fit), dimmed while the switch is off.
 */
@Composable
internal fun CleanupSettingsGroup(
    settings: CleanupSettings,
    onEvent: (CleanupEvent) -> Unit,
) {
    SettingsGroup {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_cleanup_switch),
                summary = stringResource(R.string.settings_cleanup_switch_summary),
                checked = settings.enabled,
                shapes = shapes,
                onCheckedChange = { onEvent(CleanupEvent.SetEnabled(it)) },
            )
        }
        item { shapes ->
            SettingsSegment(shapes) {
                SettingsListItem(
                    shapes = ListItemDefaults.shapes(),
                    enabled = settings.enabled,
                    colors = nestedItemColors(),
                ) {
                    Text(stringResource(R.string.settings_cleanup_months))
                }
                val spacing = MaterialTheme.spacing
                ChoiceRow(
                    options = MonthChoices,
                    selected = settings.months,
                    label = { pluralStringResource(R.plurals.settings_cleanup_months_choice, it, it) },
                    onSelect = { onEvent(CleanupEvent.SetMonths(it)) },
                    contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
                    enabled = settings.enabled,
                )
            }
        }
    }
}

private val MonthChoices = CleanupSettings.MONTHS.toImmutableList()
