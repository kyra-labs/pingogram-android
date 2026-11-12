package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching folders (FR-L3, `docs/DESIGN_SYSTEM.md` §5, §6 "Folder tabs"): the selected folder's list slides in at
 * once from its tab's side while the old one leaves beside it, where it was left or at its top; with animations off
 * (reduced motion) the lists swap at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListFolderSwitchTest {
    /** The system's animator duration scale, as Compose applies it to every animation; 0 turns them off. */
    private val durationScale =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }

    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private var state by mutableStateOf(ChatListPreviewData.contentWithFolders)

    @Test
    fun `the selected folder's list slides in from its tab's side at once, and the old one leaves`() {
        setScreen()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = workFolder
        advanceFrames(SLIDE_FRAMES)

        // Work's tab is after All chats: its list comes in from the right while All chats' leaves to the left, which
        // takes no input and is out of the semantics tree on its way.
        val sliding = left(ROCKET_CLUB)
        assertFalse(exists(SAVED_MESSAGES))
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val resting = left(ROCKET_CLUB)
        assertTrue("Rocket club came in from the right: $sliding, then $resting", sliding > resting)
        row(ROCKET_CLUB).assertIsDisplayed()
    }

    @Test
    fun `a folder selected while a slide runs comes in from beyond the list still on its way`() {
        state = allChats
        setScreen()
        composeRule.waitForIdle()
        val width =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.width
        val resting = left("Chat 1")
        composeRule.mainClock.autoAdvance = false

        state = folder(1, numberedRows(40..50))
        advanceFrames(SLIDE_FRAMES)
        // Work's list is still a part of the width away from its place when Family is selected.
        state = folder(2, numberedRows(60..70))
        val arriving =
            List(SWITCH_FRAMES) {
                advanceFrames(1)
                if (exists("Chat 60")) unclippedLeft("Chat 60") else null
            }.filterNotNull().first()
        composeRule.mainClock.autoAdvance = true

        // Family's list starts a width beyond Work's, wherever that was, not a width from the place lists rest in.
        assertTrue("$arriving against ${resting + width}", arriving > resting + width * BEYOND_A_WIDTH)
    }

    @Test
    fun `with animations off the selected folder's list shows at once`() {
        durationScale.scaleFactor = 0f
        setScreen()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = workFolder
        advanceFrames(FEW_FRAMES)

        assertFalse(exists(SAVED_MESSAGES))
        row(ROCKET_CLUB).assertIsDisplayed()
    }

    @Test
    fun `the selected folder's rows show in their places, not sliding from where the other list had them`() {
        // Chat 7 is seventh in All chats and first in the folder, above chat 2, which is second in both.
        val listState = LazyListState()
        state = allChats
        setScreen(listState)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = folder(1, numberedRows(listOf(7, 2)))
        val shown =
            List(SWITCH_FRAMES) {
                advanceFrames(1)
                // Once the folder's own list has its rows.
                if ((listStates.ofFolder(1)?.layoutInfo?.totalItemsCount ?: 0) > 0) {
                    top("Chat 7") to top("Chat 2")
                } else {
                    null
                }
            }.filterNotNull()
        composeRule.mainClock.autoAdvance = true

        // Rows not laid out yet, in the frame that composes them, read as both at 0.
        val placed = shown.filterNot { (seven, two) -> seven == 0f && two == 0f }
        assertTrue("The folder's list showed", placed.isNotEmpty())
        assertTrue("Chat 7's and chat 2's tops: $shown", placed.all { (seven, two) -> seven < two })
    }

    @Test
    fun `the selected folder's list starts at its top`() {
        val listState = LazyListState()
        state = ChatListPreviewData.contentWithFolders.copy(content = ChatListContent.Content(manyRows))
        setScreen(listState)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(manyRows.lastIndex)

        state = workFolder.copy(selectedFolderId = 2, content = ChatListContent.Content(manyRows))
        composeRule.waitForIdle()

        assertEquals(0, shown().firstVisibleItemIndex)
        composeRule.onAllNodes(rowMatcher(SAVED_MESSAGES))[0].assertIsDisplayed()
    }

    @Test
    fun `back in a folder seen before, its list is where it was left, not at the other folder's first chat`() {
        // All chats scrolled to chat 6; the Work folder starts with chat 8, which All chats has further down.
        val listState = LazyListState()
        state = allChats
        setScreen(listState)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)

        state = folder(1, numberedRows(8..20))
        composeRule.waitForIdle()
        assertEquals("Work starts at its top", 0, shown().firstVisibleItemIndex)
        state = allChats
        composeRule.waitForIdle()

        assertEquals(5, shown().firstVisibleItemIndex)
        assertEquals(6L, firstVisibleKey(shown()))
    }

    @Test
    fun `back in a folder with pinned chats, the "Pinned" header counts among the rows above the chats`() {
        // Chats 1 and 2 pinned under "Pinned" (index 0), so chat 6 is the list's index 6.
        val pinned = allChats.copy(content = ChatListContent.Content(numberedRows(1..30), pinnedCount = 2))
        val listState = LazyListState()
        state = pinned
        setScreen(listState)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        // The clock is stepped by hand here: the scroll lands in the next frame.
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(6L, firstVisibleKey(shown()))

        state = folder(1, numberedRows(8..20))
        composeRule.waitForIdle()
        state = pinned
        composeRule.waitForIdle()

        assertEquals(6, shown().firstVisibleItemIndex)
        assertEquals(6L, firstVisibleKey(shown()))
    }

    @Test
    fun `each folder keeps its own position`() {
        val listState = LazyListState()
        state = allChats
        setScreen(listState)
        state = folder(1, numberedRows(1..30))
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(12)

        state = allChats
        composeRule.waitForIdle()
        assertEquals("All chats was left at its top", 0, shown().firstVisibleItemIndex)
        state = folder(1, numberedRows(1..30))
        composeRule.waitForIdle()

        assertEquals(12, shown().firstVisibleItemIndex)
    }

    @Test
    fun `a chat that moved since is found again by its key`() {
        val listState = LazyListState()
        state = allChats
        setScreen(listState)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        state = folder(1, numberedRows(8..20))
        composeRule.waitForIdle()

        // Meanwhile chat 6 got a message and moved to the top of All chats.
        state =
            ChatListPreviewData.contentWithFolders.copy(
                content = ChatListContent.Content(numberedRows(listOf(6) + (1..30).minus(6))),
            )
        composeRule.waitForIdle()

        assertEquals(0, shown().firstVisibleItemIndex)
        assertEquals(6L, firstVisibleKey(shown()))
    }

    @Test
    fun `a list at its top stays at its top when chats load above its first one`() {
        state = folder(1, numberedRows(8..20))
        setScreen()
        composeRule.waitForIdle()

        // All chats first shows the chats TDLib had loaded through the folder, then all of them, chat 1 first.
        state = allChats.copy(content = ChatListContent.Content(numberedRows(8..20)))
        composeRule.waitForIdle()
        state = allChats
        composeRule.waitForIdle()

        assertEquals(0, shown().firstVisibleItemIndex)
        assertEquals(1L, firstVisibleKey(shown()))
    }

    @Test
    fun `where each folder was left survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var listState: LazyListState
        state = allChats
        restorationTester.setContent {
            listState = rememberLazyListState()
            PingogramTheme(dynamicColor = false) { ChatListScreen(state = state, onEvent = {}, listState = listState) }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        state = folder(1, numberedRows(8..20))
        composeRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        state = allChats
        composeRule.waitForIdle()

        assertEquals(5, listState.firstVisibleItemIndex)
    }

    /** The key of the first row past the top bar; rows behind it are visible too, in the list's content padding. */
    private fun firstVisibleKey(listState: LazyListState) =
        listState.layoutInfo.visibleItemsInfo
            .first { it.index == listState.firstVisibleItemIndex }
            .key

    /** Runs [count] frames, and what they dispatch to the main thread, as a device does between frames. */
    private fun advanceFrames(count: Int) =
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

    /** Each folder's list state, the first one [setScreen]'s; the shown folder's is [shown]. */
    private lateinit var listStates: FolderListStates

    private fun setScreen(listState: LazyListState? = null) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val first = listState ?: rememberLazyListState()
                listStates = remember { FolderListStates(first, state.selectedFolderId) }
                ChatListScreen(state = state, onEvent = {}, listState = first, listStates = listStates)
            }
        }

    /** The list state of the folder selected now. */
    private fun shown(): LazyListState = listStates.ofFolder(state.selectedFolderId)!!

    private fun row(name: String): SemanticsNodeInteraction = composeRule.onNode(rowMatcher(name))

    private fun top(name: String) = row(name).fetchSemanticsNode().boundsInRoot.top

    private fun left(name: String) = row(name).fetchSemanticsNode().boundsInRoot.left

    /** Where a row's left edge is, also beyond the window, whose bounds clip the other measure. */
    private fun unclippedLeft(name: String) = row(name).fetchSemanticsNode().positionInRoot.x

    private fun exists(name: String) = composeRule.onAllNodes(rowMatcher(name)).fetchSemanticsNodes().isNotEmpty()

    private fun rowMatcher(name: String) = hasContentDescription("$name,", substring = true) and hasClickAction()

    private companion object {
        const val SAVED_MESSAGES = "Saved Messages"
        const val ROCKET_CLUB = "Rocket club"

        /**
         * Three frames: enough to swap the lists (the frame that sees the folder change, the one that runs the swap,
         * the one that shows the new list), not for the slide on the default spatial spring.
         */
        const val FEW_FRAMES = 3

        /** Frames into the slide: the selected folder's rows are laid out, still on their way in. */
        const val SLIDE_FRAMES = 6

        /** How much more than a width away a list selected mid-slide starts: the rest of the slide it interrupts. */
        const val BEYOND_A_WIDTH = 1.1f

        /** Enough frames for most of the slide. */
        const val SWITCH_FRAMES = 30

        /** The Work folder, with only a chat that is not among All chats' rows. */
        val workFolder =
            ChatListPreviewData.contentWithFolders.copy(
                selectedFolderId = 1,
                content =
                    ChatListContent.Content(
                        persistentListOf(
                            ChatListPreviewData.items.last().copy(id = 99, title = UiText.Plain(ROCKET_CLUB)),
                        ),
                    ),
            )

        /** All chats: chats 1 to 30, in that order. */
        val allChats =
            ChatListPreviewData.contentWithFolders.copy(content = ChatListContent.Content(numberedRows(1..30)))

        /** Chats named after their [ids], in that order. */
        fun numberedRows(ids: Iterable<Int>) =
            ids
                .map { ChatListPreviewData.items.last().copy(id = it.toLong(), title = UiText.Plain("Chat $it")) }
                .toImmutableList()

        fun folder(
            id: Int,
            rows: ImmutableList<ChatListItemModel>,
        ) = ChatListPreviewData.contentWithFolders.copy(selectedFolderId = id, content = ChatListContent.Content(rows))

        /** The preview's rows three times over, with other ids, so the list scrolls. */
        val manyRows =
            (0..2)
                .flatMap { copy -> ChatListPreviewData.items.map { it.copy(id = it.id + copy * 100) } }
                .toImmutableList()
    }
}
