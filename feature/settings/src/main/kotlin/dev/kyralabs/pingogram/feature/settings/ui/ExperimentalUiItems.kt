package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

// Settings home's "Experimental UI" switch (docs/adr/0035-experimental-ui.md, docs/DESIGN_SYSTEM.md §13): a row in the
// standard look, the labs card in the experimental one.

/**
 * "Experimental UI" in the standard look: a switch row (as [SwitchItem]) with the flask in the group's tonal circle,
 * its title followed by a "Beta" label in `tertiaryContainer` (the account card's label), and what it does as the
 * summary. It opens nothing: a tap turns the experimental look on, with a `ToggleOn` haptic.
 */
@Composable
internal fun ExperimentalUiItem(
    checked: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        shapes = shapes,
        modifier =
            Modifier
                .testTag(EXPERIMENTAL_UI_TAG)
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                },
        leadingContent = { SettingsItemIcon(R.drawable.ic_science) },
        trailingContent = { CheckSwitch(checked = checked) },
        supportingContent = { Text(stringResource(R.string.settings_experimental_ui_summary)) },
        colors = settingsRowColors(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(stringResource(R.string.settings_experimental_ui), modifier = Modifier.weight(1f, fill = false))
            AccountLabel(
                text = stringResource(R.string.settings_experimental_ui_beta),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * "Experimental UI" in the experimental look: the labs card under the account card, full width, `extraLarge` corners on
 * `tertiaryContainer` / `onTertiaryContainer` (`docs/DESIGN_SYSTEM.md` §13). A `MaterialShapes.Sunny` badge in
 * `onTertiaryContainer` with the flask in `tertiaryContainer`, the title in `titleMediumEmphasized` with a "BETA"
 * label (the colors inverted), what it does in `bodyMedium`, and a [CheckSwitch] in the card's colors. The whole card
 * is one toggleable (`Role.Switch`) for touch and screen readers, with `ToggleOn` / `ToggleOff` haptics.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExperimentalUiCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.tertiaryContainer)
                .toggleable(value = checked, role = Role.Switch) {
                    haptics.performToggle(it)
                    onCheckedChange(it)
                }.padding(spacing.large)
                .testTag(EXPERIMENTAL_UI_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onTertiaryContainer) {
            Box(
                Modifier
                    .size(MaterialTheme.sizes.avatarRow)
                    .background(colors.onTertiaryContainer, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_science),
                    contentDescription = null,
                    tint = colors.tertiaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.settings_experimental_ui),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    AccountLabel(
                        text = stringResource(R.string.settings_experimental_ui_beta).toUpperCase(LocaleList.current),
                        container = colors.onTertiaryContainer,
                        content = colors.tertiaryContainer,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_experimental_ui_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CheckSwitch(
                checked = checked,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = colors.onTertiaryContainer,
                        checkedThumbColor = colors.tertiaryContainer,
                        checkedIconColor = colors.onTertiaryContainer,
                    ),
            )
        }
    }
}

/** The test tag of the "Experimental UI" row or card. */
internal const val EXPERIMENTAL_UI_TAG = "Settings.experimentalUi"

@PingogramPreviews
@Composable
private fun ExperimentalUiCardPreview() {
    PingogramPreview { ExperimentalUiCard(checked = true, onCheckedChange = {}) }
}
