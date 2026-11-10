package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChecklistUi
import dev.kyralabs.pingogram.feature.chat.ui.content.ChecklistAction
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageChecklist
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChecklistPreviewData
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Checklists on screen (`REQUIREMENTS.md` FR-C4): what a tap on a task, "Add task" and the inline field send, with
 * their haptics; a checklist the user may not change takes no tap; screen readers hear each task within the bubble
 * and tick through its custom actions; every control has a name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChecklistScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ChecklistAction>()
    private val events = mutableListOf<ChatEvent>()
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setChecklist(
        content: dev.kyralabs.pingogram.core.domain.model.MessageContent.Checklist,
        ui: ChecklistUi = ChecklistUi(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    MessageChecklist(content, ui = ui, onAction = { actions += it })
                }
            }
        }
    }

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `a tap on a task ticks it with a toggle haptic, and on a done task unticks it`() {
        setChecklist(ContentPreviewData.checklist(done = 1))

        composeRule.onNodeWithText("Update Play Store screenshots").performClick()
        composeRule.onNodeWithText("Freeze strings for translation").performClick()

        assertEquals(listOf(ChecklistAction.Mark(2, done = true), ChecklistAction.Mark(1, done = false)), actions)
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), performedHaptics)
    }

    @Test
    fun `a checklist the user may not tick takes no tap, and offers no Add task`() {
        setChecklist(ContentPreviewData.checklist(canMark = false, canAdd = false))

        composeRule.onNodeWithText("Write the changelog").assertHasNoClickAction()
        composeRule.onNodeWithText("Write the changelog").performClick()

        assertTrue(actions.isEmpty())
        assertTrue(composeRule.onAllNodes(hasTextIgnoringCase(string(R.string.checklist_add_task))).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a task whose change is on its way takes no tap`() {
        setChecklist(ContentPreviewData.checklist(), ui = ChecklistUi(pending = persistentSetOf(3)))

        composeRule.onNodeWithText("Run the TDLib upgrade tests").performClick()

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `Add task opens the field`() {
        setChecklist(ContentPreviewData.checklist())

        composeRule.onNodeWithText(string(R.string.checklist_add_task)).performClick()

        assertEquals(listOf<ChecklistAction>(ChecklistAction.OpenAddTask), actions)
    }

    @Test
    fun `the field's check sends the text, and its close puts the field away`() {
        setChecklist(ContentPreviewData.checklist(), ui = ChecklistUi(isAddingTask = true))

        composeRule.onNodeWithText(string(R.string.checklist_new_task)).performTextInput("Order chai")
        composeRule.onNodeWithContentDescription(string(R.string.checklist_add)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.checklist_cancel_add)).performClick()

        assertEquals(listOf(ChecklistAction.AddTask("Order chai"), ChecklistAction.DismissAddTask), actions)
    }

    @Test
    fun `screen readers hear the checklist within the bubble and tick through its actions`() {
        setScreen(ChecklistPreviewData.lists)

        val bubble =
            composeRule
                .onNode(hasContentDescription("Pingogram 1.2 release", substring = true))
                .fetchSemanticsNode()
        val description = bubble.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
        val custom = bubble.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val mark = custom.first { it.label == string(R.string.checklist_mark_done, "Run the TDLib upgrade tests") }
        mark.action()

        assertTrue(description, description.contains(string(R.string.checklist_kicker)))
        assertTrue(description, description.contains(string(R.string.checklist_progress, 2, 5)))
        assertTrue(description, description.contains("Freeze strings for translation, " + string(R.string.checklist_task_done_by, "Ada Lovelace")))
        assertTrue(custom.any { it.label == string(R.string.checklist_add_task) })
        assertEquals(listOf<ChatEvent>(ChecklistEvent.MarkTask(2, taskId = 3, done = true)), events.filterIsInstance<ChecklistEvent>())
    }

    @Test
    fun `every control has a name and a touch target`() {
        setScreen(ChecklistPreviewData.states)

        composeRule.assertAccessible()
    }

    private fun hasTextIgnoringCase(text: String): SemanticsMatcher =
        SemanticsMatcher("has text $text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.equals(text, ignoreCase = true) } == true
        }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}
