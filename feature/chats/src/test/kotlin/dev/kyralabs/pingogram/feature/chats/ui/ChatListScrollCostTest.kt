package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
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
 * What the chat list's scroll frames cost on the JVM (`docs/PERFORMANCE.md` §9): the main thread's CPU time per frame
 * while the list scrolls by [STEP] px a frame through 600 rows below the story rings, after the JIT warmed up, printed
 * as percentiles, with Pingogram's list state ([rememberScrollingListState]) and with Compose's default one. A
 * measurement, not a check: it runs only with `-Ppingogram.scrollCost` (`./gradlew :feature:chats:testDebugUnitTest
 * -Ppingogram.scrollCost --tests '*ScrollCostTest*' --rerun`), and its numbers are relative: compare two runs on the
 * same machine, each test in its own run, as the JIT carries over between tests of one run. Robolectric neither draws
 * nor runs Compose's prefetch between frames as a phone does, so only composition and measuring are seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListScrollCostTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty(SCROLL_COST_PROPERTY).toBoolean())

    @Test
    fun `scroll frames with the list's own state`() = measure(scrollingState = true)

    @Test
    fun `scroll frames with Compose's default state`() = measure(scrollingState = false)

    private fun measure(scrollingState: Boolean) {
        val items =
            (0 until ROWS)
                .map { index ->
                    val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                    base.copy(id = FIRST_ID + index)
                }.toImmutableList()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = if (scrollingState) rememberScrollingListState() else rememberLazyListState()
                ChatListScreen(
                    state =
                        ChatListUiState(
                            content = ChatListContent.Content(items),
                            connection = ConnectionStatus.Ready,
                            folderTabs = ChatListPreviewData.folders,
                        ),
                    onEvent = {},
                    listState = listState,
                    // The Chats tab's first row, as it has stories.
                    stories = { modifier ->
                        StoryRingsRow(
                            rings = StoryRingsPreviewData.rings,
                            onOpen = {},
                            onNearEnd = {},
                            modifier = modifier,
                        )
                    },
                )
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
        const val ROWS = 600
        const val FIRST_ID = 1_000L
        const val FRAMES = 120
        const val STEP = 40f
        const val WARMUP_RUNS = 3
        const val MEASURED_RUNS = 4
        const val NANOS_PER_MICRO = 1_000
        const val PERCENT = 100
        const val SCROLL_COST_PROPERTY = "pingogram.scrollCost"
    }
}
