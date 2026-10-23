package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.EXPANDED
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Passcode lock page off, before the passcode is entered, and on, on a phone, a tablet and at 200 % font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PasscodeLockScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onLight() = capture("onLight", ThemeMode.Light, PasscodeLockOnPreview)

    @Test
    fun onDark() = capture("onDark", ThemeMode.Dark, PasscodeLockOnPreview)

    @Test
    fun onAmoled() = capture("onAmoled", ThemeMode.Dark, PasscodeLockOnPreview, amoled = true)

    @Test
    @Config(qualifiers = EXPANDED)
    fun onExpandedLight() = capture("onExpandedLight", ThemeMode.Light, PasscodeLockOnPreview)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun onFontScale200() = capture("onFontScale200", ThemeMode.Light, PasscodeLockOnPreview)

    @Test
    fun offLight() = capture("offLight", ThemeMode.Light, PasscodeLockUiState.Off)

    @Test
    fun offDark() = capture("offDark", ThemeMode.Dark, PasscodeLockUiState.Off)

    @Test
    fun verifyLight() = capture("verifyLight", ThemeMode.Light, PasscodeLockUiState.Verify(PasscodeEntryUiState()))

    @Test
    fun turnOffDialogLight() =
        capture("turnOffDialogLight", ThemeMode.Light, PasscodeLockOnPreview.copy(showTurnOffDialog = true))

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, PasscodeLockUiState.Loading)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: PasscodeLockUiState,
        amoled: Boolean = false,
    ) = composeRule.captureScreen(javaClass, name, themeMode, amoled) {
        val input = remember { PasscodeInput() }
        PasscodeLockScreen(state = state, input = input, onEvent = {}, onSetPasscode = {}, onBack = {})
    }
}
