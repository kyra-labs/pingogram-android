package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
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
class ExperimentalUiScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun experimentalLight() =
        composeRule.captureScreen(javaClass, "experimentalLight", ThemeMode.Light) { ExperimentalHome() }

    @Test
    fun experimentalDark() =
        composeRule.captureScreen(javaClass, "experimentalDark", ThemeMode.Dark) { ExperimentalHome() }

    @Test
    fun experimentalAmoled() =
        composeRule.captureScreen(javaClass, "experimentalAmoled", ScreenshotVariant.Amoled) { ExperimentalHome() }

    @Test
    fun experimentalHighContrast() =
        composeRule.captureScreen(javaClass, "experimentalHighContrast", ScreenshotVariant.HighContrast) {
            ExperimentalHome()
        }

    @Test
    fun experimentalRtl() =
        composeRule.captureScreen(javaClass, "experimentalRtl", ScreenshotVariant.Rtl) { ExperimentalHome() }

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun experimentalFontScale200() =
        composeRule.captureScreen(javaClass, "experimentalFontScale200", ThemeMode.Light) { ExperimentalHome() }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun experimentalWholePage() =
        composeRule.captureScreen(javaClass, "experimentalWholePage", ThemeMode.Light) {
            ExperimentalHome(SettingsPreviewData.withOtherAccounts)
        }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun experimentalWholePageDark() =
        composeRule.captureScreen(javaClass, "experimentalWholePageDark", ThemeMode.Dark) {
            ExperimentalHome(SettingsPreviewData.withOtherAccounts)
        }
}

/** Settings home in the experimental look, with the values beside its rows. */
@Composable
private fun ExperimentalHome(state: SettingsUiState = SettingsPreviewData.content) {
    CompositionLocalProvider(LocalExperimentalUi provides true) {
        SettingsScreen(
            state = state,
            onEvent = {},
            onOpenPage = {},
            summaries = SettingsPreviewData.homeSummaries,
        )
    }
}
