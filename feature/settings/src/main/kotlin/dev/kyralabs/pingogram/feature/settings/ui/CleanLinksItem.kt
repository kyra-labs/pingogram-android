package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R

/**
 * Privacy and security's "Remove tracking from links" (`REQUIREMENTS.md` FR-X8): a switch row, [checked] while links
 * lose their tracking parameters when sent and when opened. No icon, as the Privacy group's other rows have none.
 */
internal fun SettingsGroupScope.cleanLinksItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = item(key = CLEAN_LINKS) { shapes ->
    SwitchItem(
        title = stringResource(R.string.settings_clean_links),
        summary = stringResource(R.string.settings_clean_links_summary),
        checked = checked,
        shapes = shapes,
        onCheckedChange = onCheckedChange,
    )
}

private const val CLEAN_LINKS = "clean_links"

@PingogramPreviews
@Composable
private fun CleanLinksItemPreview() {
    PingogramPreview {
        SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
            cleanLinksItem(checked = true, onCheckedChange = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun CleanLinksItemOffPreview() {
    PingogramPreview {
        SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
            cleanLinksItem(checked = false, onCheckedChange = {})
        }
    }
}
