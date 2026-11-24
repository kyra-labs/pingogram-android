package dev.kyralabs.pingogram.feature.chats.ui.drafts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.DraftsPreviewStates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Drafts page (`docs/DESIGN_SYSTEM.md` §6 "Drafts"): a row opening its chat, and the empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DraftsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<DraftsEvent>()

    private fun setPage(state: DraftsUiState) =
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { DraftsScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }

    @Test
    fun `a draft is read out with its text and opens its chat`() {
        val content = DraftsPreviewStates().values.first() as DraftsUiState.Content
        setPage(content)

        composeRule
            .onNodeWithContentDescription("Could you send me the notes", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Charles Babbage", substring = true, useUnmergedTree = true).performClick()

        assertEquals(listOf<DraftsEvent>(DraftsEvent.Open(content.drafts.first().chatId)), events)
    }

    @Test
    fun `without drafts the page says so`() {
        setPage(DraftsUiState.Empty)

        composeRule.onNodeWithText("No drafts").assertIsDisplayed()
    }
}
