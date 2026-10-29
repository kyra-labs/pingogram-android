package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.AccountPagePreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Login email in each of its states, light and dark; the code step in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LoginEmailScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun introLight() = capture("introLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailIntro)

    @Test
    fun introDark() = capture("introDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailIntro)

    @Test
    fun noneLight() = capture("noneLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailNone)

    @Test
    fun noneDark() = capture("noneDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailNone)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailError)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailError)

    @Test
    fun addressLight() = capture("addressLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailAddress)

    @Test
    fun addressDark() = capture("addressDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailAddress)

    @Test
    fun addressErrorLight() =
        capture("addressErrorLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailAddressError)

    @Test
    fun addressErrorDark() =
        capture("addressErrorDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailAddressError)

    @Test
    fun codeLight() = capture("codeLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailCode)

    @Test
    fun codeDark() = capture("codeDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailCode)

    @Test
    fun codeWrongLight() = capture("codeWrongLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailCodeWrong)

    @Test
    fun codeWrongDark() = capture("codeWrongDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailCodeWrong)

    @Test
    fun floodWaitLight() = capture("floodWaitLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailFloodWait)

    @Test
    fun floodWaitDark() = capture("floodWaitDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailFloodWait)

    @Test
    fun doneLight() = capture("doneLight", ThemeMode.Light, AccountPagePreviewStates.loginEmailDone)

    @Test
    fun doneDark() = capture("doneDark", ThemeMode.Dark, AccountPagePreviewStates.loginEmailDone)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, AccountPagePreviewStates.loginEmailCode)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: LoginEmailUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        LoginEmailScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            LoginEmailScreen(state = AccountPagePreviewStates.loginEmailCode, onEvent = {}, onBack = {})
        }
}
