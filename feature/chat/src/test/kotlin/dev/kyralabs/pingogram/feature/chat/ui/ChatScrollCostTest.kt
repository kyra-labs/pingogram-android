package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.management.ManagementFactory

/**
 * What the conversation's scroll frames cost on the JVM (`docs/PERFORMANCE.md` §9): the main thread's CPU time per
 * frame while 2,000 text messages (some with replies and reactions) scroll by [STEP] px a frame, after the JIT warmed
 * up, printed as percentiles, with the conversation's own list state ([rememberScrollingListState]) and with Compose's
 * default one. A measurement, not a check: it runs only with `-Ppingogram.scrollCost`, as `feature:chats`'
 * `ChatListScrollCostTest` describes. No media: Robolectric loads no pictures and draws nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatScrollCostTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty(SCROLL_COST_PROPERTY).toBoolean())

    @Test
    fun `scroll frames with the conversation's own state`() = measure(scrollingState = true)

    @Test
    fun `scroll frames with Compose's default state`() = measure(scrollingState = false)

    private fun measure(scrollingState: Boolean) {
        val state =
            ChatUiState(
                header = ChatPreviewData.privateChat.header,
                content =
                    ChatContent.Content(
                        (MESSAGES downTo 1).map(::bubble).toImmutableList(),
                        reachedStart = true,
                        isLoadingOlder = false,
                        null,
                    ),
                composer = ChatPreviewData.canWrite,
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = if (scrollingState) rememberScrollingListState() else rememberLazyListState()
                ChatScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(WARMUP_RUNS) { scroll() }
        val frames = (1..MEASURED_RUNS).flatMap { scroll() }.sorted()
        println(
            "Scroll cost (${if (scrollingState) "scrolling" else "default"} state): CPU per frame P50 " +
                "${frames.percentile(50)} µs, P90 ${frames.percentile(90)} µs, P99 ${frames.percentile(99)} µs",
        )
    }

    private fun bubble(id: Int): ChatItem.Bubble =
        ChatPreviewData.bubble(
            id = id.toLong(),
            text =
                if (id % LONG_EVERY ==
                    0
                ) {
                    "Message $id, a longer one that takes a second line or so"
                } else {
                    "Message $id"
                },
            time = "9:41 AM",
            isOutgoing = id / RUN % 2 == 0,
            sender = UiText.Plain("Ada"),
            reply =
                if (id % REPLY_EVERY == 0) {
                    ReplyQuote(UiText.Plain("Ada"), senderId = 2, UiText.Plain("Message"), id - 1L)
                } else {
                    null
                },
            reactions =
                if (id % REACTIONS_EVERY == 0) {
                    ChatPreviewData.reactions(ReactionChip(ReactionLabel.Emoji("👍"), count = 2, isChosen = false))
                } else {
                    persistentListOf()
                },
        )

    /** [FRAMES] frames scrolling by [STEP] px each; the main thread's CPU time of each, scroll and frame, in µs. */
    private fun scroll(): List<Long> {
        val threads = ManagementFactory.getThreadMXBean()
        return List(FRAMES) {
            val start = threads.currentThreadCpuTime
            // Scrolling measures the list at once, composing what comes into view, before the frame.
            composeRule.runOnIdle { listState.dispatchRawDelta(STEP) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            (threads.currentThreadCpuTime - start) / NANOS_PER_MICRO
        }
    }

    private fun List<Long>.percentile(percent: Int) = this[(size - 1) * percent / PERCENT]

    private companion object {
        const val MESSAGES = 2_000
        const val RUN = 3
        const val LONG_EVERY = 4
        const val REPLY_EVERY = 7
        const val REACTIONS_EVERY = 5
        const val FRAMES = 120
        const val STEP = 40f
        const val WARMUP_RUNS = 3
        const val MEASURED_RUNS = 6
        const val NANOS_PER_MICRO = 1_000
        const val PERCENT = 100
        const val SCROLL_COST_PROPERTY = "pingogram.scrollCost"
    }
}
