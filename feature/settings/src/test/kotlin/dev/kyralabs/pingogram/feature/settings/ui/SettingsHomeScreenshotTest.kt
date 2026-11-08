package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings home in Pingogram's look (`docs/DESIGN_SYSTEM.md` §13.9): light, dark, AMOLED black, high contrast, right to
 * left, at the largest font scale and as a whole page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsHomeScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = composeRule.captureScreen(javaClass, "light", ThemeMode.Light) { HomeWithValues() }

    @Test
    fun dark() = composeRule.captureScreen(javaClass, "dark", ThemeMode.Dark) { HomeWithValues() }

    @Test
    fun amoled() = composeRule.captureScreen(javaClass, "amoled", ScreenshotVariant.Amoled) { HomeWithValues() }

    @Test
    fun highContrast() =
        composeRule.captureScreen(javaClass, "highContrast", ScreenshotVariant.HighContrast) {
            HomeWithValues()
        }

    @Test
    fun rtl() = composeRule.captureScreen(javaClass, "rtl", ScreenshotVariant.Rtl) { HomeWithValues() }

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = composeRule.captureScreen(javaClass, "fontScale200", ThemeMode.Light) { HomeWithValues() }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun wholePage() =
        composeRule.captureScreen(javaClass, "wholePage", ThemeMode.Light) {
            HomeWithValues(SettingsPreviewData.withOtherAccounts)
        }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun wholePageDark() =
        composeRule.captureScreen(javaClass, "wholePageDark", ThemeMode.Dark) {
            HomeWithValues(SettingsPreviewData.withOtherAccounts)
        }
}

/** Settings home with the values beside its rows. */
@Composable
private fun HomeWithValues(state: SettingsUiState = SettingsPreviewData.content) {
    SettingsScreen(
        state = state,
        onEvent = {},
        onOpenPage = {},
        summaries = SettingsPreviewData.homeSummaries,
    )
}
