package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.SendAsPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsSheet
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of send-as on a compact phone, light and dark (`docs/TESTING.md`): a group's composer sending as a channel,
 * with its avatar before the attach button; the picker over it, a channel chosen and one needing Telegram Premium; the
 * picker while the new choice goes to TDLib. The picker also at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SendAsScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    /** At 200% font scale the rows grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "picker")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val sheet = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = SendAsPreviewData.groupChat, onEvent = {}, onBack = {})
                sheet?.let { SendAsBottomSheet(sheet = it, onEvent = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, SendAsSheet?> =
            mapOf(
                "composer" to null,
                "picker" to SendAsPreviewData.sheet,
                "changing" to SendAsPreviewData.changing,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
