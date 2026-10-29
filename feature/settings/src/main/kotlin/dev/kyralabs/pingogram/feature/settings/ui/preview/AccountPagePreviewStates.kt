package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.settings.ui.account.autodelete.AutoDeleteUiState
import dev.kyralabs.pingogram.feature.settings.ui.account.email.LoginEmailStep
import dev.kyralabs.pingogram.feature.settings.ui.account.email.LoginEmailUiState
import kotlinx.collections.immutable.persistentListOf

/** The states of the login email and auto-delete pages for previews and goldens. */
internal object AccountPagePreviewStates {
    val loginEmailError = LoginEmailUiState(isLoading = false, loadError = DomainError.Network)

    val loginEmailIntro = LoginEmailUiState(isLoading = false, current = "a****@g****.com")

    val loginEmailNone = LoginEmailUiState(isLoading = false)

    val loginEmailAddress = loginEmailIntro.copy(step = LoginEmailStep.Address, address = "ada@example.org")

    val loginEmailAddressError = loginEmailAddress.copy(address = "ada", addressError = DomainError.EmailInvalid)

    val loginEmailCode =
        loginEmailAddress.copy(step = LoginEmailStep.Code, sentTo = "a**@e******.org", codeLength = 6, code = "123")

    val loginEmailCodeWrong = loginEmailCode.copy(code = "123456", codeError = DomainError.CodeInvalid)

    val loginEmailFloodWait = loginEmailAddress.copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60)

    val loginEmailDone = loginEmailCode.copy(step = LoginEmailStep.Done, current = "a**@e******.org")

    private val choices = persistentListOf(0, 86_400, 604_800, 2_678_400)

    val autoDeleteLoading = AutoDeleteUiState.Loading

    val autoDeleteError = AutoDeleteUiState.Error(DomainError.Network)

    val autoDelete = AutoDeleteUiState.Content(seconds = 604_800, options = choices)

    val autoDeleteOther = AutoDeleteUiState.Content(seconds = 7_776_000, options = choices.add(7_776_000))

    val autoDeleteSaving = autoDelete.copy(seconds = 86_400, isSaving = true)
}
