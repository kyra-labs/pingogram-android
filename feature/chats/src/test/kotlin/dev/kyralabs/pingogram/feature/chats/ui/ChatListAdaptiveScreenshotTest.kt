package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * The chat list on an expanded window, where its rows keep a comfortable width (`docs/DESIGN_SYSTEM.md` §7), and at
 * 200% font scale, where rows grow instead of clipping (`REQUIREMENTS.md` §6.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListAdaptiveScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() = capture("expandedDark", ThemeMode.Dark)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatListScreen(state = ChatListPreviewData.contentWithFolders, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
