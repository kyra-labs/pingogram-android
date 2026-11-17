package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A forum's row opens the topic a tap lands on (the newest topic's tab, or a name after it), and is the row's own
 * everywhere else and for a long press. The row is a clickable box around [ForumRowPreview], as the list item is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ForumRowTopicTapTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    @Test
    fun `a tap on the newest topic's tab opens that topic, not the forum`() {
        setRow()

        row().performTouchInput { click(Offset(TAP_INSET.toPx(), TAP_INSET.toPx())) }

        assertEquals(listOf("topic $NEWEST"), events)
    }

    @Test
    fun `a tap on another topic's name opens that topic`() {
        setRow()

        // The newest topic's long name fills its share of the line, so the next name starts after it and the gap.
        row().performTouchInput {
            click(Offset(width * TAB_SHARE + (GAP + TAP_INSET).toPx(), TAP_INSET.toPx()))
        }

        assertEquals(listOf("topic $NEXT"), events)
    }

    @Test
    fun `a tap on the preview opens the forum`() {
        setRow()

        row().performTouchInput { click(Offset(width / 2f, height - TAP_INSET.toPx())) }

        assertEquals(listOf("row"), events)
    }

    @Test
    fun `a long press on a topic is the row's`() {
        setRow()

        row().performTouchInput { longClick(Offset(TAP_INSET.toPx(), TAP_INSET.toPx())) }

        assertEquals(listOf("long"), events)
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun setRow() =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Box(
                    Modifier
                        .width(ROW_WIDTH)
                        .testTag(ROW)
                        .combinedClickable(onLongClick = { events += "long" }) { events += "row" },
                ) {
                    ForumRowPreview(
                        topics =
                            persistentListOf(
                                RowTopic(NEWEST, LONG_NAME, TopicColor.Green, isUnread = false),
                                RowTopic(NEXT, "General", color = null, isUnread = false),
                                RowTopic(id = 4, "Meetups", TopicColor.Violet, isUnread = false),
                            ),
                        preview = PreviewText(AnnotatedString("Next up: Hidden Figures"), "Next up: Hidden Figures"),
                        contentIcon = null,
                        onTopicClick = { events += "topic $it" },
                    )
                }
            }
        }

    private fun row() = composeRule.onNodeWithTag(ROW)

    private companion object {
        const val ROW = "row"
        const val NEWEST = 3
        const val NEXT = 1
        const val LONG_NAME = "A reading list far too long to fit its share of the line"

        /** The newest topic's share of the line, as [ForumRowPreview] gives it. */
        const val TAB_SHARE = 0.6f
        val ROW_WIDTH = 320.dp

        /** `spacing.extraSmall`, between the tab and the other names. */
        val GAP = 4.dp
        val TAP_INSET = 6.dp
    }
}
