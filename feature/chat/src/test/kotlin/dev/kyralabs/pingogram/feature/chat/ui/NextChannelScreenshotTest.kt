package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.NextChannelPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a read channel pulled up toward the next unread channel (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6
 * "Next unread channel"): halfway, and past the threshold in every look the audit asks for (§12), on a 700 dp window
 * and at 200% font, the finger still down. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NextChannelScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun halfway() = capture("halfway", ScreenshotVariant.Light, HALF_PULL)

    @Test
    fun light() = capture("armed", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("armed", ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture("armed", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("armed", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("armed", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture("armed", ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture("armed.fontScale200", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        distance: Int = FULL_PULL,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(
                    state = ChatPreviewData.channel,
                    onEvent = {},
                    onBack = {},
                    nextChannel = NextChannelPreviewData.next,
                )
            }
        }
        composeRule.waitForIdle()
        pullNextChannel(composeRule, distance, release = false)
        captureScreenRoboImage(goldenPath(javaClass, "$name.${variant.goldenName}"))
    }

    private companion object {
        const val HALF_PULL = 96
        const val FULL_PULL = 240
    }
}
