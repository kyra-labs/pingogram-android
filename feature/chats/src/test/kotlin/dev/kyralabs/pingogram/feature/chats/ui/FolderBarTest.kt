package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.component.floatingNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The folder tabs' floating bar over the top of the chat list's sheet (`docs/DESIGN_SYSTEM.md` §13.3; owner decision
 * 2026-11-13): the lists start below it, scroll under it while it stays where it is, screen readers reach it first,
 * and the list still shows the rows §7's density check asks for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FolderBarTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Test
    fun `the list's first row starts below the bar`() {
        setScreen()

        assertTrue("${firstRowTop()} below ${barBottom()}", firstRowTop() >= barBottom())
    }

    @Test
    fun `back at the top after scrolling, the first row is below the bar again`() {
        setScreen()
        composeRule.runOnIdle { listState.requestScrollToItem(index = SCROLLED_INDEX) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { listState.requestScrollToItem(index = 0) }
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertTrue("${firstRowTop()} below ${barBottom()}", firstRowTop() >= barBottom())
    }

    @Test
    fun `the bar stays where it is while the rows scroll under it`() {
        setScreen()
        val list = composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG)
        // Collapse the header first: the bar moves up with the sheet, then stays.
        drag(list, COLLAPSE_DRAG_STEPS)
        val barTop = bar().top

        drag(list, SCROLL_DRAG_STEPS)

        assertEquals(barTop, bar().top, 0.5f)
        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 0)
    }

    @Test
    fun `screen readers reach the tabs before the list`() {
        setScreen()

        val config = composeRule.onNodeWithTag(FOLDER_BAR_TEST_TAG).fetchSemanticsNode().config
        assertEquals(true, config.getOrNull(SemanticsProperties.IsTraversalGroup))
        assertEquals(-1f, config.getOrNull(SemanticsProperties.TraversalIndex))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `nine rows show below the bar once the header collapsed`() {
        setScreen(pinned = false)
        drag(composeRule.onNodeWithTag(CHAT_LIST_TEST_TAG), COLLAPSE_DRAG_STEPS)

        // Rows partly under the folder bar count as covered; the floating navigation bar floats over rows that show
        // beside it and fade behind it (§6.1), so §7 counts the rows down to the window's bottom.
        val layout = listState.layoutInfo
        val end = layout.viewportEndOffset
        val whole = layout.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= end }
        val sizes = layout.visibleItemsInfo.map { "${it.index}@${it.offset}+${it.size}" }
        assertTrue(
            "$whole whole rows: viewport ${layout.viewportStartOffset}..${layout.viewportEndOffset}, " +
                "padding ${layout.beforeContentPadding}/${layout.afterContentPadding}, items $sizes",
            whole >= DENSITY_ROWS,
        )
    }

    private fun setScreen(pinned: Boolean = true) {
        val items =
            (0 until ROWS)
                .map { index ->
                    val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                    base.copy(id = FIRST_ID + index, isPinned = pinned && base.isPinned)
                }.toImmutableList()
        val pinnedCount = items.indexOfFirst { !it.isPinned }.coerceAtLeast(0)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalNavigationBarInsets provides floatingNavigationBarInsets()) {
                    listState = rememberLazyListState()
                    ChatListScreen(
                        state =
                            ChatListUiState(
                                content = ChatListContent.Content(items, pinnedCount),
                                connection = ConnectionStatus.Ready,
                                folderTabs = ChatListPreviewData.folders,
                            ),
                        onEvent = {},
                        listState = listState,
                        onEditFolder = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun drag(
        list: SemanticsNodeInteraction,
        steps: Int,
    ) {
        list.performTouchInput {
            down(center)
            repeat(steps) { moveBy(Offset(0f, -DRAG_STEP)) }
            up()
        }
        composeRule.waitForIdle()
    }

    private fun bar() = composeRule.onNodeWithTag(FOLDER_BAR_TEST_TAG).fetchSemanticsNode().boundsInRoot

    private fun barBottom() = bar().bottom

    /** Where the list's first item would start: its top plus the padding above its items. */
    private fun firstRowTop(): Float {
        val listTop =
            composeRule
                .onNodeWithTag(CHAT_LIST_TEST_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.top
        val first = listState.layoutInfo.visibleItemsInfo.first { it.index == 0 }
        return listTop + listState.layoutInfo.beforeContentPadding + first.offset
    }

    private companion object {
        const val ROWS = 60
        const val FIRST_ID = 1_000L
        const val SCROLLED_INDEX = 30
        const val DRAG_STEP = 10f
        const val COLLAPSE_DRAG_STEPS = 60
        const val SCROLL_DRAG_STEPS = 30

        /** `docs/DESIGN_SYSTEM.md` §7: at least nine rows on a 412 × 915 dp phone once the header collapsed. */
        const val DENSITY_ROWS = 9
        const val CHAT_LIST_TEST_TAG = "chat_list"
    }
}
