package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The label under a message that failed to send ([NotSentLabel]): "Not sent · Tap to retry" is a button with a 48 dp
 * touch target that takes no more room than the label, and sends the message again; "Not sent" alone is no button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NotSentLabelTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var retries = 0

    @Test
    fun `tapping the label sends the message again`() {
        setLabel(canRetry = true)

        composeRule.onNodeWithText(string(R.string.chat_not_sent_retry)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `the label is a 48 dp touch target and a button, and takes no more room than its look`() {
        setLabel(canRetry = true)

        composeRule
            .onNodeWithText(string(R.string.chat_not_sent_retry))
            .assertTouchHeightIsEqualTo(MIN_TOUCH_TARGET)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val height = composeRule.onNodeWithTag(LABEL).getUnclippedBoundsInRoot().height
        assertTrue("$height", height <= LABEL_HEIGHT)
    }

    @Test
    fun `a message that cannot be sent again says only that it was not sent`() {
        setLabel(canRetry = false)

        composeRule.onNodeWithText(string(R.string.chat_not_sent)).assertHasNoClickAction()
    }

    private fun setLabel(canRetry: Boolean) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                // Room around the label, where its touch target reaches past it.
                Box(Modifier.padding(MIN_TOUCH_TARGET)) {
                    Box(Modifier.testTag(LABEL)) { NotSentLabel(canRetry, onRetry = { retries++ }) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val LABEL = "label"

        /** Material's minimum touch target. */
        val MIN_TOUCH_TARGET = 48.dp

        /** A line of `labelMedium` (16 sp) and the label's padding above and below it, at 100% font. */
        val LABEL_HEIGHT = 24.dp
    }
}
