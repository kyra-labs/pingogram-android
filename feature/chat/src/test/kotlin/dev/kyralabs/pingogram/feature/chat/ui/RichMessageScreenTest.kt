package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.RichPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A rich message's bubble: a tap on a section's header toggles it and "Show full message" loads the rest; screen
 * readers hear the blocks in the bubble's description, with each section's state, and toggle sections and load the
 * full message through its custom actions; every control has a name and a 48 dp touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT_TALL)
class RichMessageScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `a tap on a section's header toggles it`() {
        setScreen(RichPreviewData.collapsed())

        composeRule.onNodeWithText("Sizes", useUnmergedTree = true).performClick()

        assertEquals(listOf(RichMessageEvent.ToggleSection(POST, 1)), events.filterIsInstance<RichMessageEvent>())
    }

    @Test
    fun `Show full message loads the rest of a message Telegram sent only the start of`() {
        setScreen(partialNotLoading())

        composeRule.onNodeWithText(string(R.string.rich_show_full), useUnmergedTree = true).performClick()

        assertEquals(listOf(RichMessageEvent.LoadFull(POST)), events.filterIsInstance<RichMessageEvent>())
    }

    @Test
    fun `screen readers hear the blocks with each section's state, and only open sections' content`() {
        setScreen(RichPreviewData.collapsed())

        val description = description()

        assertTrue(description, "Material 3 Expressive" in description)
        assertTrue(description, "Section What's new, Collapsed" in description)
        assertFalse(description, "Springs for every motion" in description)
    }

    @Test
    fun `screen readers hear an open section's content, checklist items and tables`() {
        setScreen(RichPreviewData.expanded())

        val description = description()

        assertTrue(description, "Section What's new, Expanded" in description)
        assertTrue(description, "Done: Variable type" in description)
        assertTrue(description, "Table, 3 rows" in description)
    }

    @Test
    fun `screen readers toggle sections and load the full message through the bubble's actions`() {
        setScreen(partialNotLoading())

        performCustomAction(string(R.string.rich_section_expand, "What's new"))
        performCustomAction(string(R.string.rich_show_full))

        assertEquals(
            listOf(RichMessageEvent.ToggleSection(POST, 0), RichMessageEvent.LoadFull(POST)),
            events.filterIsInstance<RichMessageEvent>(),
        )
    }

    @Test
    fun `a conversation with a rich message has names and touch targets for every control`() {
        setScreen(RichPreviewData.expanded())

        composeRule.assertAccessible()
    }

    /** The post with only its start sent, its rest not loading yet. */
    private fun partialNotLoading(): ChatUiState {
        val state = RichPreviewData.partial()
        val content = state.content as ChatContent.Content
        val items =
            content.items.map { item ->
                if (item is ChatItem.Bubble) item.copy(rich = item.rich?.copy(isLoadingFull = false)) else item
            }
        return state.copy(content = content.copy(items = items.toImmutableList()))
    }

    private fun description(): String =
        composeRule
            .onNode(
                hasCustomAction(string(R.string.rich_section_expand, "Sizes"))
                    .or(hasCustomAction(string(R.string.rich_section_collapse, "Sizes"))),
            ).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun performCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private companion object {
        const val POST = 40L
    }
}
