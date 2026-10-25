package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.preview.TopicsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Managing a forum's topics on screen (`REQUIREMENTS.md` FR-C9): the long press menu per the user's rights, "New
 * topic" and its editor, the question before deleting, moving pinned topics, and that every control has a name and a
 * touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TopicManagementScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ForumTopicsEvent>()
    private val topicEvents = mutableListOf<TopicEvent>()

    private fun show(state: ForumTopicsUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ForumTopicsScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpenTopic = {},
                    onBack = {},
                    onTopicEvent = { topicEvents += it },
                )
            }
        }
    }

    private fun longPress(name: String) {
        composeRule
            .onNode(
                hasContentDescription(name, substring = true)
                    .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)),
            ).performTouchInput { longClick() }
    }

    @Test
    fun `an administrator's long press offers every action, and each sends its event`() {
        show(TopicsPreviewData.managed)

        longPress("Help")
        composeRule.onNodeWithText("Edit name").assertExists()
        composeRule.onNodeWithText("Pin").assertExists()
        composeRule.onNodeWithText("Delete topic").assertExists()
        composeRule.onNodeWithText("Close topic").performClick()

        longPress("Releases")
        composeRule.onNodeWithText("Reopen topic").performClick()

        assertEquals(listOf(TopicEvent.SetClosed(12, true), TopicEvent.SetClosed(14, false)), topicEvents)
    }

    @Test
    fun `General's menu hides or shows it, and offers no close, pin or delete`() {
        show(TopicsPreviewData.managed)

        longPress("General")
        composeRule.onNodeWithText("Close topic").assertDoesNotExist()
        composeRule.onNodeWithText("Delete topic").assertDoesNotExist()
        composeRule.onNodeWithText("Show General").performClick()

        assertEquals(listOf<TopicEvent>(TopicEvent.SetGeneralHidden(false)), topicEvents)
    }

    @Test
    fun `without rights a long press opens nothing and there is no "New topic"`() {
        show(TopicsPreviewData.content)

        composeRule.onNodeWithContentDescription("New topic").assertDoesNotExist()
        val rows =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                .fetchSemanticsNodes()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `"New topic" opens the editor, which sends the name, the color and "Create"`() {
        show(TopicsPreviewData.managed.copy(management = TopicManagementUiState(TopicsPreviewData.newTopic())))

        composeRule.onNodeWithText("Topic name").performTextReplacement("Roadmap")
        composeRule.onNodeWithContentDescription("Rose").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(
            listOf(
                TopicEvent.NameChanged("Roadmap"),
                TopicEvent.ColorPicked(TopicColor.Rose),
                TopicEvent.Save,
                TopicEvent.DismissEditor,
            ),
            topicEvents,
        )
    }

    @Test
    fun `the top bar's "New topic" sends its event`() {
        show(TopicsPreviewData.managed)

        composeRule.onNodeWithContentDescription("New topic").performClick()

        assertEquals(listOf<TopicEvent>(TopicEvent.NewTopic), topicEvents)
    }

    @Test
    fun `a name too long cannot be created`() {
        show(
            TopicsPreviewData.managed.copy(
                management = TopicManagementUiState(TopicsPreviewData.newTopic().copy(name = "A".repeat(129))),
            ),
        )
        composeRule.onNodeWithText("The name is too long").assertExists()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `the editor of a rename has no color picker`() {
        show(TopicsPreviewData.managed.copy(management = TopicManagementUiState(TopicsPreviewData.renaming)))

        composeRule.onNodeWithText("Edit topic").assertExists()
        composeRule.onNodeWithContentDescription("Rose").assertDoesNotExist()
        composeRule.onNodeWithText("A topic keeps the color it was created with.").assertExists()
    }

    @Test
    fun `deleting asks first, and only "Delete" confirms`() {
        show(TopicsPreviewData.managed.copy(management = TopicManagementUiState(deleting = TopicsPreviewData.deleting)))

        composeRule.onNodeWithText("Delete topic?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(TopicEvent.DismissDelete, TopicEvent.ConfirmDelete), topicEvents)
    }

    @Test
    fun `screen readers move pinned topics and reach the menu's actions`() {
        show(TopicsPreviewData.managed)

        val row =
            composeRule.onNode(
                hasContentDescription(
                    "Announcements",
                    substring = true,
                ).and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)),
            )
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(
            listOf("Edit name", "Close topic", "Unpin", "Delete topic", "Move down"),
            actions.map { it.label },
        )
        actions.first { it.label == "Move down" }.action.invoke()

        assertEquals(
            listOf<ForumTopicsEvent>(ForumTopicsEvent.Move(5, up = false)),
            events.filterIsInstance<ForumTopicsEvent.Move>(),
        )
        assertTrue(row.fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `the list's and the menu's controls have names and touch targets`() {
        show(TopicsPreviewData.managed)
        composeRule.assertAccessible()

        longPress("Help")
        composeRule.assertAccessible()
    }

    @Test
    fun `the question's controls have names and touch targets`() {
        show(TopicsPreviewData.managed.copy(management = TopicManagementUiState(deleting = TopicsPreviewData.deleting)))
        composeRule.assertAccessible()
    }

    @Test
    fun `the editor's controls have names and touch targets`() {
        show(TopicsPreviewData.managed.copy(management = TopicManagementUiState(TopicsPreviewData.newTopic())))
        composeRule.assertAccessible()
    }
}
