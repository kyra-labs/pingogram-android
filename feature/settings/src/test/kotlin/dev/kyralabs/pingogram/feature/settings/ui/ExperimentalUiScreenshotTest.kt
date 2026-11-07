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
 * Settings home's "Experimental UI" (`docs/adr/0035-experimental-ui.md`) in both looks: the standard look's switch row
 * with its "Beta" label, off, on the page's color; and Settings home in the experimental look with the labs card under
 * the account card. Light, dark, AMOLED black, high contrast, right to left and at the largest font scale. The standard
 * Settings home's goldens ([SettingsScreenScreenshotTest]) are unchanged: their state has the setting not yet read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalUiScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rowLight() = composeRule.captureScreen(javaClass, "rowLight", ThemeMode.Light) { Row() }

    @Test
    fun rowDark() = composeRule.captureScreen(javaClass, "rowDark", ThemeMode.Dark) { Row() }

    @Test
    fun rowAmoled() = composeRule.captureScreen(javaClass, "rowAmoled", ScreenshotVariant.Amoled) { Row() }

    @Test
    fun rowHighContrast() =
        composeRule.captureScreen(javaClass, "rowHighContrast", ScreenshotVariant.HighContrast) { Row() }

    @Test
    fun rowRtl() = composeRule.captureScreen(javaClass, "rowRtl", ScreenshotVariant.Rtl) { Row() }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun rowFontScale200() = composeRule.captureScreen(javaClass, "rowFontScale200", ThemeMode.Light) { Row() }

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

/** The standard look's row in a group of its own, as Settings home shows it. */
@Composable
private fun Row() {
    Box(
        Modifier
            .fillMaxSize()
            .background(settingsPageColor())
            .padding(MaterialTheme.spacing.large),
    ) {
        SettingsGroup(iconTone = SettingsIconTone.Tertiary) {
            item { shapes -> ExperimentalUiItem(checked = false, shapes = shapes, onCheckedChange = {}) }
        }
    }
}

/** Settings home in the experimental look, the labs card on, with the values beside its rows. */
@Composable
private fun ExperimentalHome(state: SettingsUiState = SettingsPreviewData.content) {
    CompositionLocalProvider(LocalExperimentalUi provides true) {
        SettingsScreen(
            state = state,
            onEvent = {},
            onOpenPage = {},
            experimentalUi = true,
            summaries = SettingsPreviewData.homeSummaries,
        )
    }
}
