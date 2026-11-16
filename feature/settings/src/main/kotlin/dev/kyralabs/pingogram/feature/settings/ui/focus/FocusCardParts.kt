package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.FocusBadge
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.symbol
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A profile's card for screen readers: one item, its name, how it runs, what it allows and whether it is on ([isOn]),
 * the switch its custom action ([toggle], board 31).
 */
@Composable
internal fun Modifier.cardSemantics(
    isOn: Boolean,
    toggle: () -> Unit,
): Modifier {
    val state = stringResource(if (isOn) R.string.settings_focus_state_on else R.string.settings_focus_state_off)
    val label = stringResource(if (isOn) R.string.settings_focus_turn_off else R.string.settings_focus_turn_on)
    return semantics {
        stateDescription = state
        customActions =
            listOf(
                CustomAccessibilityAction(label) {
                    toggle()
                    true
                },
            )
    }
}

/** The profile's [name], with "ON NOW" beside it while [isOn], under it where the line has no room (large fonts). */
@Composable
internal fun ProfileTitle(
    name: String,
    isOn: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name)
        if (isOn) OnNowPill()
    }
}

/** "ON NOW" on a full pill in the card's inverse colors, part of the card for screen readers (its state says so). */
@Composable
private fun OnNowPill() {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = stringResource(R.string.settings_focus_on_now).uppercase(locale),
        style = MaterialTheme.typography.labelSmallEmphasized,
        color = colors.primaryContainer,
        modifier =
            Modifier
                .clearAndSetSemantics {}
                .background(colors.onPrimaryContainer, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
    )
}
