package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The conversation on an expanded window, where bubbles keep a readable width (`docs/DESIGN_SYSTEM.md` §7), and at
 * 200% font scale, where bubbles and the top bar grow instead of clipping (`REQUIREMENTS.md` §6.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatAdaptiveScreenshotTest {
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

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200Private() = capture("fontScale200Private", ThemeMode.Light, ChatPreviewData.privateChat)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChatUiState = ChatPreviewData.groupChat,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = state, onEvent = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
