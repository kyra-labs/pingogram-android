package dev.kyralabs.pingogram.feature.auth.ui.preview

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.feature.auth.ui.AuthAction
import dev.kyralabs.pingogram.feature.auth.ui.AuthScreen
import dev.kyralabs.pingogram.feature.auth.ui.AuthStepUiState
import dev.kyralabs.pingogram.feature.auth.ui.AuthUiState
import dev.kyralabs.pingogram.feature.auth.ui.CountryPickerUiState
import dev.kyralabs.pingogram.feature.auth.ui.CountryUiModel
import kotlinx.collections.immutable.persistentListOf

/** Sample states for the previews of every login step (and the screenshot tests that will reuse them). */
internal object AuthPreviewData {
    val unitedKingdom = CountryUiModel(isoCode = "GB", name = "United Kingdom", flagEmoji = "🇬🇧", callingCode = "44")

    val countries =
        persistentListOf(
            CountryUiModel("CA", "Canada", "🇨🇦", "1"),
            CountryUiModel("DE", "Germany", "🇩🇪", "49"),
            unitedKingdom,
            CountryUiModel("US", "United States", "🇺🇸", "1"),
        )

    fun state(
        step: AuthStepUiState,
        connection: ConnectionStatus = ConnectionStatus.Ready,
        floodWaitSeconds: Long? = null,
        pendingAction: AuthAction? = null,
        canGoBack: Boolean = true,
    ) = AuthUiState(step, connection, floodWaitSeconds, pendingAction, canGoBack)

    fun phoneNumber(
        number: String = "7911123456",
        error: DomainError? = null,
        isChangingNumber: Boolean = false,
        pickerOpen: Boolean = false,
    ) = AuthStepUiState.PhoneNumber(
        callingCode = "44",
        number = number,
        numberFormat = "---- ------",
        country = unitedKingdom,
        error = error,
        isChangingNumber = isChangingNumber,
        countryPicker = if (pickerOpen) CountryPickerUiState("", countries) else null,
    )
}

/** An [AuthScreen] in the preview theme. */
@Composable
internal fun AuthScreenPreview(state: AuthUiState) {
    PingogramPreview {
        AuthScreen(state = state, onEvent = {})
    }
}
