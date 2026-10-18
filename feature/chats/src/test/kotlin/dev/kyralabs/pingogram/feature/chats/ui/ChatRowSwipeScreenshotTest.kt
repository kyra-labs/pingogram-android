package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row held mid-swipe, past the threshold (`docs/DESIGN_SYSTEM.md` §5): the action's container, icon at full size and
 * label behind it. The finger stays down, so the row stays where it was dragged; the icon's spring has settled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatRowSwipeScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Grace Hopper's row dragged towards the end: mark as unread, on `secondaryContainer`. */
    @Test
    fun swipeToReadLight() = capture("swipeToReadLight", ThemeMode.Light, SWIPE_FRACTION)

    /** Grace Hopper's row dragged towards the start: archive, on `tertiaryContainer`. */
    @Test
    fun swipeToArchiveDark() = capture("swipeToArchiveDark", ThemeMode.Dark, -SWIPE_FRACTION)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        fraction: Float,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatListScreen(state = ChatListPreviewData.content, onEvent = {})
            }
        }
        composeRule
            .onNode(
                hasContentDescription("Grace Hopper,", substring = true) and hasClickAction(),
            ).performTouchInput {
                down(center)
                moveBy(Offset(width * fraction, 0f))
            }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        /** Well past the swipe's threshold, with the label still clear of the moved row. */
        const val SWIPE_FRACTION = 0.6f
    }
}
