package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.OauthLoginRequest
import dev.kyralabs.pingogram.feature.settings.ui.oauth.OauthLoginStep
import dev.kyralabs.pingogram.feature.settings.ui.oauth.OauthLoginUiState

/** Sample states of the "Log in with Telegram" sheet, for previews and goldens. */
internal object OauthLoginPreviewStates {
    val request =
        OauthLoginRequest(
            domain = "example.org",
            appName = "Example News",
            isApp = false,
            botUserId = 7,
            asksToMessage = true,
            asksForPhoneNumber = true,
            asksForTonWallet = false,
            browser = "Firefox 145",
            platform = "Linux",
            location = "Berlin, Germany",
            matchCodes = listOf("17", "42", "88"),
        )

    val confirm =
        OauthLoginUiState(
            step = OauthLoginStep.Confirm,
            request = request,
            accountName = "Ada Lovelace",
            botName = "@examplenews_bot",
        )

    val confirmChosen = confirm.copy(allowMessages = true, sharePhoneNumber = true)

    val unverifiedApp =
        confirm.copy(
            request = request.copy(appName = "", isApp = true, asksForPhoneNumber = false, asksForTonWallet = true),
        )

    val matchCode = confirm.copy(step = OauthLoginStep.MatchCode)

    val busy = confirmChosen.copy(isBusy = true)

    val refused = confirm.copy(error = DomainError.Network)

    val done = confirm.copy(step = OauthLoginStep.Done)

    val failed = OauthLoginUiState(step = OauthLoginStep.Failed, error = DomainError.Network)
}
