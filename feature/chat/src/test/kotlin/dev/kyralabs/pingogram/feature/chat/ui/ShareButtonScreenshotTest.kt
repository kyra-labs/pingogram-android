package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ShareButtonPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the share button beside a channel's posts (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Share button"):
 * a one-line post, one with reactions and a photo, in every look the audit asks for (§12), on a 700 dp window and at
 * 200% font. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ShareButtonScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("channel", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("channel", ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture("channel", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("channel", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("channel", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture("channel", ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture("channel.fontScale200", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = ShareButtonPreviewData.channel, onEvent = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$name.${variant.goldenName}"))
    }
}
