package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the chat list composes again while the user scrolls, collapses its top bar, and leaves and comes back
 * (`docs/PERFORMANCE.md` §4: no recomposition of what stays on screen), counted with [RecompositionCounter] frame by
 * frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListRecompositionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    private lateinit var listState: LazyListState

    /** False while "another screen" shows: the list leaves the composition, its saved state kept, as in navigation. */
    private var shown by mutableStateOf(true)

    @Test
    fun `scrolling the list recomposes none of its composables, and nothing in every frame`() {
        setScreen(connection = ConnectionStatus.Ready)

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ROWS_ON_SCREEN)
        assertEquals(emptyList<String>(), counter.recomposedOwn)
        // Now and then the lazy list's machinery recomposes a text of a row it brings in (a reused or prefetched one).
        assertTrue(counter.recomposed.toString(), counter.recomposed.size < SCROLL_FRAMES / RARE)
    }

    @Test
    fun `collapsing the top bar recomposes neither the bar's composables nor the list's`() {
        setScreen(connection = ConnectionStatus.Connecting)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        val barBottom = list.fetchSemanticsNode().boundsInRoot.top

        list.performTouchInput { down(center) }
        repeat(DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        // The bar collapsed: the list moved up with it.
        assertTrue(list.fetchSemanticsNode().boundsInRoot.top < barBottom)
        // Material's Scaffold runs the screen's content lambda again in every frame the bar's height changes, and
        // everything that lambda calls skips.
        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `coming back to the list puts it where it was and composes its rows only once`() {
        setScreen(connection = ConnectionStatus.Ready)
        composeRule.runOnIdle { listState.requestScrollToItem(index = RETURN_INDEX) }
        repeat(SETTLE_FRAMES) { frame() }

        shown = false
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()
        shown = true
        repeat(SETTLE_FRAMES) { frame() }

        assertEquals(RETURN_INDEX, listState.firstVisibleItemIndex)
        // The test's own content recomposes to bring the list back; nothing of the list composes twice.
        val testContent = ChatListRecompositionTest::class.java.name
        assertEquals(emptyList<String>(), counter.recomposed.filterNot { it.startsWith(testContent) })
    }

    @Test
    fun `the counter sees a recomposition`() {
        setScreen(connection = ConnectionStatus.Ready)

        connection = ConnectionStatus.Updating
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwn)
    }

    private var connection by mutableStateOf(ConnectionStatus.Ready)

    private fun setScreen(connection: ConnectionStatus) {
        this.connection = connection
        val items =
            (0 until ROWS)
                .map { index ->
                    val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                    base.copy(id = FIRST_ID + index)
                }.toImmutableList()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val holder = rememberSaveableStateHolder()
                if (shown) {
                    holder.SaveableStateProvider(SCREEN_KEY) {
                        listState = rememberLazyListState()
                        ChatListScreen(
                            state =
                                ChatListUiState(
                                    content = ChatListContent.Content(items),
                                    connection = this.connection,
                                    folderTabs = ChatListPreviewData.folders,
                                ),
                            onEvent = {},
                            listState = listState,
                        )
                    }
                }
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
        const val ROWS = 200
        const val FIRST_ID = 1_000L
        const val ROWS_ON_SCREEN = 10
        const val RETURN_INDEX = 40

        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f

        /** At most one recomposition in this many frames counts as rare, nothing like one in every frame. */
        const val RARE = 10

        const val DRAG_FRAMES = 30
        const val DRAG_STEP = 10f

        const val SETTLE_FRAMES = 60
        const val SCREEN_KEY = "chats"
        const val CHAT_LIST_TEST_TAG = "chat_list"
    }
}
