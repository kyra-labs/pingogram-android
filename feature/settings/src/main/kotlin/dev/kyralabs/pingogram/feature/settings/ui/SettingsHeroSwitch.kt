package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.preview.PingogramAmoledPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramHighContrastPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A page's master switch, the one that turns the page's feature on or off, as a card at the top of the page
 * (`docs/DESIGN_SYSTEM.md` §13.10, "Master switch"): [title] in `titleMediumEmphasized` over [summary] in `bodyMedium`
 * where it has one, and a [CheckSwitch] at the end, on `primaryContainer` / `onPrimaryContainer` with full corners and
 * `spacing.large` inside. The whole card toggles it, with a `ToggleOn` or `ToggleOff` haptic, and screen readers hear
 * it as one switch.
 */
@Composable
internal fun SettingsHeroSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .toggleable(value = checked, role = Role.Switch) { on ->
                    haptics.performToggle(on)
                    onCheckedChange(on)
                }.padding(spacing.large),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        CheckSwitch(checked = checked)
    }
}

@PingogramPreviews
@PingogramAmoledPreview
@PingogramHighContrastPreview
@Composable
private fun SettingsHeroSwitchPreview() {
    PingogramPreview {
        var on by remember { mutableStateOf(true) }
        SettingsHeroSwitch(
            title = "Use proxy",
            summary = "Connect to Telegram through a proxy server",
            checked = on,
            onCheckedChange = { on = it },
            modifier = Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}
