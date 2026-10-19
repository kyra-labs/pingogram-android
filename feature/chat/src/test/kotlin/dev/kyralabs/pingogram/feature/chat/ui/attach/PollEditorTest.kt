package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What makes a poll in [PollEditor] complete, and how options come and go. */
class PollEditorTest {
    private fun editor(
        question: String,
        vararg options: String,
    ) = PollEditor(PollDraft(question = question, options = options.toList()))

    @Test
    fun `a new form has two empty options and anonymous voting`() {
        val editor = PollEditor()

        assertEquals(listOf("", ""), editor.options.map { it.text.text.toString() })
        assertTrue(editor.isAnonymous)
        assertNull(editor.poll())
    }

    @Test
    fun `a poll needs a question and two filled-in options, empty ones are left out`() {
        assertNull(editor("", "Pizza", "Sushi").poll())
        assertNull(editor("Lunch?", "Pizza", " ").poll())
        assertEquals(
            NewPoll("Lunch?", persistentListOf("Pizza", "Sushi"), true, false, null),
            editor(" Lunch? ", "Pizza", "", " Sushi ").poll(),
        )
    }

    @Test
    fun `a repeated option, whatever its case, makes the poll incomplete and is marked`() {
        val editor = editor("Lunch?", "Pizza", "pizza ")

        assertNull(editor.poll())
        assertFalse(editor.isDuplicate(editor.options[0].key))
        assertTrue(editor.isDuplicate(editor.options[1].key))
    }

    @Test
    fun `options stay between two and ten`() {
        val editor = PollEditor()

        editor.removeOption(editor.options[0].key)
        assertEquals(2, editor.options.size)
        repeat(12) { editor.addOption() }

        assertEquals(NewPoll.MAX_OPTIONS, editor.options.size)
        assertFalse(editor.canAddOption)
        assertNull(editor.addOption())
    }

    @Test
    fun `a quiz needs a filled-in correct option, allows one answer, and forgets a removed correct option`() {
        val editor = editor("2 + 2?", "3", "4", "5")
        editor.isQuiz = true
        editor.allowsMultipleAnswers = true
        assertNull(editor.poll())

        editor.correctOption = editor.options[1].key
        assertEquals(NewPoll("2 + 2?", persistentListOf("3", "4", "5"), true, false, 1), editor.poll())
        editor.removeOption(editor.options[1].key)

        assertNull(editor.correctOption)
    }

    @Test
    fun `the correct option counts among the filled-in ones`() {
        val editor = editor("2 + 2?", "", "3", "4")
        editor.isQuiz = true
        editor.correctOption = editor.options[2].key

        assertEquals(1, editor.poll()?.correctOptionIndex)
    }

    @Test
    fun `channels force anonymous voting`() {
        val editor = editor("Next?", "A", "B")
        editor.isAnonymous = false

        assertFalse(editor.poll()!!.isAnonymous)
        assertTrue(editor.poll(forceAnonymous = true)!!.isAnonymous)
    }

    @Test
    fun `the draft holds the form, and clearing empties it`() {
        val editor = editor("2 + 2?", "3", "4")
        editor.isQuiz = true
        editor.correctOption = editor.options[1].key

        assertEquals(PollDraft("2 + 2?", listOf("3", "4"), true, false, true, 1), editor.draft)
        assertEquals(editor.draft, PollEditor(editor.draft).draft)
        editor.question.setTextAndPlaceCursorAtEnd("x")
        editor.clear()

        assertEquals(PollDraft(options = listOf("", "")), editor.draft)
    }
}
