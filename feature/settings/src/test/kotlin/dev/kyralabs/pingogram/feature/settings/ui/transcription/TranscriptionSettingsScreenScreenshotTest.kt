package dev.kyralabs.pingogram.feature.settings.ui.transcription

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page of voice message transcription (board 28d): the switch with the languages, the languages being asked for, a
 * device that cannot transcribe, and the audit's variants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TranscriptionSettingsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, TranscriptionSettingsPreviewData.content)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, TranscriptionSettingsPreviewData.content)

    @Test
    fun languagesLoading() =
        capture("languagesLoading", ThemeMode.Light, TranscriptionSettingsPreviewData.languagesLoading)

    @Test
    fun unavailable() = capture("unavailable", ThemeMode.Light, TranscriptionSettingsUiState.Unavailable)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, TranscriptionSettingsPreviewData.content)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            TranscriptionSettingsScreen(TranscriptionSettingsPreviewData.content, onEvent = {}, onBack = {})
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: TranscriptionSettingsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        TranscriptionSettingsScreen(state, onEvent = {}, onBack = {})
    }
}
