package dev.kyralabs.pingogram.feature.chat.ui.muted

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.MutedMessageEvent
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a group's conversation with a message collapsed for a muted word (FR-X21), on a compact phone, light,
 * dark, right to left and at 200% font, and the event of tapping it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MutedMessageRowScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @Test
    fun `tapping the line shows its message`() {
        setScreen(ScreenshotVariant.Light)
        composeRule.assertAccessible()

        composeRule.onNodeWithText("muted word", substring = true).performScrollTo().performClick()

        assertEquals(listOf(MutedMessageEvent.Show(MOTION)), events.filterIsInstance<MutedMessageEvent>())
    }

    private fun setScreen(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) { ChatScreen(state = STATE, onEvent = { events += it }, onBack = {}) }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        setScreen(variant)
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f

        /** The preview group's message about motion. */
        const val MOTION = 24L

        val STATE =
            MutedMessages().apply(
                ChatPreviewData.groupChat,
                MutedWordsFilter(persistentListOf("motion"), myUserId = 1L, shown = persistentSetOf()),
            )
    }
}
