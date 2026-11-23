package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Privacy and security's "Incognito keyboard" (`REQUIREMENTS.md` FR-X7): a switch row with the keyboard symbol, as the
 * Security group's other rows have theirs, [checked] while text fields ask the keyboard not to learn.
 */
internal fun SettingsGroupScope.incognitoKeyboardItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = item { shapes ->
    SwitchItem(
        title = stringResource(R.string.settings_incognito_keyboard),
        summary = stringResource(R.string.settings_incognito_keyboard_summary),
        checked = checked,
        shapes = shapes,
        icon = UiR.drawable.ic_keyboard,
        onCheckedChange = onCheckedChange,
    )
}

@PingogramPreviews
@Composable
private fun IncognitoKeyboardItemPreview() {
    PingogramPreview {
        SettingsGroup(title = stringResource(R.string.settings_section_security)) {
            incognitoKeyboardItem(checked = false, onCheckedChange = {})
            incognitoKeyboardItem(checked = true, onCheckedChange = {})
        }
    }
}
