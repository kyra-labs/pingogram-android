package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
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
                        if (event is ChatListEvent.SelectFolder) state = state.copy(selectedFolderId = event.folderId)
                    },
                )
            }
        }
    }

    private fun swipe(gesture: TouchInjectionScope.() -> Unit) {
        row(ADA).performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    private fun selectedFolders() = events.filterIsInstance<ChatListEvent.SelectFolder>().map { it.folderId }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("$name,", substring = true) and hasClickAction())

    private companion object {
        const val ADA = "Ada Lovelace"
        const val ADA_ID = 2L
        const val WORK = 1
        const val FAMILY = 2
        const val CHANNELS = 3

        /** A tenth of the width, short of the third a swipe must cross, and slow enough not to count as a fling. */
        const val SHORT_SWIPE = 0.1f
    }
}
