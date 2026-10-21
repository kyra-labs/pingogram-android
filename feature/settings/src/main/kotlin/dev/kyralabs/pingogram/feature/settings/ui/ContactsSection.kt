package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData

// Privacy and security's "Contacts" group (docs/adr/0011-contacts-sync.md), driven by ContactSyncSettingViewModel.

/** "Sync contacts": on while sync is on or paused, its summary saying what it does or why it is paused. */
@Composable
internal fun ContactsSection(
    status: ContactSyncStatus,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_contacts)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_contact_sync),
                summary =
                    stringResource(
                        when (status) {
                            ContactSyncStatus.Off -> R.string.settings_contact_sync_off
                            ContactSyncStatus.NeedsPermission -> R.string.settings_contact_sync_paused
                            is ContactSyncStatus.On -> R.string.settings_contact_sync_on
                        },
                    ),
                checked = status != ContactSyncStatus.Off,
                shapes = shapes,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun PrivacySecurityScreenContactSyncOnPreview() {
    PingogramPreview {
        PrivacySecurityScreen(
            state = SettingsPreviewData.privacySecurity,
            onEvent = {},
            onOpen = {},
            onBack = {},
            contactSync = ContactSyncStatus.On(lastSyncedAt = null),
        )
    }
}
