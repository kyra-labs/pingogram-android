package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
 * Goldens of a chat's Apply choices, as its sheet shows them: without Telegram Premium "Apply for me and <name>" is
 * locked, with it both are offered; and the audit's looks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperApplySheetScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lockedLight() = composeRule.captureScreen(javaClass, "lockedLight", ThemeMode.Light) { Choices(false) }

    @Test
    fun premiumDark() = composeRule.captureScreen(javaClass, "premiumDark", ThemeMode.Dark) { Choices(true) }

    @Test
    fun amoled() = composeRule.captureScreen(javaClass, "amoled", ScreenshotVariant.Amoled) { Choices(false) }

    @Test
    fun highContrast() =
        composeRule.captureScreen(javaClass, "highContrast", ScreenshotVariant.HighContrast) { Choices(false) }

    @Test
    fun rtl() = composeRule.captureScreen(javaClass, "rtl", ScreenshotVariant.Rtl) { Choices(false) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = composeRule.captureScreen(javaClass, "fontScale200", ThemeMode.Light) { Choices(false) }

    @Composable
    private fun Choices(canSetForBoth: Boolean) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            WallpaperApplyChoices(chatName = "Meera Krishnan", canSetForBoth = canSetForBoth, onApply = {})
        }
    }
}
