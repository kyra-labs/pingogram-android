package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.profile.ui.RecompositionCounter
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the members page composes again while the user scrolls a long list of members, its top bar collapsing
 * (`docs/PERFORMANCE.md` §4: nothing on screen composes again per frame), counted with [RecompositionCounter] frame by
 * frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatMembersRecompositionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()
    private lateinit var listState: LazyListState

    @Test
    fun `scrolling the members recomposes none of their rows`() {
        setScreen()

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 1)
        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `dragging the list collapses the bar without recomposing the rows`() {
        setScreen()
        val list = composeRule.onNodeWithTag(CHAT_MEMBERS_LIST_TEST_TAG)

        list.performTouchInput { down(center) }
        repeat(DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        val rows = counter.recomposedOwnFunctions.filter { it.substringAfterLast('.') in ROW_PARTS }
        assertEquals(counter.recomposedOwn.distinct().toString(), emptyList<String>(), rows)
    }

    private fun setScreen() {
        val members =
            (0 until MEMBERS).map { index ->
                ChatInfoPreviewData.members[index % ChatInfoPreviewData.members.size].copy(
                    sender = MessageSender.User(FIRST_ID + index),
                    name = UiText.Plain("Member $index"),
                )
            }
        val state =
            ChatInfoPreviewData.membersPage.copy(
                list = MembersList.Content(members.toImmutableList(), totalCount = MEMBERS),
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                ChatMembersScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(SETTLE_FRAMES) { frame() }
        counter.start()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val MEMBERS = 200
        const val FIRST_ID = 1_000L
        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f
        const val DRAG_FRAMES = 40
        const val DRAG_STEP = 30f
        const val SETTLE_FRAMES = 60

        /** A member's row and what it holds. */
        val ROW_PARTS = setOf("MemberRow", "MemberMenu")
    }
}
