package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.InteractionsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of "Seen by" and "Reactions" (`docs/DESIGN_SYSTEM.md` §6 "Seen by and reactions") on a compact phone over
 * the group conversation: a group message's actions with both rows first, and their sheet at the viewers, at every
 * reaction (the next page on its way), at the hearts, loading, empty and failed. Each light, dark, with AMOLED black,
 * in high contrast, right to left, on a 700 dp window and at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageInteractionsScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    /** At 200% font scale the tabs scroll and the rows grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val sheet = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                if (sheet == null) {
                    ChatScreen(state = InteractionsPreviewData.groupActionsSheet, onEvent = {}, onBack = {})
                } else {
                    ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
                    MessageInteractionsBottomSheet(sheet = sheet, onEvent = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        /** Each state's sheet; null for the message's actions, which open it. */
        val STATES: Map<String, MessageInteractionsSheet?> =
            with(InteractionsPreviewData) {
                mapOf(
                    "actions" to null,
                    "seen" to seen,
                    "allReactions" to allReactions,
                    "oneReaction" to oneReaction,
                    "loading" to loading,
                    "empty" to empty,
                    "error" to error,
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
