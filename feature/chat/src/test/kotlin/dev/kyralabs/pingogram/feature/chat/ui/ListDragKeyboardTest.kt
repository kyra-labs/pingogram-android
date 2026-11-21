package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ReportListDrags] and the keyboard: the user's drag of the message list hides the keyboard; the list's own
 * scrolls (a jump to a message, back to the newest) and new messages arriving do not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ListDragKeyboardTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var hides = 0

    private val keyboard =
        object : SoftwareKeyboardController {
            override fun show() = Unit

            override fun hide() {
                hides++
            }
        }

    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    /** How many messages the list holds; newest at the bottom, as in a chat. */
    private val count = mutableIntStateOf(MESSAGES)

    private fun setList() {
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(MESSAGE_LIST_TEST_TAG),
                    state = listState,
                    reverseLayout = true,
                ) {
                    items((count.intValue downTo 1).toList(), key = { it }) { id ->
                        BasicText("Message $id", Modifier.height(ROW_HEIGHT))
                    }
                }
                ReportListDrags(listState) {}
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `dragging the list hides the keyboard`() {
        setList()

        composeRule.onNodeWithTag(MESSAGE_LIST_TEST_TAG).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, hides)
    }

    @Test
    fun `the list's own scrolls leave the keyboard up`() {
        setList()

        composeRule.runOnIdle {
            scope.launch {
                listState.animateScrollToItem(MESSAGES / 2)
                listState.scrollToItem(0)
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, hides)
    }

    @Test
    fun `new messages leave the keyboard up`() {
        setList()

        composeRule.runOnIdle { count.intValue += 3 }
        composeRule.waitForIdle()

        assertEquals(0, hides)
    }

    private companion object {
        const val MESSAGES = 60
        val ROW_HEIGHT = 48.dp
    }
}
