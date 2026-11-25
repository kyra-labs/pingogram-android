package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The emoji tab's cells: each is one button that screen readers read as its emoji and a tap puts into the text, drawn
 * from the tab's kept layouts with no text of its own composed and laid out inside it (`docs/PERFORMANCE.md` §5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class EmojiCellTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tapped = mutableListOf<String>()

    private fun panel() =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                EmojiPanel(
                    state = PanelPreviewData.content,
                    onEmoji = { tapped += it },
                    onBackspace = {},
                    onSticker = {},
                    onEvent = {},
                    modifier = Modifier.fillMaxWidth().height(PANEL_HEIGHT.dp),
                )
            }
        }

    @Test
    fun `a cell is one button read as its emoji, with no text inside it`() {
        panel()

        val cell = composeRule.onAllNodesWithText(EMOJI, useUnmergedTree = true).onFirst()

        cell.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(emptyList<Any>(), cell.fetchSemanticsNode().children)
    }

    @Test
    fun `a tap puts its emoji into the text`() {
        panel()

        composeRule.onAllNodesWithText(EMOJI).onFirst().performClick()

        assertEquals(listOf(EMOJI), tapped)
    }

    private companion object {
        const val PANEL_HEIGHT = 320

        /** The first of the recent emoji in the preview's panel (also among the smileys). */
        const val EMOJI = "😂"
    }
}
