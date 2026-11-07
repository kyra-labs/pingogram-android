package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Swiping the whole chat list to the folder tab beside the selected one (Swipe actions → "Swipe to change folders"):
 * right shows the tab on the left, left the one on the right, mirrored right to left; nothing past the first or last
 * tab, in selection mode, in the archive or without folders; a short swipe springs back. Rows do not swipe meanwhile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListFolderSwipeTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatListEvent>()

    // Work, between "All chats" and Family.
    private var state by mutableStateOf(ChatListPreviewData.contentWithFolders.copy(selectedFolderId = WORK))

    /** Each folder its own chats ([folderState]) once set, so the lists sliding against each other tell apart. */
    private var distinctFolders = false

    /** All chats: the preview's chats; Work: Analytical Engine Club to Orbital; Family: Katherine to Free Crypto. */
    private fun folderState(folderId: Int?): ChatListUiState {
        val items = ChatListPreviewData.items
        val chats =
            when (folderId) {
                WORK -> items.subList(WORK_FROM, WORK_FROM + FOLDER_SIZE)
                FAMILY -> items.subList(FAMILY_FROM, FAMILY_FROM + FOLDER_SIZE)
                null -> items
                else -> items.takeLast(FOLDER_SIZE)
            }.toImmutableList()
        val pinned = chats.count { it.isPinned }
        return ChatListPreviewData.contentWithFolders.copy(
            selectedFolderId = folderId,
            content = ChatListContent.Content(chats, pinnedCount = pinned),
            archive = null,
        )
    }

    @Test
    fun `swiping right shows the folder on the left`() {
        setScreen()

        swipe { swipeRight() }

        assertEquals(listOf(null), selectedFolders())
    }

    @Test
    fun `swiping left shows the folder on the right`() {
        setScreen()

        swipe { swipeLeft() }

        assertEquals(listOf(FAMILY), selectedFolders())
    }

    @Test
    fun `right to left, swiping left shows the folder on the left`() {
        setScreen(LayoutDirection.Rtl)

        swipe { swipeLeft() }

        assertEquals(listOf(null), selectedFolders())
    }

    @Test
    fun `swipes go on from folder to folder`() {
        setScreen()

        swipe { swipeLeft() }
        swipe { swipeLeft() }

        assertEquals(listOf(FAMILY, CHANNELS), selectedFolders())
    }

    @Test
    fun `nothing lies before the first tab`() {
        state = state.copy(selectedFolderId = null)
        setScreen()

        swipe { swipeRight() }

        assertEquals(emptyList<Int?>(), selectedFolders())
    }

    @Test
    fun `a short swipe springs back`() {
        setScreen()

        swipe { swipeLeft(startX = centerX, endX = centerX - width * SHORT_SWIPE) }

        assertEquals(emptyList<Int?>(), selectedFolders())
    }

    @Test
    fun `rows do not swipe while the list does`() {
        setScreen()

        swipe { swipeLeft() }

        assertEquals(emptyList<ChatListEvent>(), events.filterIsInstance<ChatListEvent.RowAction>())
    }

    @Test
    fun `rows swipe as before while it is off`() {
        setScreen(changesFolder = false)

        swipe { swipeLeft() }

        assertEquals(emptyList<Int?>(), selectedFolders())
        assertEquals(1, events.filterIsInstance<ChatListEvent.RowAction>().size)
    }

    @Test
    fun `nothing changes in selection mode`() {
        state = state.copy(selection = ChatSelection(persistentSetOf(ADA_ID), persistentListOf()))
        setScreen()

        swipe { swipeLeft() }

        assertEquals(emptyList<Int?>(), selectedFolders())
    }

    @Test
    fun `nothing changes without folders`() {
        state = ChatListPreviewData.content
        setScreen()

        swipe { swipeLeft() }

        assertEquals(emptyList<Int?>(), selectedFolders())
    }

    @Test
    fun `tapping a tab after the shown one slides its list in from the end`() {
        assertSlidesIn(tab = "Family", row = KATHERINE, fromEnd = true)
    }

    @Test
    fun `tapping a tab before the shown one slides its list in from the start`() {
        assertSlidesIn(tab = ALL_CHATS, row = ADA, fromEnd = false)
    }

    @Test
    fun `right to left, a tab after the shown one slides its list in from the left`() {
        assertSlidesIn(tab = "Family", row = KATHERINE, fromEnd = true, layoutDirection = LayoutDirection.Rtl)
    }

    @Test
    fun `a swipe that changes the folder leaves its list in place, with no slide in after it`() {
        distinctFolders = true
        state = folderState(WORK)
        setScreen()
        val resting = restingLeft()

        swipe(ANALYTICAL) { swipeLeft() }
        val left = row(KATHERINE).fetchSemanticsNode().boundsInRoot.left

        assertEquals(listOf(FAMILY), selectedFolders())
        assertEquals(resting, left, 1f)
    }

    /**
     * Taps [tab] while Work shows: [row] of its list comes in from the end ([fromEnd], the right left to right) or the
     * start, and settles where rows rest.
     */
    private fun assertSlidesIn(
        tab: String,
        row: String,
        fromEnd: Boolean,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        distinctFolders = true
        state = folderState(WORK)
        setScreen(layoutDirection)
        val resting = restingLeft()
        composeRule.mainClock.autoAdvance = false

        tab(tab).performClick()
        repeat(SLIDE_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        val sliding = row(row).fetchSemanticsNode().boundsInRoot
        // Laid out, not a node of the frame that composes it, whose bounds read as empty.
        assertTrue("$sliding", sliding.width > 0f)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        val towardRight = fromEnd == (layoutDirection == LayoutDirection.Ltr)
        val left = sliding.left
        assertTrue("$left against $resting", if (towardRight) left > resting + 1f else left < resting - 1f)
        assertEquals(resting, row(row).fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    /** Where a row rests, its left edge: the Work list's first row, before anything moves. */
    private fun restingLeft(): Float = row(ANALYTICAL).fetchSemanticsNode().boundsInRoot.left

    private fun setScreen(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        changesFolder: Boolean = true,
    ) = composeRule.setContent {
        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
            LocalChatSwipes provides ChatSwipes(changesFolder = changesFolder),
        ) {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        if (event is ChatListEvent.SelectFolder) {
                            state =
                                if (distinctFolders) {
                                    folderState(event.folderId)
                                } else {
                                    state.copy(selectedFolderId = event.folderId)
                                }
                        }
                    },
                )
            }
        }
    }

    private fun swipe(
        rowName: String = ADA,
        gesture: TouchInjectionScope.() -> Unit,
    ) {
        row(rowName).performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    private fun tab(label: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasContentDescription(label, substring = true) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )

    private fun selectedFolders() = events.filterIsInstance<ChatListEvent.SelectFolder>().map { it.folderId }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("$name,", substring = true) and hasClickAction())

    private companion object {
        const val ADA = "Ada Lovelace"
        const val ANALYTICAL = "Analytical Engine Club"
        const val KATHERINE = "Katherine Johnson"
        const val ALL_CHATS = "All chats"
        const val WORK_FROM = 2
        const val FAMILY_FROM = 5
        const val FOLDER_SIZE = 3

        /** Frames into a slide: the selected list is still on its way in. */
        const val SLIDE_FRAMES = 6
        const val ADA_ID = 2L
        const val WORK = 1
        const val FAMILY = 2
        const val CHANNELS = 3

        /** A tenth of the width, short of the third a swipe must cross, and slow enough not to count as a fling. */
        const val SHORT_SWIPE = 0.1f
    }
}
