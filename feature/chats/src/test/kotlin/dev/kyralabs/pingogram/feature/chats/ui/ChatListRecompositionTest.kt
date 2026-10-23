package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the chat list composes again while the user scrolls, drags it under its top bar, and leaves and comes back
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
    fun `dragging the list under its pinned top bar recomposes neither the bar's composables nor the list's`() {
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

        // The bar is pinned: the list scrolled under it and stayed where it was.
        assertEquals(barBottom, list.fetchSemanticsNode().boundsInRoot.top)
        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 0)
        // The bar takes the scroll through its scroll behavior, read in the layout and draw phases. Only the "New
        // chat" button composes again, as it collapses (`dragging the list back and forth…` counts it).
        assertEquals(
            emptyList<String>(),
            counter.recomposedOwnFunctions.filterNot { it.endsWith(NEW_CHAT_BUTTON) },
        )
    }

    @Test
    fun `dragging the list back and forth recomposes the New chat button once per direction, and nothing else`() {
        setScreen(connection = ConnectionStatus.Ready)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        // The counter sees a composable compose again only from its second time on: collapse and expand it once.
        dragBackAndForth(list)
        counter.reset()

        dragBackAndForth(list)

        // Collapsed on the way down, expanded on the way back: two recompositions in 60 frames of dragging.
        assertEquals(
            listOf(NEW_CHAT_BUTTON, NEW_CHAT_BUTTON),
            counter.recomposedOwnFunctions.map { it.substringAfterLast('.') },
        )
    }

    /** Drags [list] towards its end for [DRAG_FRAMES] frames, then halfway back, and lets it settle. */
    private fun dragBackAndForth(list: SemanticsNodeInteraction) {
        list.performTouchInput { down(center) }
        repeat(DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        repeat(DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, DRAG_STEP / 2)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }
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
    fun `a story ring turning seen recomposes the rings row alone, and scrolling past it nothing`() {
        setScreen(connection = ConnectionStatus.Ready, withStories = true)

        // The counter sees a composable compose again only from its second time on: change the rings once first.
        seeRing(1)
        counter.reset()
        seeRing(2)

        val recomposed = counter.recomposedOwnFunctions
        assertTrue(recomposed.toString(), recomposed.isNotEmpty())
        assertEquals(emptyList<String>(), recomposed.filterNot { STORY_RINGS in it })

        counter.reset()
        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }
        assertEquals(emptyList<String>(), counter.recomposedOwn)
    }

    @Test
    fun `the counter sees a recomposition`() {
        setScreen(connection = ConnectionStatus.Ready)

        connection = ConnectionStatus.Updating
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwn)
    }

    private var connection by mutableStateOf(ConnectionStatus.Ready)

    private var rings by mutableStateOf(StoryRingsPreviewData.rings)

    /** Marks ring [index] seen, as the viewer does, and lets the frames pass. */
    private fun seeRing(index: Int) {
        rings =
            rings.mapIndexed { i, ring -> if (i == index) ring.copy(hasUnread = false) else ring }.toPersistentList()
        repeat(SETTLE_FRAMES) { frame() }
    }

    private fun setScreen(
        connection: ConnectionStatus,
        withStories: Boolean = false,
    ) {
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
                // Read in the row's own composition, as the route does.
                val stories: @Composable (Modifier) -> Unit =
                    remember {
                        { modifier -> StoryRingsRow(rings = rings, onOpen = {}, onNearEnd = {}, modifier = modifier) }
                    }
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
                            stories = stories.takeIf { withStories },
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
        const val NEW_CHAT_BUTTON = "NewChatButton"
        const val STORY_RINGS = "StoryRingsKt"
    }
}
