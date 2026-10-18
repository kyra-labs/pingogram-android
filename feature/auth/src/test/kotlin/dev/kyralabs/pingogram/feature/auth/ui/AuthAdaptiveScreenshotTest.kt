package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The adaptive layout of the login screens (`docs/DESIGN_SYSTEM.md` §7): one centered column of comfortable width on
 * an expanded window and in landscape, and text-heavy screens at 200% font scale (`REQUIREMENTS.md` §6.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AuthAdaptiveScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = EXPANDED)
    fun welcomeExpanded() = capture("welcomeExpanded", AuthPreviewData.welcome)

    @Test
    @Config(qualifiers = EXPANDED)
    fun phoneNumberExpanded() = capture("phoneNumberExpanded", AuthPreviewData.phoneNumberFloodWait)

    @Test
    @Config(qualifiers = EXPANDED)
    fun qrCodeExpanded() = capture("qrCodeExpanded", AuthPreviewData.qrCode)

    @Test
    @Config(qualifiers = EXPANDED)
    fun codeExpanded() = capture("codeExpanded", AuthPreviewData.code)

    @Test
    @Config(qualifiers = EXPANDED)
    fun registrationExpanded() = capture("registrationExpanded", AuthPreviewData.registration)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun welcomeLandscape() = capture("welcomeLandscape", AuthPreviewData.welcome)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun phoneNumberLandscape() = capture("phoneNumberLandscape", AuthPreviewData.phoneNumber)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun welcomeFontScale200() = capture("welcomeFontScale200", AuthPreviewData.welcome)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun codePhraseFontScale200() = capture("codePhraseFontScale200", AuthPreviewData.codePhrase)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun registrationFontScale200() = capture("registrationFontScale200", AuthPreviewData.registration)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun premiumRequiredFontScale200() = capture("premiumRequiredFontScale200", AuthPreviewData.premiumRequired)

    /** The dynamic goldens of [AuthScreenScreenshotTest] only show Material You if it differs from the brand. */
    @Test
    fun `dynamic color differs from the brand colors`() {
        var brand = Color.Unspecified
        var dynamic = Color.Unspecified
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                brand = MaterialTheme.colorScheme.primary
            }
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = true) {
                dynamic = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()

        assertNotEquals(brand, dynamic)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        state: AuthUiState,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                AuthScreen(state = state, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
