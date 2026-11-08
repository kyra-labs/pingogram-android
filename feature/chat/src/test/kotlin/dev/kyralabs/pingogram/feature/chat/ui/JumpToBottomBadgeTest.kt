package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The jump button's unread badge (`docs/DESIGN_SYSTEM.md` §6 "Jump to bottom"): a count of four characters stays on
 * screen, growing toward the start ([WithCornerBadge]), in every look the audit asks for (§12) and at 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class JumpToBottomBadgeTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a long unread count stays within the window's margin`() {
        setScreen(ScreenshotVariant.Light)

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val badge = composeRule.onNodeWithText(OVERFLOW).fetchSemanticsNode().boundsInRoot
        val margin = with(composeRule.density) { 8.dp.toPx() }
        assertTrue("Badge $badge in window $window", badge.right <= window.right - margin)
    }

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

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200.")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        prefix: String = "",
    ) {
        setScreen(variant)
        captureScreenRoboImage(goldenPath(javaClass, "unread.$prefix${variant.goldenName}"))
    }

    private fun setScreen(variant: ScreenshotVariant) {
        val state = ChatPreviewData.privateChat
        val content = state.content as ChatContent.Content
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(
                    state = state.copy(content = content.copy(reachedEnd = false), unreadCount = UNREAD),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val UNREAD = 1_234
        const val OVERFLOW = "999+"
    }
}
