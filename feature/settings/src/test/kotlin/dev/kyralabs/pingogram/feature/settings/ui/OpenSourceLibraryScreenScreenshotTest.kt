package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class OpenSourceLibraryScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.openSourceLibrary)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.openSourceLibrary)

    @Test
    fun withoutTextLight() =
        capture("withoutTextLight", ThemeMode.Light, SettingsPreviewData.openSourceLibraryWithoutText)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: OpenSourceLibraryUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        OpenSourceLibraryScreen(state = state, onEvent = {}, onBack = {})
    }
}
