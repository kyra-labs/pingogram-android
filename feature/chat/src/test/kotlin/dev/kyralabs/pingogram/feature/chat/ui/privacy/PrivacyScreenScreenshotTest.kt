package dev.kyralabs.pingogram.feature.chat.ui.privacy

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.privacy.LocalPrivacyScreen
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Goldens of a group's conversation behind the privacy screen (FR-X25), its text blurred and its photo hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacyScreenScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalPrivacyScreen provides true) {
                    ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, variant.goldenName))
    }
}
