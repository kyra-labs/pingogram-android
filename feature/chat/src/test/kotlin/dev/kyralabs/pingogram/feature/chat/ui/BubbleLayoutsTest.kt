package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The custom layouts of a text bubble and a media bubble ([rememberTextBubbleMeasurePolicy],
 * [rememberMediaBubbleMeasurePolicy]): every piece is measured once per pass (`docs/PERFORMANCE.md` §4), and the meta
 * follows the text's last line where it fits, else takes a line of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BubbleLayoutsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val measures = mutableMapOf<String, Int>()

    @Test
    fun `a text bubble measures each of its pieces once`() {
        setTextBubble("Hello")

        assertEquals(mapOf(HEADER to 1, TEXT to 1, META to 1), measures)
    }

    @Test
    fun `the meta follows the text's last line where it fits`() {
        setTextBubble("Hello")

        val text = bounds(TEXT)
        val meta = bounds(META)
        assertEquals(text.bottom, meta.bottom)
        assertTrue(meta.left > text.right)
    }

    @Test
    fun `the meta takes a line of its own where the last line has no room for it`() {
        setTextBubble("Hello", width = 200.dp, metaWidth = 160.dp)

        val text = bounds(TEXT)
        val meta = bounds(META)
        assertEquals(text.bottom, meta.top)
        assertEquals(text.left, meta.left)
    }

    @Test
    fun `a media bubble measures each of its pieces once and puts the overlay meta over the media's bottom end`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val textLayout = remember { TextLayoutHolder() }
                Layout(
                    contents =
                        listOf(
                            { Piece(HEADER, 80.dp, 20.dp) },
                            { Piece(MEDIA, 240.dp, 160.dp) },
                            { Piece(META, 60.dp, 16.dp) },
                            {},
                            {},
                            {},
                        ),
                    modifier = Modifier.width(240.dp),
                    measurePolicy = rememberMediaBubbleMeasurePolicy(textLayout),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(mapOf(HEADER to 1, MEDIA to 1, META to 1), measures)
        val media = bounds(MEDIA)
        val meta = bounds(META)
        assertTrue(media.top > bounds(HEADER).bottom)
        assertTrue(meta.right < media.right && meta.bottom < media.bottom && meta.top > media.top)
    }

    private fun setTextBubble(
        text: String,
        width: Dp = 300.dp,
        metaWidth: Dp = 60.dp,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val textLayout = remember { TextLayoutHolder() }
                Layout(
                    contents =
                        listOf(
                            { Piece(HEADER, 80.dp, 20.dp) },
                            {
                                Text(
                                    text = text,
                                    modifier = Modifier.counted(TEXT),
                                    onTextLayout = { textLayout.result = it },
                                )
                            },
                            {},
                            { Piece(META, metaWidth, 16.dp) },
                        ),
                    modifier = Modifier.width(width),
                    measurePolicy = rememberTextBubbleMeasurePolicy(textLayout),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Piece(
        tag: String,
        width: Dp,
        height: Dp,
    ) {
        Spacer(Modifier.counted(tag).size(width, height))
    }

    private fun Modifier.counted(tag: String): Modifier =
        testTag(tag).layout { measurable, constraints ->
            measures[tag] = (measures[tag] ?: 0) + 1
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private companion object {
        const val HEADER = "header"
        const val TEXT = "text"
        const val MEDIA = "media"
        const val META = "meta"
    }
}
