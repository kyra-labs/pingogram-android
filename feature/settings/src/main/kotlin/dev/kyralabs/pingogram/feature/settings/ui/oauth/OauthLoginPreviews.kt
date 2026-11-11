package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.OauthLoginPreviewStates as States

// Previews of the "Log in with Telegram" sheet's content in each of its states.

@PingogramPreviews
@Composable
private fun OauthLoginConfirmPreview() = Content(States.confirm)

@PingogramPreviews
@Composable
private fun OauthLoginChosenPreview() = Content(States.confirmChosen)

@PingogramPreviews
@Composable
private fun OauthLoginUnverifiedPreview() = Content(States.unverifiedApp)

@PingogramPreviews
@Composable
private fun OauthLoginMatchCodePreview() = Content(States.matchCode)

@PingogramPreviews
@Composable
private fun OauthLoginBusyPreview() = Content(States.busy)

@PingogramPreviews
@Composable
private fun OauthLoginRefusedPreview() = Content(States.refused)

@PingogramPreviews
@Composable
private fun OauthLoginDonePreview() = Content(States.done)

@PingogramPreviews
@Composable
private fun OauthLoginFailedPreview() = Content(States.failed)

@Composable
private fun Content(state: OauthLoginUiState) {
    PingogramPreview { OauthLoginContent(state, onEvent = {}, onClose = {}) }
}
