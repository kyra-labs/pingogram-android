package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatSearchPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * The search in the conversation on screen (`REQUIREMENTS.md` §5.9): the top bar's button opens it, the field takes
 * what is typed and closes it, the bar's buttons move through the results, pick a sender or a day, back closes it, and
 * screen readers hear every control and the result's bubble by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatSearchUiTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()
    private val query = TextFieldState()
    private var search by mutableStateOf<ChatSearchUiState?>(null)

    /** The group conversation, search opening as [opened] when the screen asks, and closing. */
    private fun setScreen(
        initial: ChatSearchUiState? = null,
        opened: ChatSearchUiState = ChatSearchPreviewData.found,
        chat: ChatUiState = ChatSearchPreviewData.groupChat,
    ) {
        search = initial
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                ChatScreen(
                    state = chat,
                    onEvent = { event ->
                        events += event
                        when (event) {
                            ChatSearchEvent.Open -> search = opened
                            ChatSearchEvent.Close -> search = null
                            else -> Unit
                        }
                    },
                    onBack = {},
                    search = search,
                    searchQuery = query,
                )
            }
        }
    }

    @Test
    fun `the top bar's search button opens search, whose field takes the focus and what is typed`() {
        setScreen()

        composeRule.onNodeWithContentDescription(string(R.string.search_open)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<ChatEvent>(ChatSearchEvent.Open), events.filterIsInstance<ChatSearchEvent>())
        val field = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
        field.performTextInput("motion")
        assertEquals("motion", query.text.toString())
    }

    @Test
    fun `the field's back button closes search, and its clear button empties it`() {
        query.edit { replace(0, length, "motion") }
        setScreen(ChatSearchPreviewData.found)

        composeRule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()
        assertEquals("", query.text.toString())
        composeRule.onNodeWithContentDescription(string(R.string.search_close)).performClick()

        assertEquals(ChatSearchEvent.Close, events.last())
        composeRule.onNodeWithContentDescription(string(R.string.search_open)).assertIsDisplayed()
    }

    @Test
    fun `the arrows move to the older and newer results, and the counter says which is shown`() {
        setScreen(ChatSearchPreviewData.found)

        composeRule.onNodeWithText("2 of 3").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithContentDescription(string(R.string.search_older)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.search_newer)).performClick()

        assertEquals(listOf(ChatSearchEvent.Older, ChatSearchEvent.Newer), events.filterIsInstance<ChatSearchEvent>())
    }

    @Test
    fun `without results the arrows are disabled and the bar says so`() {
        setScreen(ChatSearchPreviewData.noResults)

        composeRule.onNodeWithText(string(R.string.search_no_results)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.search_older)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(string(R.string.search_newer)).assertIsNotEnabled()
    }

    @Test
    fun `a failed search offers to try again`() {
        setScreen(ChatSearchPreviewData.failed)

        composeRule.onNodeWithText(string(R.string.search_retry)).performClick()

        assertEquals(ChatSearchEvent.Retry, events.last())
    }

    @Test
    fun `in a group the sender button opens the members, and the sender's chip removes them`() {
        setScreen(ChatSearchPreviewData.found)
        composeRule.onNodeWithContentDescription(string(R.string.search_by_sender)).assertIsEnabled().performClick()
        assertEquals(ChatSearchEvent.OpenSenders, events.last())

        search = ChatSearchPreviewData.withSender
        composeRule.onNodeWithContentDescription(string(R.string.search_sender_chip, "Grace Hopper")).performClick()

        assertEquals(ChatSearchEvent.ClearSender, events.last())
    }

    @Test
    fun `the calendar picks a day to jump to, today at first`() {
        setScreen(ChatSearchPreviewData.found)

        composeRule.onNodeWithContentDescription(string(R.string.search_jump_to_date)).performClick()
        composeRule.onNodeWithText(string(R.string.search_date_go)).performClick()

        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        assertEquals(ChatSearchEvent.JumpToDate(today), events.last())
    }

    @Test
    fun `back closes search`() {
        setScreen(ChatSearchPreviewData.found)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(ChatSearchEvent.Close, events.last())
    }

    @Test
    fun `screen readers hear that the result's bubble matches the search`() {
        setScreen(ChatSearchPreviewData.found)

        val match = string(R.string.chat_bubble_search_match)
        val matching =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("describes the match") { node ->
                        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { match in it } == true
                    },
                ).fetchSemanticsNodes()
        assertEquals(1, matching.size)
        assertTrue(
            matching
                .single()
                .config[SemanticsProperties.ContentDescription]
                .single()
                .contains("motion"),
        )
    }

    @Test
    fun `while search is closed the composer shows, without the search's bar`() {
        setScreen(chat = ChatPreviewData.privateChat)

        composeRule.onNodeWithContentDescription(string(R.string.search_older)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.search_open)).assertIsDisplayed()
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}
