package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.RecompositionCounter
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the list of scheduled messages composes again while the user scrolls it and as Telegram sends one of them
 * (`docs/PERFORMANCE.md` §4: no recomposition of what stays on screen), counted with [RecompositionCounter] frame by
 * frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ScheduledRecompositionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    private lateinit var listState: LazyListState

    private var state by mutableStateOf(ScheduledMessagesUiState())

    /** False until the counter runs, so it sees the screen's first composition. */
    private var shown by mutableStateOf(false)

    @Test
    fun `scrolling the scheduled messages recomposes none of the bubbles`() {
        setScreen(withMessages(MESSAGES))

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ENTRIES_ON_SCREEN)
        assertEquals(emptyList<String>(), counter.recomposedOwn.filter { it.isItemFunction() })
    }

    @Test
    fun `a message Telegram sent leaves without recomposing the others`() {
        setScreen(withMessages(MESSAGES))

        state = withMessages(MESSAGES - 1)
        repeat(SETTLE_FRAMES) { frame() }

        assertEquals(emptyList<String>(), counter.recomposedOwn.filter { it.isItemFunction() })
    }

    private fun setScreen(initial: ScheduledMessagesUiState) {
        state = initial
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                if (shown) {
                    listState = rememberLazyListState()
                    ScheduledMessagesScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        counter.start()
        shown = true
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    /** The composables of the list's entries: bubbles and what they show, and the date pills. */
    private fun String.isItemFunction(): Boolean = ITEM_FILES.any { "ui.$it." in this || "ui.content.$it." in this }

    /** [count] scheduled messages of one day each, the latest first, the newest one (at the bottom) leaving first. */
    private fun withMessages(count: Int): ScheduledMessagesUiState {
        val items =
            (count downTo 1).flatMap { id ->
                val bubble =
                    ChatPreviewData
                        .bubble(id.toLong(), "Scheduled $id", time = "9:41 AM", isOutgoing = true)
                        .copy(schedule = BubbleSchedule.At())
                listOf(bubble, ChatItem.DateSeparator(id.toLong(), ChatPreviewData.today))
            }
        return ScheduledMessagesUiState(content = ScheduledContent.Content(items.toImmutableList()))
    }

    private companion object {
        const val MESSAGES = 100
        const val ENTRIES_ON_SCREEN = 10
        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f
        const val SETTLE_FRAMES = 60

        val ITEM_FILES = listOf("MessageBubbleKt", "BubbleFramesKt", "BubblePartsKt", "BubbleMetaKt", "ChatPillsKt")
    }
}
