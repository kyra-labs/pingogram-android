package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ShareButtonPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The share button beside channel posts (`docs/DESIGN_SYSTEM.md` §6 "Share button"): on the bubble's far side, its
 * circle `spacing.small` from it and level with its bottom, mirrored for outgoing posts and right to left; a tap
 * forwards the post, which screen readers do through the bubble's custom action; it has a name and a 48 dp target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ShareButtonScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var shares = 0
    private val forward get() = RuntimeEnvironment.getApplication().getString(R.string.message_action_forward)

    private fun setButton(
        isOutgoing: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box {
                        WithShareButton(
                            isOutgoing = isOutgoing,
                            style = rememberShareButtonStyle(),
                            onShare = { shares++ },
                            modifier = Modifier,
                        ) {
                            Box(Modifier.size(BUBBLE_WIDTH.dp, BUBBLE_HEIGHT.dp).testTag(BUBBLE))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a tap on the button shares the post`() {
        setButton()

        composeRule.onNodeWithContentDescription(forward).performClick()

        assertEquals(1, shares)
    }

    @Test
    fun `the button sits after an incoming bubble, its circle level with the bubble's bottom`() {
        setButton()
        val bubble = composeRule.onNodeWithTag(BUBBLE).getBoundsInRoot()
        val button = composeRule.onNodeWithContentDescription(forward).getBoundsInRoot()

        // The button's bounds are its 40 dp circle, inside its 48 dp target.
        assertEquals(bubble.right + 8.dp, button.left)
        assertEquals(bubble.bottom, button.bottom)
    }

    @Test
    fun `the button sits before an outgoing bubble`() {
        setButton(isOutgoing = true)
        val bubble = composeRule.onNodeWithTag(BUBBLE).getBoundsInRoot()
        val button = composeRule.onNodeWithContentDescription(forward).getBoundsInRoot()

        assertEquals(bubble.left - 8.dp, button.right)
    }

    @Test
    fun `right to left, the button sits on the bubble's left`() {
        setButton(direction = LayoutDirection.Rtl)
        val bubble = composeRule.onNodeWithTag(BUBBLE).getBoundsInRoot()
        val button = composeRule.onNodeWithContentDescription(forward).getBoundsInRoot()

        assertTrue("$button before $bubble", button.right < bubble.left)
    }

    @Test
    fun `screen readers forward a channel post through its custom action`() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = ShareButtonPreviewData.channel, onEvent = { events += it }, onBack = {})
            }
        }
        val node = composeRule.onNodeWithContentDescription("New icons are out.", substring = true).fetchSemanticsNode()

        val action = node.config[SemanticsActions.CustomActions].single { it.label == forward }
        composeRule.runOnIdle { action.action() }

        assertEquals(listOf(ChatEvent.Forward(33)), events.filterIsInstance<ChatEvent.Forward>())
    }

    @Test
    fun `the button has a name and a 48 dp target`() {
        setButton()

        composeRule.assertAccessible()
    }

    private companion object {
        const val BUBBLE = "bubble"
        const val BUBBLE_WIDTH = 200
        const val BUBBLE_HEIGHT = 80
    }
}
