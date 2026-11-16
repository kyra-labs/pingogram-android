package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.activity.ComponentActivity
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Keyword alerts' page (board 30), light and dark, in AMOLED black, high contrast, right to left, a 700 dp window and
 * at twice the font size (`docs/DESIGN_SYSTEM.md` §12): words, none with suggestions, the most words, and the sheet of
 * folders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class KeywordAlertsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = page("light", ThemeMode.Light)

    @Test
    fun dark() = page("dark", ThemeMode.Dark)

    @Test
    fun emptyLight() = page("emptyLight", ThemeMode.Light, KeywordAlertsPreviewStates.empty)

    @Test
    fun emptyDark() = page("emptyDark", ThemeMode.Dark, KeywordAlertsPreviewStates.empty)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun fullLight() = page("fullLight", ThemeMode.Light, KeywordAlertsPreviewStates.full)

    @Test
    fun foldersLight() = folders("foldersLight", ThemeMode.Light)

    @Test
    fun foldersDark() = folders("foldersDark", ThemeMode.Dark)

    @Test
    fun amoled() = audit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = audit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = audit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = audit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = page("fontScale200", ThemeMode.Light)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun emptyFontScale200() = page("emptyFontScale200", ThemeMode.Light, KeywordAlertsPreviewStates.empty)

    private fun page(
        name: String,
        themeMode: ThemeMode,
        state: KeywordAlertsUiState = KeywordAlertsPreviewStates.words,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { KeywordAlertsScreen(state, onEvent = {}, onBack = {}) }

    private fun folders(
        name: String,
        themeMode: ThemeMode,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { FoldersOnSheet() }

    /** The sheet's content on the bottom sheet's container, as it shows. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FoldersOnSheet() {
        Surface(color = BottomSheetDefaults.ContainerColor) {
            KeywordFoldersContent(KeywordAlertsPreviewStates.words, persistentSetOf(1), onEvent = {})
        }
    }

    private fun audit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            KeywordAlertsScreen(KeywordAlertsPreviewStates.words, onEvent = {}, onBack = {})
        }
}
