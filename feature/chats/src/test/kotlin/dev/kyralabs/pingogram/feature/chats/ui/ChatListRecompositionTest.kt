package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.swipeLeft
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
    fun `scrolling the list under the folder bar recomposes none of its composables, and nothing in every frame`() {
        setScreen(connection = ConnectionStatus.Ready)
        val barTop = folderBarTop()

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ROWS_ON_SCREEN)
        // The rows went under the bar, which stayed where it was.
        assertEquals(barTop, folderBarTop(), 0.5f)
        assertEquals(emptyList<String>(), counter.recomposedOwn)
        // Now and then the lazy list's machinery recomposes a text of a row it brings in (a reused or prefetched one).
        assertTrue(counter.recomposed.toString(), counter.recomposed.size < SCROLL_FRAMES / RARE)
    }

    @Test
    fun `scrolling under the folder bar recomposes neither the bar nor the rows already on screen`() {
        setScreen(connection = ConnectionStatus.Ready)
        val onScreen = listState.layoutInfo.visibleItemsInfo.map { it.key }

        // Less than a screen, so most rows on screen at the start stay there, under or below the bar.
        repeat(SHORT_SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        val stillOnScreen =
            listState.layoutInfo.visibleItemsInfo
                .map { it.key }
                .intersect(onScreen.toSet())
        assertTrue("$stillOnScreen of $onScreen", stillOnScreen.size > onScreen.size / 2)
        // Composable lambdas included: neither the bar's own nor the rows' run again.
        val recomposed = counter.recomposed
        assertEquals(emptyList<String>(), recomposed.filter { name -> BAR_FILES.any { it in name } })
        assertEquals(emptyList<String>(), recomposed.filter { name -> ROW_FILES.any { it in name } })
    }

    @Test
    fun `flinging the list composes nothing again in its frames, as the benchmark's flings do`() {
        setScreen(connection = ConnectionStatus.Ready)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        val framesWithRecompositions =
            (1..FLINGS).sumOf {
                list.performTouchInput { down(center) }
                val dragging =
                    (1..FLING_DRAG_FRAMES).count {
                        counter.reset()
                        list.performTouchInput { moveBy(Offset(0f, -FLING_STEP)) }
                        frame()
                        counter.recomposed.isNotEmpty()
                    }
                list.performTouchInput { up() }
                val flying =
                    (1..SETTLE_FRAMES).count {
                        counter.reset()
                        frame()
                        counter.recomposed.isNotEmpty()
                    }
                dragging + flying
            }

        // The flings scrolled the list past the rows that first showed.
        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ROWS_ON_SCREEN / 2)
        // The header's collapse and the scaffold's content once in a while (the header's height changing it), never
        // in every frame: a fling's frames on the device are its animation's (Recomposer:animation), not
        // recompositions.
        val frames = FLINGS * (FLING_DRAG_FRAMES + SETTLE_FRAMES)
        assertTrue("$framesWithRecompositions of $frames", framesWithRecompositions < frames / RARE)
    }

    @Test
    fun `dragging the list collapses the header without recomposing it`() {
        setScreen(connection = ConnectionStatus.Ready)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        val barBottom = list.fetchSemanticsNode().boundsInRoot.top

        list.performTouchInput { down(center) }
        repeat(COLLAPSE_DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        assertTrue(list.fetchSemanticsNode().boundsInRoot.top < barBottom)
        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `dragging the list collapses the header and recomposes neither the header's composables nor the list's`() {
        setScreen(connection = ConnectionStatus.Connecting)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        val barBottom = list.fetchSemanticsNode().boundsInRoot.top

        list.performTouchInput { down(center) }
        repeat(COLLAPSE_DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        // The header collapsed, so the list starts higher, and the list scrolled under it.
        assertTrue(list.fetchSemanticsNode().boundsInRoot.top < barBottom)
        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 0)
        // The header takes the scroll through its scroll behavior, read in the layout and draw phases.
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
    fun `a count in the status line changing recomposes the header's status alone`() {
        setScreen(connection = ConnectionStatus.Ready)

        // The counter sees a composable compose again only from its second time on: change the counts once first.
        summary = summary.copy(unreadChats = summary.unreadChats + 1)
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()
        summary = summary.copy(unreadChats = summary.unreadChats + 1, mentionChats = summary.mentionChats + 1)
        repeat(SETTLE_FRAMES) { frame() }

        val recomposed = counter.recomposedOwnFunctions
        assertTrue(recomposed.toString(), recomposed.isNotEmpty())
        assertEquals(emptyList<String>(), recomposed.filterNot { HEADER in it || HEADER_STATUS in it })
    }

    @Test
    fun `a row changing recomposes neither the header nor the folder bar`() {
        setScreen(connection = ConnectionStatus.Ready)

        // The counter sees a composable compose again only from its second time on: change the row once first.
        rowChange++
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()
        rowChange++
        repeat(SETTLE_FRAMES) { frame() }

        val recomposed = counter.recomposed
        assertTrue(recomposed.toString(), recomposed.any { name -> ROW_FILES.any { it in name } })
        // The screen calls each bar again as it composes again; given the same arguments, it skips, and nothing in it
        // composes again. Given the whole state, as before, everything in them did.
        val header = recomposed.filter { HEADER in it || HEADER_STATUS in it }
        assertEquals(emptyList<String>(), header.filterNot { it.endsWith(HEADER_ENTRY) })
        val bar = recomposed.filter { name -> BAR_FILES.any { it in name } }
        assertEquals(emptyList<String>(), bar.filterNot { it.endsWith(BAR_ENTRY) })
    }

    @Test
    fun `the counter sees a recomposition`() {
        setScreen(connection = ConnectionStatus.Ready)

        connection = ConnectionStatus.Updating
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwn)
    }

    @Test
    fun `swiping the list to the next folder recomposes no row of the shown list, only the neighbour coming in`() {
        setScreen(connection = ConnectionStatus.Ready, folderSwipe = true)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)

        composeRule.mainClock.autoAdvance = true
        list.performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * SIDE_DRAG, durationMillis = SIDE_DRAG_MILLIS)
        }
        composeRule.waitForIdle()

        // Only the neighbour's own slot runs again as it comes in and goes; the list's rows never.
        assertEquals(emptyList<String>(), counter.recomposedOwn.filterNot { FOLDER_SLIDE in it })
        assertTrue(counter.recomposedOwn.toString(), counter.recomposedOwn.any { FOLDER_SLIDE in it })
    }

    @Test
    fun `a swipe that lands on the next folder recomposes no row of either list, under a bar that stays still`() {
        setScreen(connection = ConnectionStatus.Ready, folderSwipe = true)
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        val bar = folderBarBounds()

        composeRule.mainClock.autoAdvance = true
        list.performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * LANDING_DRAG, durationMillis = SIDE_DRAG_MILLIS)
        }
        composeRule.waitForIdle()

        // Family is selected; its list, which came in as the neighbour, and Work's, now beside it, changed places.
        assertEquals(FAMILY, selectedFolder)
        val rows = counter.recomposedOwn.filter { ROW in it }
        assertEquals(emptyList<String>(), rows)
        // The lists slid under the bar; the bar itself did not move.
        assertEquals(bar, folderBarBounds())
    }

    /** These rows with [added] unread messages in the first. */
    private fun ChatListContent.Content.withUnread(added: Int) =
        if (added == 0) {
            this
        } else {
            val first = items.first()
            copy(
                items = (listOf(first.copy(unreadCount = first.unreadCount + added)) + items.drop(1)).toImmutableList(),
            )
        }

    private fun folderBarBounds() = composeRule.onNodeWithTag(FOLDER_BAR_TEST_TAG).fetchSemanticsNode().boundsInRoot

    private fun folderBarTop() = folderBarBounds().top

    private var connection by mutableStateOf(ConnectionStatus.Ready)

    /** Unread messages added to the first row, as a new message does. */
    private var rowChange by mutableStateOf(0)

    private var summary by mutableStateOf(ChatListPreviewData.summary)

    /** The folder selected, which a swipe's [ChatListEvent.SelectFolder] changes, as the view model does. */
    private var selectedFolder by mutableStateOf<Int?>(null)

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
        folderSwipe: Boolean = false,
    ) {
        this.connection = connection
        val items =
            (0 until ROWS)
                .map { index ->
                    val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                    base.copy(id = FIRST_ID + index)
                }.toImmutableList()
        // The leading pinned chats as one group, as the view model gives them.
        val content = ChatListContent.Content(items, pinnedCount = items.indexOfFirst { !it.isPinned })
        // Family, beside Work: other chats, as the view model gives a neighbour.
        val familyItems = items.map { it.copy(id = it.id + ROWS) }.toImmutableList()
        val familyContent = ChatListContent.Content(familyItems, 0)
        selectedFolder = WORK.takeIf { folderSwipe }
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
                        CompositionLocalProvider(LocalChatSwipes provides ChatSwipes(changesFolder = folderSwipe)) {
                            val family = selectedFolder == FAMILY
                            ChatListScreen(
                                state =
                                    ChatListUiState(
                                        content = if (family) familyContent else content.withUnread(rowChange),
                                        connection = this.connection,
                                        folderTabs = ChatListPreviewData.folders,
                                        selectedFolderId = selectedFolder,
                                    ),
                                onEvent = { if (it is ChatListEvent.SelectFolder) selectedFolder = it.folderId },
                                listState = listState,
                                stories = stories.takeIf { withStories },
                                // Read by the header alone, as the route hands it over.
                                summary = { summary },
                                neighbours = { neighboursOf(selectedFolder, content, familyContent) },
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(SETTLE_FRAMES) { frame() }
        counter.start()
    }

    /** Family after Work, as the view model gives them: each beside the other, with its own chats. */
    private fun neighboursOf(
        selected: Int?,
        work: ChatListContent,
        family: ChatListContent,
    ) = when (selected) {
        WORK -> FolderNeighbourLists(next = FolderNeighbourList(FAMILY, folderState(FAMILY, family)))
        FAMILY -> FolderNeighbourLists(previous = FolderNeighbourList(WORK, folderState(WORK, work)))
        else -> FolderNeighbourLists()
    }

    private fun folderState(
        folderId: Int,
        content: ChatListContent,
    ) = ChatListUiState(content = content, folderTabs = ChatListPreviewData.folders, selectedFolderId = folderId)

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

        /** 250 px: less than a screen of rows. */
        const val SHORT_SCROLL_FRAMES = 10

        /** At most one recomposition in this many frames counts as rare, nothing like one in every frame. */
        const val RARE = 10

        /** Enough to collapse the header and scroll the list past its first rows. */
        const val COLLAPSE_DRAG_FRAMES = 60

        /** Three flings, each a quick drag up of [FLING_DRAG_FRAMES] frames, then the frames it flies on. */
        const val FLINGS = 3
        const val FLING_DRAG_FRAMES = 8
        const val FLING_STEP = 60f
        const val DRAG_STEP = 10f

        const val SETTLE_FRAMES = 60

        /** Part of the width, slowly: the neighbour shows, short of the third that would select it. */
        const val SIDE_DRAG = 0.15f
        const val SIDE_DRAG_MILLIS = 400L

        /** Past the third of the width that selects the folder beside. */
        const val LANDING_DRAG = 0.6f
        const val ROW = "ChatRow"

        /** The folder bar's files: the bar, its tabs, their chips and menu, and "Edit folders". */
        val BAR_FILES = listOf("FolderBarKt", "FolderTabsKt", "FolderChipsKt", "FolderTabMenuKt", "EditFoldersButtonKt")

        /** The chat rows' files. */
        val ROW_FILES = listOf("ChatRow", "ChatMetaKt", "ChatListItemKt", "PreviewTextKt", "HighlightedTextKt")

        const val WORK = 1
        const val FAMILY = 2
        const val FOLDER_SLIDE = "FolderSlideKt"
        const val SCREEN_KEY = "chats"
        const val CHAT_LIST_TEST_TAG = "chat_list"
        const val STORY_RINGS = "StoryRingsKt"
        const val HEADER = "ChatListTopBarKt"
        const val HEADER_ENTRY = "ChatListTopBarKt.ChatListScreenTopBar"
        const val BAR_ENTRY = "FolderBarKt.FolderBar"
        const val HEADER_STATUS = "ChatListHeaderStatusKt"
    }
}
