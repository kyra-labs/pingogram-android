package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChangePhoneNumberPreviewStates

// The previews of Change number, one per state.

@PingogramPreviews
@Composable
private fun ChangePhoneNumberIntroPreview() = Preview(ChangePhoneNumberPreviewStates.intro)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberNumberPreview() = Preview(ChangePhoneNumberPreviewStates.number)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberOccupiedPreview() = Preview(ChangePhoneNumberPreviewStates.numberOccupied)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberSendingPreview() = Preview(ChangePhoneNumberPreviewStates.sending)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberCodePreview() = Preview(ChangePhoneNumberPreviewStates.code)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberCodeWrongPreview() = Preview(ChangePhoneNumberPreviewStates.codeWrong)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberFloodWaitPreview() = Preview(ChangePhoneNumberPreviewStates.floodWait)

@PingogramPreviews
@Composable
private fun ChangePhoneNumberDonePreview() = Preview(ChangePhoneNumberPreviewStates.done)

@Composable
private fun Preview(state: ChangePhoneNumberUiState) {
    PingogramPreview { ChangePhoneNumberScreen(state = state, onEvent = {}, onBack = {}) }
}
