package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the reminder sheet's content (the owner's board 29b): late in the evening light and dark, before 20:00
 * with "This evening", a message with a reminder already, AMOLED, high contrast, right to left and 200% font. Brand
 * colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ReminderSheetScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun existing() = capture("existing", ScreenshotVariant.Light, ReminderSheetPreviewData.existing)

    @Test
    fun afternoon() = capture("afternoon", ScreenshotVariant.Light, ReminderSheetPreviewData.afternoon)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: ReminderSheetUiState = ReminderSheetPreviewData.new,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ReminderSheetContent(state, onEvent = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
