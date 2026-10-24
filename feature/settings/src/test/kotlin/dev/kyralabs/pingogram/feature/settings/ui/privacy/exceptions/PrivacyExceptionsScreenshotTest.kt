package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.PrivacyPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A privacy setting's exceptions in each of their states and the picker, light and dark; the list in AMOLED black, high
 * contrast, right to left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacyExceptionsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listLight() = capture("listLight", ThemeMode.Light, PrivacyPreviewStates.exceptions)

    @Test
    fun listDark() = capture("listDark", ThemeMode.Dark, PrivacyPreviewStates.exceptions)

    @Test
    fun emptyLight() =
        capture("emptyLight", ThemeMode.Light, PrivacyPreviewStates.exceptionsEmpty, PrivacyExceptionSide.Always)

    @Test
    fun emptyDark() =
        capture("emptyDark", ThemeMode.Dark, PrivacyPreviewStates.exceptionsEmpty, PrivacyExceptionSide.Always)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, PrivacyPreviewStates.exceptionsSaving)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, PrivacyPreviewStates.exceptionsError)

    @Test
    fun pickerLight() = capturePicker("pickerLight", ThemeMode.Light)

    @Test
    fun pickerDark() = capturePicker("pickerDark", ThemeMode.Dark)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, PrivacyPreviewStates.exceptions)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: PrivacyExceptionsUiState,
        side: PrivacyExceptionSide = PrivacyExceptionSide.Never,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        PrivacyExceptionsScreen(PrivacySetting.LastSeen, side, state, onEvent = {}, onBack = {})
    }

    // The sheet's content on the sheet's container, as a ModalBottomSheet window cannot be captured.
    private fun capturePicker(
        name: String,
        themeMode: ThemeMode,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            PrivacyExceptionsPickerContent(PrivacyPreviewStates.exceptionsPicker, onEvent = {})
        }
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            PrivacyExceptionsScreen(
                PrivacySetting.LastSeen,
                PrivacyExceptionSide.Never,
                PrivacyPreviewStates.exceptions,
                onEvent = {},
                onBack = {},
            )
        }
}
