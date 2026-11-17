package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The link under a long press ([linkTarget], [onLongPress]'s `onPressedLink`): the row's long press finds the link of
 * the text inside it drawn where it landed, and nothing on the text's other words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LinkTargetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pressed = mutableListOf<String?>()

    private fun setText() {
        val text =
            buildAnnotatedString {
                append("Read ")
                withLink(LinkAnnotation.Url(URL)) { append(URL) }
            }
        composeRule.setContent {
            val layout = remember { TextLayoutHolder() }
            Box(Modifier.onLongPress(onLongPress = {}, onPressedLink = { pressed += it })) {
                Text(
                    text = text,
                    modifier = Modifier.testTag(TEXT).linkTarget(layout),
                    onTextLayout = { layout.result = it },
                )
            }
        }
    }

    @Test
    fun `a long press on a link finds its address`() {
        setText()

        composeRule.onNodeWithTag(TEXT).performTouchInput { longClick(Offset(width - 2f, centerY)) }
        composeRule.waitForIdle()

        assertEquals(listOf(URL), pressed)
    }

    @Test
    fun `a long press on the other words finds none`() {
        setText()

        composeRule.onNodeWithTag(TEXT).performTouchInput { longClick(Offset(2f, centerY)) }
        composeRule.waitForIdle()

        assertEquals(listOf<String?>(null), pressed)
    }

    private companion object {
        const val TEXT = "text"
        const val URL = "https://example.com"
    }
}
