package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A touch that stops the folder tabs' fling only stops it, as in Telegram's apps: held, it opens no tab's menu. On the
 * tabs at rest the same hold, at the same place, opens the menu of the tab there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FolderTabsFlingTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `holding a tab while the tabs fling opens no menu`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FolderTabs(tabs = TABS, selectedFolderId = null, onSelect = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val bar = tabs().first()
        screen().performTouchInput {
            swipe(Offset(width * START, bar.center.y), Offset(0f, bar.center.y), durationMillis = FLING_MILLIS)
        }
        val sliding = tabs().map { it.left }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertNotEquals("the tabs should still slide", sliding, tabs().map { it.left })

        // The second tab on screen: the first may be cut by the bar's start.
        val at = tabs()[1].center
        hold(at)
        assertEquals(0, menus())
        // The same hold on the tabs at rest, where the first one landed, opens the menu of the tab there.
        hold(at)

        assertEquals(1, menus())
    }

    /** The bounds of the tabs on screen, from the start. */
    private fun tabs(): List<Rect> =
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.width > 0f }
            .sortedBy { it.left }

    /** A finger down [at] a place, held past the long-press timeout, then lifted. */
    private fun hold(at: Offset) {
        screen().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        screen().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
    }

    /** The screen, not the menu's popup. */
    private fun screen() = composeRule.onAllNodes(isRoot()).onFirst()

    private fun menus() = composeRule.onAllNodesWithText(MARK_ALL_AS_READ).fetchSemanticsNodes().size

    private companion object {
        val TABS =
            (
                persistentListOf<FolderTab>(FolderTab.AllChats(unreadCount = 1)) +
                    (1..20).map { FolderTab.Folder(id = it, title = "Folder number $it", unreadCount = 1) }
            ).toImmutableList()
        const val MARK_ALL_AS_READ = "Mark all as read"
        const val START = 0.9f
        const val FLING_MILLIS = 60L
        const val FRAMES = 3

        /** Past the long-press timeout (400 ms). */
        const val HOLD_MILLIS = 1_000L
    }
}
