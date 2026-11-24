package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.contacts.R

/**
 * "Share my phone number" on the contact forms: a list item with a [CheckSwitch] at its end on the fields' tonal
 * container (`surfaceContainer`), the whole row toggling it as a switch for screen readers, with a
 * `ToggleOn`/`ToggleOff` haptic. Its summary names the contact ([name]), or says "they" while no name is typed.
 */
@Composable
internal fun SharePhoneNumberRow(
    name: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        enabled = enabled,
        supportingContent = {
            val summary =
                if (name.isNullOrBlank()) {
                    stringResource(R.string.contacts_share_phone_number_summary_unnamed)
                } else {
                    stringResource(R.string.contacts_share_phone_number_summary, name.bidiIsolated()).bidiIsolated()
                }
            Text(summary)
        },
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = { Text(stringResource(R.string.contacts_share_phone_number)) },
    )
}
