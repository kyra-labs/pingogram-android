package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat list with folders in a few of the generated color themes (`docs/adr/0015-color-themes.md`): every role it
 * uses (tabs, badges, avatars, the sheet and its bar) in another palette than the brand's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListThemesScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tealLight() = capture("tealLight", ThemeMode.Light, ThemePalette.Teal)

    @Test
    fun pinkDark() = capture("pinkDark", ThemeMode.Dark, ThemePalette.Pink)

    @Test
    fun monochromeLight() = capture("monochromeLight", ThemeMode.Light, ThemePalette.Monochrome)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        palette: ThemePalette,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, palette = palette) {
                ChatListScreen(state = ChatListPreviewData.contentWithFolders, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}
