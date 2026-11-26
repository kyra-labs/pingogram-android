package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Muted words' page (FR-X21), light and dark, in AMOLED black, high contrast, right to left, a 700 dp window and at
 * twice the font size (`docs/DESIGN_SYSTEM.md` §12): on with words, off without any, and the most words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MutedWordsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = page("light", ThemeMode.Light)

    @Test
    fun dark() = page("dark", ThemeMode.Dark)

    @Test
    fun emptyLight() = page("emptyLight", ThemeMode.Light, MutedWordsPreviewStates.empty)

    @Test
    fun emptyDark() = page("emptyDark", ThemeMode.Dark, MutedWordsPreviewStates.empty)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun fullLight() = page("fullLight", ThemeMode.Light, MutedWordsPreviewStates.full)

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

    private fun page(
        name: String,
        themeMode: ThemeMode,
        state: MutedWordsUiState = MutedWordsPreviewStates.words,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { MutedWordsScreen(state, onEvent = {}, onBack = {}) }

    private fun audit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            MutedWordsScreen(MutedWordsPreviewStates.words, onEvent = {}, onBack = {})
        }
}
