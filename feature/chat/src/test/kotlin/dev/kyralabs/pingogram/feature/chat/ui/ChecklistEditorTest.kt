package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChecklistDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChecklistEditor
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The form of a new checklist: what it sends, what keeps it incomplete, its limits and its draft. */
class ChecklistEditorTest {
    @Test
    fun `a title and one task make a checklist, empty tasks left out`() {
        val editor = ChecklistEditor()
        editor.title.setTextAndPlaceCursorAtEnd(" Release ")
        editor.tasks[0].text.setTextAndPlaceCursorAtEnd("Freeze strings")
        editor.addTask()

        assertEquals(
            NewChecklist(
                "Release",
                persistentListOf("Freeze strings"),
                othersCanAddTasks = false,
                othersCanMarkTasksAsDone = true,
            ),
            editor.checklist(),
        )
    }

    @Test
    fun `without a title or a task there is nothing to send`() {
        val editor = ChecklistEditor()
        editor.tasks[0].text.setTextAndPlaceCursorAtEnd("Freeze strings")
        assertNull(editor.checklist())

        editor.tasks[0].text.setTextAndPlaceCursorAtEnd("")
        editor.title.setTextAndPlaceCursorAtEnd("Release")
        assertNull(editor.checklist())
    }

    @Test
    fun `a task that repeats one above it is marked and keeps the checklist incomplete`() {
        val editor = ChecklistEditor(ChecklistDraft(title = "Release", tasks = listOf("Tag", " tag ")))

        assertTrue(editor.isDuplicate(editor.tasks[1].key))
        assertFalse(editor.isDuplicate(editor.tasks[0].key))
        assertNull(editor.checklist())
    }

    @Test
    fun `tasks are added up to thirty and removed down to one`() {
        val editor = ChecklistEditor()
        repeat(NewChecklist.MAX_TASKS + 5) { editor.addTask() }
        assertEquals(NewChecklist.MAX_TASKS, editor.tasks.size)
        assertFalse(editor.canAddTask)

        editor.tasks.toList().forEach { editor.removeTask(it.key) }
        assertEquals(NewChecklist.MIN_TASKS, editor.tasks.size)
        assertFalse(editor.canRemoveTask)
    }

    @Test
    fun `the draft holds what the form holds, and clearing empties it`() {
        val editor = ChecklistEditor(ChecklistDraft("Release", listOf("Tag", "Post"), othersCanAddTasks = true))

        assertEquals(ChecklistDraft("Release", listOf("Tag", "Post"), othersCanAddTasks = true), editor.draft)

        editor.clear()
        assertEquals(ChecklistDraft(tasks = listOf("")), editor.draft)
    }
}
