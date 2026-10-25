package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.SavedPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.saved.LocalConversationTabs
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Saved Messages with its tabs (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs"): every control named with a 48 dp
 * target, the tabs heard as tabs with the selected one on, a tap selecting its topic, and the composer's place saying
 * where new messages go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SavedTabsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<Long?>()

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalConversationTabs provides {
                        SavedTabs(SavedPreviewData.tabs, onSelect = { selected += it }, onNearEnd = {})
                    },
                ) {
                    ChatScreen(state = SavedPreviewData.savedFromChannel, onEvent = {}, onBack = {})
                }
            }
        }
    }

    @Test
    fun `every control is named and large enough`() {
        setScreen()

        composeRule.assertAccessible()
    }

    @Test
    fun `the tabs are tabs, the selected one on`() {
        setScreen()

        val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.fetchSemanticsNodes().let { assertEquals(SavedPreviewData.tabs.tabs.size, it.size) }
        composeRule.onNodeWithText("Android Developers").assertIsOn()
        composeRule.onNodeWithText(string(R.string.saved_tab_all)).assertIsOff()
    }

    @Test
    fun `tapping a tab selects its topic`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.saved_tab_my_notes)).performClick()

        assertEquals(listOf(SavedPreviewData.tabs.tabs[1].topicId), selected)
    }

    @Test
    fun `a chat's tab says where new messages go`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.composer_saved_topic)).assertExists()
    }
}
