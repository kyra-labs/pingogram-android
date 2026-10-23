package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.ui.phone.CountryUiModel
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberStep
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberUiState
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.CodeUiState
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.NewNumberUiState

/** The states of Change number for previews and goldens: a German account moving to a British number. */
internal object ChangePhoneNumberPreviewStates {
    private val britain = CountryUiModel("GB", "United Kingdom", "🇬🇧", "44")

    val intro = ChangePhoneNumberUiState(currentNumber = "4915112345678")

    val number =
        intro.copy(
            step = ChangePhoneNumberStep.Number,
            newNumber =
                NewNumberUiState(
                    callingCode = "44",
                    number = "7911123456",
                    numberFormat = "---- ------",
                    country = britain,
                ),
        )

    val numberOccupied = number.copy(newNumber = number.newNumber.copy(error = DomainError.PhoneNumberOccupied))

    val sending = number.copy(isWorking = true)

    val code =
        number.copy(
            step = ChangePhoneNumberStep.Code,
            code =
                CodeUiState(
                    sentTo = "+44 7911 123456",
                    type = AuthCodeType.Sms(length = 5),
                    nextType = AuthCodeType.Call(length = 5),
                    code = "123",
                    resendInSeconds = 42,
                    resendTotalSeconds = 60,
                ),
        )

    val codeWrong =
        code.copy(code = code.code?.copy(code = "12345", error = DomainError.CodeInvalid, resendInSeconds = 0))

    val floodWait = code.copy(floodWaitSeconds = 75, floodWaitTotalSeconds = 120)

    val done = intro.copy(step = ChangePhoneNumberStep.Done, changedTo = "+44 7911 123456")
}
