package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Setting a passcode: choosing a PIN or a password, entering it again, and entries that differ. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SetPasscodeScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chooseLight() = capture("chooseLight", ThemeMode.Light, SetPasscodeUiState(), digits = 2)

    @Test
    fun chooseDark() = capture("chooseDark", ThemeMode.Dark, SetPasscodeUiState(), digits = 2)

    @Test
    fun confirmLight() =
        capture("confirmLight", ThemeMode.Light, SetPasscodeUiState(step = SetPasscodeStep.Confirm), digits = 4)

    @Test
    fun mismatchLight() =
        capture(
            "mismatchLight",
            ThemeMode.Light,
            SetPasscodeUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Mismatch, rejections = 1)),
        )

    @Test
    fun passwordDark() =
        capture(
            "passwordDark",
            ThemeMode.Dark,
            SetPasscodeUiState(entry = PasscodeEntryUiState(type = PasscodeType.Password)),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: SetPasscodeUiState,
        digits: Int = 0,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        val input = remember { PasscodeInput().apply { repeat(digits) { append('5', PasscodeType.Pin) } } }
        SetPasscodeScreen(state = state, input = input, onEvent = {}, onBack = {})
    }
}
