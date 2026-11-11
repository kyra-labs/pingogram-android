package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.ui.comments.CommentUi
import dev.kyralabs.pingogram.feature.calls.ui.comments.FLOATING_REACTIONS
import dev.kyralabs.pingogram.feature.calls.ui.comments.FloatingReaction
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsHost
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsUiState
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.management.ManagementFactory

/**
 * The voice chat screen's recompositions and frame cost on the JVM (`docs/PERFORMANCE.md` §4, §9): a connected voice
 * chat whose timer ticks and whose speakers change every frame, and the same with comments coming in.
 *
 * The recomposition checks always run: a comment recomposes the comments pane alone, never the header, the tiles or the
 * controls. The CPU time per frame is a measurement, printed as percentiles only with `-Ppingogram.scrollCost`, as
 * the scroll cost tests: `./gradlew :feature:calls:testDebugUnitTest -Ppingogram.scrollCost --tests
 * '*GroupCallFrameCostTest*' --rerun`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class GroupCallFrameCostTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    /** Power saving's "Stop call screen animations". */
    private val still = mutableStateOf(false)

    @Test
    fun `a new comment recomposes the comments pane alone`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(GroupCallPreviewStates.comments)
        show(call, comments)

        repeat(COMMENTS_CHECKED) { index -> comments.value = withComment(comments.value, index) }
        frame()

        val recomposed = counter.recomposedOwnFunctions.toSet()
        assertTrue("the pane recomposed", recomposed.isNotEmpty())
        val outside = recomposed.filterNot { "comments" in it }
        assertEquals("only the comments' composables recompose", emptyList<String>(), outside)
    }

    @Test
    fun `a new comment shows at the bottom, where the user reads the newest`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(GroupCallPreviewStates.comments)
        show(call, comments)

        repeat(COMMENTS_CHECKED) { index -> comments.value = withComment(comments.value, index) }
        frame()
        frame()

        composeRule
            .onNodeWithContentDescription(
                "Comment ${COMMENTS_CHECKED - 1}",
                substring = true,
            ).assertIsDisplayed()
    }

    @Test
    fun `a comment while the pane is hidden recomposes nothing`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(GroupCallPreviewStates.comments.copy(isShown = false))
        show(call, comments)

        comments.value = withComment(comments.value, 0)
        frame()

        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `a new reaction starts its own animation alone`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(withReactions(1..REACTIONS_KEPT))
        show(call, comments)
        repeat(RISE_FRAMES) { frame() }
        counter.reset()

        // The view model keeps the newest six: the seventh pushes the first out and every other one moves up a place.
        comments.value = withReactions(2..REACTIONS_KEPT + 1)
        repeat(RISE_FRAMES) { frame() }

        val restarted = counter.recomposedOwnFunctions.filter { "FloatingEmoji" in it }
        assertEquals("no reaction that had risen starts again", emptyList<String>(), restarted)
    }

    @Test
    fun `reactions float up, but not while power saving holds the call screens still`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(withReactions(1..1))
        show(call, comments)
        composeRule.onNodeWithTag(FLOATING_REACTIONS, useUnmergedTree = true).assertExists()

        still.value = true
        frame()

        composeRule.onNodeWithTag(FLOATING_REACTIONS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `turning comments on crossfades the layouts without recomposing each frame`() {
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val comments = mutableStateOf<GroupCallCommentsUiState>(GroupCallPreviewStates.comments.copy(isShown = false))
        show(call, comments)

        comments.value = GroupCallPreviewStates.comments
        frame()
        frame()
        counter.reset()
        repeat(CROSSFADE_FRAMES) { frame() }

        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
        composeRule.onNodeWithContentDescription("Comment", substring = true).assertExists()
    }

    @Test
    fun `frame cost of a connected voice chat`() = measure(comments = null)

    @Test
    fun `frame cost of a voice chat with comments coming in`() = measure(comments = GroupCallPreviewStates.comments)

    /**
     * Each frame the timer ticks, the speaker moves to the next participant and, with [comments], every
     * [FRAMES_PER_COMMENT]th frame a comment comes (10 a second at 60 Hz, a busy voice chat);
     * prints the main thread's CPU time per frame.
     */
    private fun measure(comments: GroupCallCommentsUiState.Available?) {
        assumeTrue(System.getProperty(COST_PROPERTY).toBoolean())
        val call = mutableStateOf(GroupCallPreviewStates.connected)
        val shown = mutableStateOf<GroupCallCommentsUiState>(comments ?: GroupCallCommentsUiState.Unavailable)
        show(call, shown)
        repeat(WARMUP_FRAMES) { step(it, call, shown, comments != null) }
        counter.reset()
        val times = (1..MEASURED_FRAMES).map { step(WARMUP_FRAMES + it, call, shown, comments != null) }.sorted()
        val perFrame = counter.recomposedOwnFunctions.size.toDouble() / MEASURED_FRAMES
        println(
            "Voice chat frame cost (${if (comments != null) "comments" else "no comments"}): CPU per frame P50 " +
                "${times.percentile(50)} µs, P90 ${times.percentile(90)} µs, P99 ${times.percentile(99)} µs; " +
                "Pingogram composables recomposed per frame ${"%.1f".format(perFrame)}",
        )
    }

    private fun step(
        index: Int,
        call: MutableState<GroupCallUiState.InCall>,
        comments: MutableState<GroupCallCommentsUiState>,
        withComments: Boolean,
    ): Long {
        val bean = ManagementFactory.getThreadMXBean()
        val start = bean.currentThreadCpuTime
        call.value = tick(call.value, index)
        if (withComments && index % FRAMES_PER_COMMENT == 0) comments.value = withComment(comments.value, index)
        frame()
        return (bean.currentThreadCpuTime - start) / NANOS_PER_MICRO
    }

    private fun show(
        call: MutableState<GroupCallUiState.InCall>,
        comments: MutableState<GroupCallCommentsUiState>,
    ) {
        val host = GroupCallCommentsHost(comments, mutableStateOf(null), onEvent = {})
        val shown = mutableStateOf(false)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalStillCallAnimations provides still.value) {
                    if (shown.value) GroupCallScreen(call.value, {}, {}, {}, {}, comments = host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        // Before the screen first composes, so the counter tells its first composition from the ones after.
        counter.start()
        shown.value = true
        frame()
        frame()
        counter.reset()
    }

    private fun frame() {
        // The writes reach the recomposer first, so the frame composes them.
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    /** The timer one second on, and the next participant speaking. */
    private fun tick(
        state: GroupCallUiState.InCall,
        index: Int,
    ): GroupCallUiState.InCall {
        val speaker = index % state.participants.size
        return state.copy(
            duration = "${index / SECONDS_PER_MINUTE}:${"%02d".format(index % SECONDS_PER_MINUTE)}",
            participants =
                state.participants
                    .mapIndexed { i, row ->
                        row.copy(state = if (i == speaker) ParticipantState.Speaking else ParticipantState.Listening)
                    }.toPersistentList(),
        )
    }

    private fun withComment(
        state: GroupCallCommentsUiState,
        index: Int,
    ): GroupCallCommentsUiState {
        val available = state as GroupCallCommentsUiState.Available
        val comment =
            CommentUi(
                id = FIRST_NEW_ID + index,
                senderKey = "user:${index % SENDERS}",
                senderId = (index % SENDERS).toLong(),
                name = "Person ${index % SENDERS}",
                photo = null,
                text = "Comment $index",
            )
        return available.copy(
            received = available.received + 1,
            comments = (available.comments + comment).takeLast(MAX_SHOWN).toPersistentList(),
        )
    }

    /** The comments shown, with the reactions of [ids] floating. */
    private fun withReactions(ids: IntRange): GroupCallCommentsUiState =
        GroupCallPreviewStates.comments.copy(
            reactions = ids.map { FloatingReaction(id = it, emoji = "❤") }.toPersistentList(),
        )

    private fun List<Long>.percentile(percent: Int) = this[(size - 1) * percent / PERCENT]

    private companion object {
        const val COST_PROPERTY = "pingogram.scrollCost"
        const val COMMENTS_CHECKED = 3
        const val FRAMES_PER_COMMENT = 6
        const val WARMUP_FRAMES = 60
        const val MEASURED_FRAMES = 240
        const val FIRST_NEW_ID = 1_000
        const val SENDERS = 7
        const val MAX_SHOWN = 200
        const val SECONDS_PER_MINUTE = 60
        const val NANOS_PER_MICRO = 1_000L
        const val PERCENT = 100

        /** As many reactions as the view model keeps (`GroupCallCommentsViewModel`). */
        const val REACTIONS_KEPT = 6

        /** Enough frames for the layouts' crossfade and their resize on the slow spatial spring. */
        const val CROSSFADE_FRAMES = 30

        /** Enough frames for a reaction to rise and fade on the slow springs. */
        const val RISE_FRAMES = 60
    }
}
