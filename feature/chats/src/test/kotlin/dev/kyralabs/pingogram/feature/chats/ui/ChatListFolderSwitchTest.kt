package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * Switching folders (FR-L3, `docs/DESIGN_SYSTEM.md` §5): Material's fade-through between the two lists, the old one
 * fading out before the new one shows, at its top; with animations off (reduced motion) the lists swap at once.
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
    fun `the shown list fades out before the selected folder's list replaces it`() {
        setScreen()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = workFolder
        advanceFrames(FEW_FRAMES)

        assertTrue("All chats' list is still fading out", exists(SAVED_MESSAGES))
        assertFalse(exists(ROCKET_CLUB))
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(exists(SAVED_MESSAGES))
        row(ROCKET_CLUB).assertIsDisplayed()
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
    fun `the selected folder's list starts at its top`() {
        state = ChatListPreviewData.contentWithFolders.copy(content = ChatListContent.Content(manyRows))
        setScreen()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(manyRows.lastIndex)

        state = workFolder.copy(selectedFolderId = 2, content = ChatListContent.Content(manyRows))
        composeRule.waitForIdle()

        row(SAVED_MESSAGES).assertIsDisplayed()
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
        assertEquals("Work starts at its top", 0, listState.firstVisibleItemIndex)
        state = allChats
        composeRule.waitForIdle()

        assertEquals(5, listState.firstVisibleItemIndex)
        assertEquals(6L, firstVisibleKey(listState))
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
        assertEquals("All chats was left at its top", 0, listState.firstVisibleItemIndex)
        state = folder(1, numberedRows(1..30))
        composeRule.waitForIdle()

        assertEquals(12, listState.firstVisibleItemIndex)
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

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(6L, firstVisibleKey(listState))
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

    private fun setScreen(listState: LazyListState? = null) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(state = state, onEvent = {}, listState = listState ?: rememberLazyListState())
            }
        }

    private fun row(name: String): SemanticsNodeInteraction = composeRule.onNode(rowMatcher(name))

    private fun exists(name: String) = composeRule.onAllNodes(rowMatcher(name)).fetchSemanticsNodes().isNotEmpty()

    private fun rowMatcher(name: String) = hasContentDescription("$name,", substring = true) and hasClickAction()

    private companion object {
        const val SAVED_MESSAGES = "Saved Messages"
        const val ROCKET_CLUB = "Rocket club"

        /**
         * Three frames: enough to swap the lists without animations (the frame that sees the folder change, the one
         * that runs the swap, the one that shows the new list), not for the fade-out on the fast effects spring.
         */
        const val FEW_FRAMES = 3

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
