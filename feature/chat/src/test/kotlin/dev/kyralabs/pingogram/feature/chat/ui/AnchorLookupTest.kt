package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FollowListAnchor] looks the anchor's entry up among the items only while the anchor waits for the list: once the
 * list moved to it, the conversation's updates (a new message, an edit) look nothing up. The items count how often
 * they are walked through; the list itself reads them by index.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AnchorLookupTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val bubbles = (200L downTo 1L).map { ChatPreviewData.bubble(it, "Message $it", time = "9:41 AM") }
    private val anchor = ListAnchor(id = 1, AnchorTarget.Message(FAR_BACK))
    private var items by mutableStateOf(CountingList(bubbles))

    private fun setList(state: MessageJumpState) {
        composeRule.setContent {
            val listState = rememberLazyListState()
            LazyColumn(state = listState) {
                items(items, key = { it.key }) { Box(Modifier.fillMaxWidth().height(ROW.dp)) }
            }
            FollowListAnchor(ChatContent.Content(items, true, false, null, anchor = anchor), listState, state, 0)
        }
        composeRule.waitForIdle()
    }

    /** A new message arrives: new items, the same anchor. */
    private fun update() {
        composeRule.runOnIdle { items = CountingList(listOf(ChatPreviewData.bubble(201L, "New", "9:42 AM")) + bubbles) }
        composeRule.waitForIdle()
    }

    @Test
    fun `an anchor the list moved to is looked up no more as the items change`() {
        setList(MessageJumpState(movedToAnchor = anchor.id))

        update()

        assertEquals(0, items.iterations)
    }

    @Test
    fun `an anchor still waiting for the list is looked up`() {
        setList(MessageJumpState())

        update()

        assertTrue("${items.iterations}", items.iterations > 0)
    }

    /** The entries, counting how often they are walked through ([iterations]). */
    private class CountingList(
        private val entries: List<ChatItem>,
    ) : ImmutableList<ChatItem>,
        List<ChatItem> by entries {
        var iterations = 0
            private set

        override fun iterator(): Iterator<ChatItem> {
            iterations++
            return entries.iterator()
        }

        override fun subList(
            fromIndex: Int,
            toIndex: Int,
        ): ImmutableList<ChatItem> = entries.subList(fromIndex, toIndex).toImmutableList()
    }

    private companion object {
        const val ROW = 60
        const val FAR_BACK = 20L
    }
}
