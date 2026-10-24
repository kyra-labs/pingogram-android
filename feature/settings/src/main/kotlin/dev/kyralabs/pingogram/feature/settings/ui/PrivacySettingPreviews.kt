package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData

// The previews of a privacy setting's page, one per state.

@PingogramPreviews
@Composable
private fun PrivacySettingScreenPreview() {
    PingogramPreview { PrivacySettingScreen(state = SettingsPreviewData.privacySetting, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenSavingPreview() {
    PingogramPreview {
        PrivacySettingScreen(
            state = SettingsPreviewData.privacySetting.copy(savingChoice = PrivacyChoice.Nobody),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenPremiumRequiredPreview() {
    PingogramPreview { PrivacySettingScreen(state = SettingsPreviewData.voiceMessages, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenLoadingPreview() {
    PingogramPreview {
        PrivacySettingScreen(state = PrivacySettingUiState(PrivacySetting.Calls), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenErrorPreview() {
    PingogramPreview {
        PrivacySettingScreen(
            state = PrivacySettingUiState(PrivacySetting.Calls, PrivacyRulesState.Failed(DomainError.Network)),
            onEvent = {},
            onBack = {},
        )
    }
}
