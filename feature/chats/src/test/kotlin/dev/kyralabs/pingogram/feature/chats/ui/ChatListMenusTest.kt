package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The chat list's menus (FR-L3, FR-L9): a folder tab's long press ("Edit folder" where the app has a folder editor,
 * "Mark all as read" while anything is unread, the same as screen readers' custom actions), and the archive row's
 * styles (compact, full, hidden by its menu or a swipe), its stand-in in the top bar while hidden, and the archive's
 * settings button. The screen is stateless: these check its events and callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListMenusTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatListEvent>()
    private val edited = mutableListOf<Int?>()

    // Folder tabs

    @Test
    fun `a tab's long press offers to edit its folder first, then to mark it as read`() {
        setScreen(ChatListPreviewData.contentWithFolders, canEditFolders = true)

        tab("Work").performTouchInput { longClick() }

        val items = menuItems(string(R.string.chat_list_edit_folder), string(R.string.chat_list_mark_all_as_read))
        assertTrue("Edit folder comes first: $items", items[0] < items[1])
        composeRule.onNodeWithText(string(R.string.chat_list_edit_folder)).performClick()

        assertEquals(listOf<Int?>(1), edited)
        // The long press did not select the tab.
        assertTrue(events.none { it is ChatListEvent.SelectFolder })
    }

    @Test
    fun `All chats edits the folders, with no folder`() {
        setScreen(ChatListPreviewData.contentWithFolders, canEditFolders = true)

        tab(string(R.string.chat_list_all_chats)).performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_edit_folders)).performClick()

        assertEquals(listOf<Int?>(null), edited)
    }

    @Test
    fun `mark all as read reads the folder, or the main list for All chats`() {
        setScreen(ChatListPreviewData.contentWithFolders)

        tab("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_mark_all_as_read)).performClick()
        tab(string(R.string.chat_list_all_chats)).performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_mark_all_as_read)).performClick()

        assertEquals(listOf(ChatListEvent.MarkAllAsRead(1), ChatListEvent.MarkAllAsRead(null)), events)
    }

    @Test
    fun `without a folder editor a tab with nothing unread has no menu`() {
        setScreen(ChatListPreviewData.contentWithFolders)

        tab("Family").assertCustomActions()
        tab("Family").performTouchInput { longClick() }

        composeRule.onNode(hasText(string(R.string.chat_list_mark_all_as_read))).assertDoesNotExist()
        composeRule.onNode(hasText(string(R.string.chat_list_edit_folder))).assertDoesNotExist()
    }

    @Test
    fun `screen readers get the menu as the tab's custom actions`() {
        setScreen(ChatListPreviewData.contentWithFolders, canEditFolders = true)

        tab("Work").assertCustomActions(
            string(R.string.chat_list_edit_folder),
            string(R.string.chat_list_mark_all_as_read),
        )
        tab("Family").assertCustomActions(string(R.string.chat_list_edit_folder))
        tab("Work").runCustomAction(string(R.string.chat_list_mark_all_as_read))

        assertEquals(listOf(ChatListEvent.MarkAllAsRead(1)), events)
    }

    @Test
    fun `in selection mode the tabs have no menu`() {
        setScreen(ChatListPreviewData.selecting, canEditFolders = true)

        tab("Work").assertCustomActions()
        tab("Work").performTouchInput { longClick() }

        composeRule.onNode(hasText(string(R.string.chat_list_edit_folder))).assertDoesNotExist()
    }

    @Test
    fun `more tabs than a button group holds keep their menus`() {
        val many =
            (listOf<FolderTab>(FolderTab.AllChats(3)) + (1..7).map { FolderTab.Folder(it, "Folder $it", it % 2) })
                .toImmutableList()
        setScreen(ChatListPreviewData.contentWithFolders.copy(folderTabs = many), canEditFolders = true)

        tab("Folder 1").performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_mark_all_as_read)).performClick()

        assertEquals(listOf(ChatListEvent.MarkAllAsRead(1)), events)
    }

    // The archive row

    @Test
    fun `the archive row's long press makes it compact or hides it`() {
        setScreen(ChatListPreviewData.contentWithArchive)

        archiveRow().performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_archive_row_collapse)).performClick()
        archiveRow().performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_archive_row_hide)).performClick()

        assertEquals(
            listOf(
                ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Compact),
                ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Hidden),
            ),
            events,
        )
    }

    @Test
    fun `a compact archive row shows the names again`() {
        setScreen(ChatListPreviewData.contentWithCompactArchive)

        archiveRow().performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.chat_list_archive_row_expand)).performClick()

        assertEquals(listOf(ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Full)), events)
    }

    @Test
    fun `a compact archive row is one line, a list item's 56 dp`() {
        setScreen(ChatListPreviewData.contentWithCompactArchive)
        val density = composeRule.density

        val height =
            with(density) {
                archiveRow()
                    .fetchSemanticsNode()
                    .size.height
                    .toDp()
            }

        assertTrue("$height", height.value in ONE_LINE_ROW_DP..ONE_LINE_ROW_DP + 2)
    }

    @Test
    fun `swiping the archive row away either way hides it`() {
        setScreen(ChatListPreviewData.contentWithArchive)

        archiveRow().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Hidden)), events)
    }

    @Test
    fun `the archive row does not swipe or open a menu in selection mode`() {
        setScreen(ChatListPreviewData.selecting.copy(archive = ChatListPreviewData.archive))

        archiveRow().assertIsNotEnabled()
        archiveRow().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertTrue(events.none { it is ChatListEvent.SetArchiveRowStyle })
    }

    @Test
    fun `screen readers get the archive row's styles as custom actions`() {
        setScreen(ChatListPreviewData.contentWithArchive)

        archiveRow().assertCustomActions(
            string(R.string.chat_list_archive_row_collapse),
            string(R.string.chat_list_archive_row_hide),
        )
        archiveRow().runCustomAction(string(R.string.chat_list_archive_row_hide))

        assertEquals(listOf(ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Hidden)), events)
    }

    @Test
    fun `while hidden the top bar's archive button opens the archive, and says what is unread`() {
        setScreen(ChatListPreviewData.contentWithHiddenArchive)

        composeRule
            .onNodeWithContentDescription(string(R.string.chat_list_archived_chats), substring = true)
            .assert(hasContentDescription("2", substring = true))
            .performClick()

        assertEquals(listOf(ChatListEvent.OpenArchive), events)
    }

    @Test
    fun `the archive's top bar opens its settings`() {
        setScreen(ChatListPreviewData.archived)

        composeRule.onNodeWithContentDescription(string(R.string.archive_settings_title)).performClick()

        assertEquals(listOf(ChatListEvent.OpenArchiveSettings), events)
    }

    private fun setScreen(
        state: ChatListUiState,
        canEditFolders: Boolean = false,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(
                    state = state,
                    onEvent = ::record,
                    onEditFolder = if (canEditFolders) ({ edited += it }) else null,
                )
            }
        }
    }

    private fun record(event: ChatListEvent) {
        if (event !is ChatListEvent.NearEndChanged) events += event
    }

    /** A tab, named by its label (and unread count, if any). */
    private fun tab(label: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasContentDescription(label, substring = true) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )

    private fun archiveRow(): SemanticsNodeInteraction =
        composeRule.onNode(
            hasContentDescription("${string(R.string.chat_list_archived_chats)},", substring = true) and
                hasClickAction(),
        )

    /** Where the menu shows [labels], top to bottom. */
    private fun menuItems(vararg labels: String): List<Float> =
        labels.map {
            composeRule
                .onNodeWithText(it)
                .fetchSemanticsNode()
                .boundsInRoot.top
        }

    private fun SemanticsNodeInteraction.assertCustomActions(vararg labels: String) {
        val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(labels.toList(), actions.map { it.label })
    }

    private fun SemanticsNodeInteraction.runCustomAction(label: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnUiThread { actions.single { it.label == label }.action() }
        composeRule.waitForIdle()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        /** Material's one-line list item. */
        const val ONE_LINE_ROW_DP = 56f
    }
}
