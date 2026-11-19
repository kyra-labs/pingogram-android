package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

/**
 * A setting that is on or off and has a page of its own (`docs/DESIGN_SYSTEM.md` §13.10 "Split row", owner decision
 * 2026-11-19): [title] over [summary] at the start, then a chevron, a 1 dp divider in `outlineVariant` and a
 * [CheckSwitch]. Two touch targets: the text opens the page ([onOpen]; screen readers hear "open [title]"), the switch
 * toggles the setting in place with a `ToggleOn` or `ToggleOff` haptic, read as a switch named [switchLabel].
 */
@Composable
internal fun SettingsSplitRow(
    title: String,
    checked: Boolean,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    switchLabel: String = title,
) {
    val haptics = LocalHapticFeedback.current
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shapes.shape)
                .background(settingsRowContainerColor()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = stringResource(R.string.settings_split_row_open, title),
                    role = Role.Button,
                    onClick = onOpen,
                ).semantics { contentDescription = listOfNotNull(title, summary).joinToString(", ") },
        ) {
            // The text area reads as one button, named by the title and summary.
            SettingsListItem(
                modifier = Modifier.clearAndSetSemantics {},
                shapes = shapes,
                trailingContent = { PageChevron() },
                supportingContent = summary?.let { { Text(it) } },
                colors = nestedItemColors(),
            ) { Text(title) }
        }
        VerticalDivider(
            modifier = Modifier.padding(vertical = spacing.large),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .toggleable(value = checked, role = Role.Switch) { on ->
                        haptics.performToggle(on)
                        onCheckedChange(on)
                    }.semantics { contentDescription = switchLabel }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = spacing.large),
            contentAlignment = Alignment.Center,
        ) {
            CheckSwitch(checked = checked)
        }
    }
}

@PingogramPreviews
@Composable
private fun SettingsSplitRowPreview() {
    PingogramPreview {
        var on by remember { mutableStateOf(true) }
        SettingsSplitRow(
            title = "Raise to listen",
            summary = "Hold the phone to your ear to hear voice messages through the earpiece",
            checked = on,
            shapes = ListItemDefaults.shapes(),
            onOpen = {},
            onCheckedChange = { on = it },
            modifier = Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}
