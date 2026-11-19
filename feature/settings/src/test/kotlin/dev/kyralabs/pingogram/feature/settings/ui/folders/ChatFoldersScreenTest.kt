package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.FolderTabStyle
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFoldersPreviewStates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The "Folder tabs" group of the Chat folders page (owner decision 2026-11-19): what the tabs show, and the Edit
 * folders button and Feed tab as switch rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatFoldersScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatFoldersEvent>()

    private fun setScreen(tabs: ChatListSettings? = ChatListSettings()) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatFoldersScreen(
                    state = ChatFoldersPreviewStates.content,
                    onEvent = { events += it },
                    onOpenFolder = {},
                    onBack = {},
                    tabs = tabs,
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun scrollTo(key: String) = composeRule.onNode(hasScrollToKeyAction()).performScrollToKey(key)

    private fun switchRow(
        @StringRes title: Int,
    ) = composeRule.onNode(
        hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    private fun on() = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)

    @Test
    fun `picking what the tabs show saves it`() {
        setScreen()

        scrollTo("tabStyle")
        composeRule.onNodeWithText(string(R.string.settings_folder_tabs_icon)).performClick()

        assertEquals(listOf(ChatFoldersEvent.SetFolderTabStyle(FolderTabStyle.Icon)), events)
    }

    @Test
    fun `the Edit folders button row is a switch, on at first, whose tap turns the button off`() {
        setScreen()

        scrollTo("editFoldersButton")
        switchRow(R.string.settings_edit_folders_button)
            .assert(on())
            .assert(hasText(string(R.string.settings_edit_folders_button_summary)))
            .performClick()

        assertEquals(listOf(ChatFoldersEvent.SetShowEditFoldersButton(false)), events)
    }

    @Test
    fun `the Show Feed tab row is a switch, on at first, whose tap hides the tab`() {
        setScreen()

        scrollTo("feedTab")
        switchRow(R.string.settings_show_feed_tab)
            .assert(on())
            .assert(hasText(string(R.string.settings_show_feed_tab_summary)))
            .performClick()

        assertEquals(listOf(ChatFoldersEvent.SetShowFeedTab(false)), events)
    }

    @Test
    fun `the group waits for the settings to be read`() {
        setScreen(tabs = null)

        composeRule.onNodeWithText(string(R.string.settings_chat_folders_section_tabs)).assertDoesNotExist()
    }
}
