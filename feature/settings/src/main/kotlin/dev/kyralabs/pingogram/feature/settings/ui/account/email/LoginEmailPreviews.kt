package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.AccountPagePreviewStates as States

// The previews of the login email page, one per state (loading shows nothing for its first 200 ms, so none).

@PingogramPreviews
@Composable
private fun LoginEmailErrorPreview() = LoginEmail(States.loginEmailError)

@PingogramPreviews
@Composable
private fun LoginEmailIntroPreview() = LoginEmail(States.loginEmailIntro)

@PingogramPreviews
@Composable
private fun LoginEmailNonePreview() = LoginEmail(States.loginEmailNone)

@PingogramPreviews
@Composable
private fun LoginEmailAddressPreview() = LoginEmail(States.loginEmailAddress)

@PingogramPreviews
@Composable
private fun LoginEmailAddressErrorPreview() = LoginEmail(States.loginEmailAddressError)

@PingogramPreviews
@Composable
private fun LoginEmailCodePreview() = LoginEmail(States.loginEmailCode)

@PingogramPreviews
@Composable
private fun LoginEmailCodeWrongPreview() = LoginEmail(States.loginEmailCodeWrong)

@PingogramPreviews
@Composable
private fun LoginEmailFloodWaitPreview() = LoginEmail(States.loginEmailFloodWait)

@PingogramPreviews
@Composable
private fun LoginEmailDonePreview() = LoginEmail(States.loginEmailDone)

@Composable
private fun LoginEmail(state: LoginEmailUiState) {
    PingogramPreview { LoginEmailScreen(state = state, onEvent = {}, onBack = {}) }
}
