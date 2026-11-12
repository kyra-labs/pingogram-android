package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A shared style's sheet (`docs/DESIGN_SYSTEM.md` §6 "AI text editor", §5.1): the progress line while Telegram
 * answers keeps its place before and after, so the sheet keeps its height: its top, the title, does not move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TextStyleSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the sheet keeps its height while Telegram answers and after`() {
        var state by mutableStateOf(AiEditorPreviewData.style)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                TextStyleSheet(state, onEvent = {}, onClose = {})
            }
        }
        composeRule.waitForIdle()
        val atRest = titleTop()

        state = AiEditorPreviewData.style.copy(busy = true)
        composeRule.waitForIdle()
        val answering = titleTop()

        state = AiEditorPreviewData.style
        composeRule.waitForIdle()

        assertEquals(atRest, answering)
        assertEquals(atRest, titleTop())
    }

    private fun titleTop() = composeRule.onNodeWithText(TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private companion object {
        const val TITLE = "Pirate"
    }
}
