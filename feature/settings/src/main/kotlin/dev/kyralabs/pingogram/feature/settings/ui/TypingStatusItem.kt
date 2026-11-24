package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R

/**
 * Privacy and security's "Send typing status": a switch row, [checked] while a chat sees what the user is doing in it
 * (typing, recording a voice or round video message, sending a photo or a file). No icon, as the Privacy group's other
 * rows have none.
 */
internal fun SettingsGroupScope.typingStatusItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = item(key = TYPING_STATUS) { shapes ->
    SwitchItem(
        title = stringResource(R.string.settings_send_typing_status),
        summary = stringResource(R.string.settings_send_typing_status_summary),
        checked = checked,
        shapes = shapes,
        onCheckedChange = onCheckedChange,
    )
}

private const val TYPING_STATUS = "typing_status"

@PingogramPreviews
@Composable
private fun TypingStatusItemPreview() {
    PingogramPreview {
        SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
            typingStatusItem(checked = true, onCheckedChange = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun TypingStatusItemOffPreview() {
    PingogramPreview {
        SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
            typingStatusItem(checked = false, onCheckedChange = {})
        }
    }
}
