package dev.kyralabs.pingogram.feature.chat.ui.drop

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
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
 * Goldens of the drop zone over the conversation (the owner's board 35a), on a tablet and in split screen: photos
 * hovered, light and dark; files not hovered yet; right to left; 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h1000dp-xhdpi")
class ChatDropZoneScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mediaHovered() = capture("mediaHovered", ScreenshotVariant.Light, ChatDropState(true, true, isMedia = true))

    @Test
    fun mediaHoveredDark() = capture("mediaHoveredDark", ScreenshotVariant.Dark, ChatDropState(true, true, true))

    @Test
    fun files() = capture("files", ScreenshotVariant.Light, ChatDropState(isActive = true))

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl, ChatDropState(true, true, true))

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun splitScreenFontScale200() =
        capture("splitScreenFontScale200", ScreenshotVariant.Light, ChatDropState(true, false, true))

    @Test
    fun `the drop zone is accessible`() {
        composeRule.setContent {
            ScreenshotTheme(ScreenshotVariant.Light) {
                Box(Modifier.fillMaxSize()) { ChatDropZone(ChatDropState(isActive = true), "Ada Lovelace") }
            }
        }
        composeRule.assertAccessible()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: ChatDropState,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    ChatDropZone(state, chatTitle = "Ada Lovelace")
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
