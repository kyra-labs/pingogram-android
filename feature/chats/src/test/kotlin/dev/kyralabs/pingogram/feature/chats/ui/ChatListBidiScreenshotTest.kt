package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of previews that put names into strings of another direction (`docs/DESIGN_SYSTEM.md` §9): "שרה: See you
 * at 9!", "שרה is typing…" and "Ada: שלום!" keep their words and punctuation in order, left to right and right to left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListBidiScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun leftToRight() = capture(ScreenshotVariant.Light)

    @Test
    fun rightToLeft() = capture(ScreenshotVariant.Rtl)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatListScreen(state = ChatListPreviewData.namesInOtherScripts, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "namesInOtherScripts.${variant.goldenName}"))
    }
}
