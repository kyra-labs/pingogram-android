package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChangePhoneNumberPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Change number in each of its states, light and dark; the code step in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChangePhoneNumberScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun introLight() = capture("introLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.intro)

    @Test
    fun introDark() = capture("introDark", ThemeMode.Dark, ChangePhoneNumberPreviewStates.intro)

    @Test
    fun numberLight() = capture("numberLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.number)

    @Test
    fun numberDark() = capture("numberDark", ThemeMode.Dark, ChangePhoneNumberPreviewStates.number)

    @Test
    fun occupiedLight() = capture("occupiedLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.numberOccupied)

    @Test
    fun sendingLight() = capture("sendingLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.sending)

    @Test
    fun codeLight() = capture("codeLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.code)

    @Test
    fun codeDark() = capture("codeDark", ThemeMode.Dark, ChangePhoneNumberPreviewStates.code)

    @Test
    fun codeWrongLight() = capture("codeWrongLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.codeWrong)

    @Test
    fun codeWrongDark() = capture("codeWrongDark", ThemeMode.Dark, ChangePhoneNumberPreviewStates.codeWrong)

    @Test
    fun floodWaitLight() = capture("floodWaitLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.floodWait)

    @Test
    fun doneLight() = capture("doneLight", ThemeMode.Light, ChangePhoneNumberPreviewStates.done)

    @Test
    fun doneDark() = capture("doneDark", ThemeMode.Dark, ChangePhoneNumberPreviewStates.done)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ChangePhoneNumberPreviewStates.code)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChangePhoneNumberUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChangePhoneNumberScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ChangePhoneNumberScreen(state = ChangePhoneNumberPreviewStates.code, onEvent = {}, onBack = {})
        }
}
