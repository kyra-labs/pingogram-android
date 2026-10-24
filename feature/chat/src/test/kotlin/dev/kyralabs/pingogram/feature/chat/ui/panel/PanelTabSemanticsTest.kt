package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The panel's tabs (Emoji and Stickers, and each tab's row of sections) are tabs for screen readers: "Tab, selected",
 * never the toggle buttons' "Tab, checked" (`docs/audit/accessibility.md` A14).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PanelTabSemanticsTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val isNotToggle = SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState)

    @Test
    fun `the panel's tabs say whether they are selected, not checked, and select on a click`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                EmojiPanel(
                    state = PanelPreviewData.content,
                    onEmoji = {},
                    onBackspace = {},
                    onSticker = {},
                    onEvent = {},
                    modifier = Modifier.fillMaxWidth().height(PANEL_HEIGHT.dp),
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Emoji")
            .assert(isTab)
            .assert(isNotToggle)
            .assertIsSelected()
        val stickers = composeRule.onNodeWithContentDescription("Stickers")
        stickers.assert(isTab).assert(isNotToggle).assertIsNotSelected()

        stickers.performClick()

        composeRule.onNodeWithContentDescription("Stickers").assertIsSelected()
    }
}

private const val PANEL_HEIGHT = 320
