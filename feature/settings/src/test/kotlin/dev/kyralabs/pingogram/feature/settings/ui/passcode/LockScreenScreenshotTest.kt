package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lock screen: a PIN, a password, what goes wrong, in landscape and at 200 % font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LockScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pin = LockUiState(biometricUnlock = true)

    @Test
    fun pinLight() = capture("pinLight", ThemeMode.Light, pin, digits = 3)

    @Test
    fun pinDark() = capture("pinDark", ThemeMode.Dark, pin, digits = 3)

    @Test
    fun pinAmoled() = capture("pinAmoled", ThemeMode.Dark, pin, digits = 3, amoled = true)

    @Test
    fun wrongLight() =
        capture(
            "wrongLight",
            ThemeMode.Light,
            LockUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Wrong, rejections = 1)),
        )

    @Test
    fun waitDark() =
        capture(
            "waitDark",
            ThemeMode.Dark,
            LockUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Wait(30), rejections = 5)),
        )

    @Test
    fun passwordLight() =
        capture(
            "passwordLight",
            ThemeMode.Light,
            LockUiState(entry = PasscodeEntryUiState(type = PasscodeType.Password)),
        )

    /** The phone's screen lock alone unlocks, without biometrics: the button shows the PIN symbol. */
    @Test
    fun screenLockLight() =
        capture("screenLockLight", ThemeMode.Light, LockUiState(biometricUnlock = true, screenLock = ScreenLock.Only))

    @Test
    fun forgetDialogLight() = capture("forgetDialogLight", ThemeMode.Light, LockUiState(showForgetDialog = true))

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeLight() = capture("landscapeLight", ThemeMode.Light, pin, digits = 4)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, pin, digits = 2)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: LockUiState,
        digits: Int = 0,
        amoled: Boolean = false,
    ) = composeRule.captureScreen(javaClass, name, themeMode, amoled) {
        val input = remember { PasscodeInput().apply { repeat(digits) { append('5', PasscodeType.Pin) } } }
        LockScreen(state = state, input = input, onEvent = {}, onBiometricUnlock = {})
    }
}
