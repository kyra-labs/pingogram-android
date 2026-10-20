package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Language page: loading, English only and the system default chosen with Android's own settings to open (Android
 * 13+), several languages with one chosen before Android 13, and at 200% font scale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LanguageScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, LanguageUiState.Loading)

    @Test
    fun light() = capture("light", ThemeMode.Light, englishOnly)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, englishOnly)

    @Test
    fun translatedBeforeAndroid13Dark() =
        capture(
            "translatedBeforeAndroid13Dark",
            ThemeMode.Dark,
            LanguageUiState.Content(persistentListOf("en", "de", "pt-BR"), selected = "pt-BR"),
            systemLanguage = "de-DE",
            androidSettings = false,
        )

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, englishOnly)

    private val englishOnly = LanguageUiState.Content(persistentListOf("en"), selected = null)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: LanguageUiState,
        systemLanguage: String = "en-US",
        androidSettings: Boolean = true,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        LanguageScreen(
            state = state,
            onEvent = {},
            onBack = {},
            systemLanguage = systemLanguage,
            onOpenAndroidSettings = if (androidSettings) ({}) else null,
        )
    }
}
