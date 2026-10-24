package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.PrivacyPreviewStates as States

// The previews of a privacy setting's exceptions, one per state (loading shows nothing for its first 200 ms, so none).

@PingogramPreviews
@Composable
private fun PrivacyExceptionsPreview() = Preview(States.exceptions)

@PingogramPreviews
@Composable
private fun PrivacyExceptionsEmptyPreview() = Preview(States.exceptionsEmpty, PrivacyExceptionSide.Always)

@PingogramPreviews
@Composable
private fun PrivacyExceptionsSavingPreview() = Preview(States.exceptionsSaving)

@PingogramPreviews
@Composable
private fun PrivacyExceptionsErrorPreview() = Preview(States.exceptionsError)

@PingogramPreviews
@Composable
private fun PrivacyExceptionsPickerPreview() {
    PingogramPreview { PrivacyExceptionsPickerContent(States.exceptionsPicker, onEvent = {}) }
}

@Composable
private fun Preview(
    state: PrivacyExceptionsUiState,
    side: PrivacyExceptionSide = PrivacyExceptionSide.Never,
) {
    PingogramPreview {
        PrivacyExceptionsScreen(PrivacySetting.LastSeen, side, state, onEvent = {}, onBack = {})
    }
}
