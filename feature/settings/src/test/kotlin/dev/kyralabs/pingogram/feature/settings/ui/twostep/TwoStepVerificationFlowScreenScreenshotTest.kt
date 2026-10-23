package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.ChangePassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.ConfirmRecoveryEmail
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow.SetPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.CurrentPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.EmailCode
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.Hint
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.NewPassword
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.RecoveryCode
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.RecoveryEmail
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationStep.Reset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each step of 2-step verification's flows, with an error and a flood wait, on a phone and at 200 % font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TwoStepVerificationFlowScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun currentPasswordLight() = capture("currentPasswordLight", ThemeMode.Light, flow(ChangePassword, CurrentPassword))

    @Test
    fun currentPasswordDark() = capture("currentPasswordDark", ThemeMode.Dark, flow(ChangePassword, CurrentPassword))

    @Test
    fun wrongPasswordLight() =
        capture(
            "wrongPasswordLight",
            ThemeMode.Light,
            flow(ChangePassword, CurrentPassword).copy(
                fieldError = TwoStepVerificationFieldError.Rejected(DomainError.PasswordInvalid),
            ),
        )

    @Test
    fun newPasswordLight() =
        capture(
            "newPasswordLight",
            ThemeMode.Light,
            flow(SetPassword, NewPassword).copy(fieldError = TwoStepVerificationFieldError.PasswordsDiffer),
        )

    @Test
    fun newPasswordDark() = capture("newPasswordDark", ThemeMode.Dark, flow(SetPassword, NewPassword))

    @Test
    fun hintLight() = capture("hintLight", ThemeMode.Light, flow(SetPassword, Hint))

    @Test
    fun recoveryEmailLight() = capture("recoveryEmailLight", ThemeMode.Light, flow(SetPassword, RecoveryEmail))

    @Test
    fun emailCodeLight() = capture("emailCodeLight", ThemeMode.Light, flow(ConfirmRecoveryEmail, EmailCode))

    @Test
    fun recoveryCodeLight() = capture("recoveryCodeLight", ThemeMode.Light, flow(ChangePassword, RecoveryCode))

    @Test
    fun resetLight() = capture("resetLight", ThemeMode.Light, flow(ChangePassword, Reset))

    @Test
    fun floodWaitLight() =
        capture(
            "floodWaitLight",
            ThemeMode.Light,
            flow(ChangePassword, CurrentPassword).copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60),
        )

    @Test
    fun floodWaitDark() =
        capture(
            "floodWaitDark",
            ThemeMode.Dark,
            flow(ChangePassword, CurrentPassword).copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60),
        )

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, flow(ChangePassword, CurrentPassword))

    private fun flow(
        flow: TwoStepVerificationFlow,
        step: TwoStepVerificationStep,
    ) = SettingsPreviewData.twoStepFlow(flow, step)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: TwoStepVerificationFlowUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        TwoStepVerificationFlowScreen(state = state, onEvent = {}, onBack = {})
    }
}
