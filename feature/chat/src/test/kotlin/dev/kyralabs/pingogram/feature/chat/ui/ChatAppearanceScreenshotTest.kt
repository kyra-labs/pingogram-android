package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a conversation with the user's Chat appearance (`REQUIREMENTS.md` FR-S1, `docs/DESIGN_SYSTEM.md` §3.2,
 * §4): the message text at its largest and smallest, the bubbles with `medium` and `extraLargeIncreased` corners, and a
 * plain and a gradient wallpaper, light and dark. A group chat, so joined bubbles show their corners. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatAppearanceScreenshotTest(
    private val appearanceName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val appearance = APPEARANCES.getValue(appearanceName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, chat = appearance) {
                ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$appearanceName.$variant"))
    }

    companion object {
        val APPEARANCES: Map<String, ChatAppearance> =
            mapOf(
                "largeText" to
                    ChatAppearance(
                        textScale = ChatAppearance.MAX_TEXT_SCALE,
                        bubbleCorners = BubbleCorners.Medium,
                        wallpaper = ChatWallpaper.Primary,
                    ),
                "smallText" to
                    ChatAppearance(
                        textScale = ChatAppearance.MIN_TEXT_SCALE,
                        bubbleCorners = BubbleCorners.ExtraLargeIncreased,
                        wallpaper = ChatWallpaper.PrimaryToTertiary,
                    ),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun appearances(): List<Array<Any>> = APPEARANCES.keys.map { arrayOf(it) }
    }
}
