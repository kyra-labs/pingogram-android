package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The folder tabs as a Material 3 Expressive button group (FR-L3, `docs/DESIGN_SYSTEM.md` §6): every folder stays a
 * tab when they do not fit, the selected one always in view, and a pressed tab grows while its neighbours shrink.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FolderTabsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<Int?>()

    @Test
    fun `folders that do not fit stay tabs, reached by scrolling`() {
        setTabs(LONG_TABS)

        tab("Archive of old projects").performScrollTo().assertIsDisplayed().performClick()
        tab("All chats").performScrollTo().assertIsDisplayed()

        assertEquals(listOf<Int?>(5), selected)
    }

    @Test
    fun `the selected folder is brought into view, at once and when another is selected`() {
        setTabs(LONG_TABS, selectedFolderId = 5)
        tab("Archive of old projects").assertIsDisplayed()

        selectedFolderId = null
        composeRule.waitForIdle()
        tab("All chats").assertIsDisplayed()

        selectedFolderId = 3
        composeRule.waitForIdle()
        tab("Channels I read every day").assertIsDisplayed()
    }

    @Test
    fun `a pressed tab grows and squeezes its neighbours`() {
        setTabs(
            persistentListOf(
                FolderTab.AllChats(unreadCount = 0),
                FolderTab.Folder(id = 1, title = "Work", unreadCount = 0),
                FolderTab.Folder(id = 2, title = "Family", unreadCount = 0),
            ),
        )
        val before = widths()

        tab("Work").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(PRESS_MILLIS)
        val pressed = widths()
        tab("Work").performTouchInput { up() }

        assertTrue("Work grows: $before → $pressed", pressed[1] > before[1])
        assertTrue("All chats shrinks: $before → $pressed", pressed[0] < before[0])
        assertTrue("Family shrinks: $before → $pressed", pressed[2] < before[2])
        assertEquals("The group keeps its width", before.sum(), pressed.sum())
    }

    @Test
    fun `tapping a tab selects its folder, All chats as null`() {
        setTabs(persistentListOf(FolderTab.AllChats(0), FolderTab.Folder(id = 1, title = "Work", unreadCount = 0)))

        tab("Work").performClick()
        tab("All chats").performClick()

        assertEquals(listOf(1, null), selected)
    }

    @Test
    fun `screen readers hear selected tabs, not checked toggle buttons`() {
        setTabs(
            persistentListOf(FolderTab.AllChats(0), FolderTab.Folder(id = 1, title = "Work", unreadCount = 0)),
            selectedFolderId = 1,
        )

        tab("Work")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        tab("All chats").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))

        selectedFolderId = null
        composeRule.waitForIdle()
        tab("All chats").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        tab("Work").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    private var selectedFolderId by mutableStateOf<Int?>(null)

    private fun setTabs(
        tabs: ImmutableList<FolderTab>,
        selectedFolderId: Int? = null,
    ) {
        this.selectedFolderId = selectedFolderId
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FolderTabs(tabs = tabs, selectedFolderId = this.selectedFolderId, onSelect = { selected += it })
            }
        }
    }

    /** A tab, named by its label (and unread count, if any). */
    private fun tab(label: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasContentDescription(label, substring = true) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )

    private fun widths(): List<Int> =
        listOf("All chats", "Work", "Family").map { tab(it).fetchSemanticsNode().size.width }

    private companion object {
        /** Six folders with long names: more than a compact window shows. */
        val LONG_TABS =
            persistentListOf(
                FolderTab.AllChats(unreadCount = 0),
                FolderTab.Folder(id = 1, title = "Colleagues from work", unreadCount = 3),
                FolderTab.Folder(id = 2, title = "Family and close friends", unreadCount = 0),
                FolderTab.Folder(id = 3, title = "Channels I read every day", unreadCount = 12),
                FolderTab.Folder(id = 4, title = "Bots and automations", unreadCount = 0),
                FolderTab.Folder(id = 5, title = "Archive of old projects", unreadCount = 1),
            )

        /** Long enough for the press to be reported (it waits for a possible scroll) and its spring to settle. */
        const val PRESS_MILLIS = 1_000L
    }
}
