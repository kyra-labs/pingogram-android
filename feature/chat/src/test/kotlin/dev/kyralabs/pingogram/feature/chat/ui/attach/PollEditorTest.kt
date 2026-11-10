package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import dev.kyralabs.pingogram.core.domain.model.PollLimits
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What makes a poll in [PollEditor] complete, how options come, go and move, and what the poll carries. */
class PollEditorTest {
    private fun editor(
        question: String,
        vararg options: String,
    ) = PollEditor(PollDraft(question = question, options = options.toList()))

    private fun options(vararg texts: String) = persistentListOf(*texts.map { NewPollOption(it) }.toTypedArray())

    @Test
    fun `a new form has two empty options, anonymous voting and changing the vote`() {
        val editor = PollEditor()

        assertEquals(listOf("", ""), editor.options.map { it.text.text.toString() })
        assertTrue(editor.isAnonymous)
        assertTrue(editor.allowsRevoting)
        assertNull(editor.poll())
    }

    @Test
    fun `a poll needs a question and two filled-in options, empty ones are left out`() {
        assertNull(editor("", "Pizza", "Sushi").poll())
        assertNull(editor("Lunch?", "Pizza", " ").poll())
        assertEquals(
            NewPoll("Lunch?", options("Pizza", "Sushi"), isAnonymous = true, allowsMultipleAnswers = false),
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
    fun `options stay between two and Telegram's limit`() {
        val editor = PollEditor()
        editor.limits = PollLimits(maxOptions = 12)

        editor.removeOption(editor.options[0].key)
        assertEquals(2, editor.options.size)
        repeat(14) { editor.addOption() }

        assertEquals(12, editor.options.size)
        assertFalse(editor.canAddOption)
        assertNull(editor.addOption())
    }

    @Test
    fun `options move to another place, out of range nothing moves`() {
        val editor = editor("Book?", "A", "B", "C")

        editor.moveOption(0, 2)
        editor.moveOption(1, 7)

        assertEquals(listOf("B", "C", "A"), editor.options.map { it.text.text.toString() })
    }

    @Test
    fun `an option's link goes with it, and taking it away drops it`() {
        val editor = editor("Book?", "A", "B")
        editor.options[0].toggleLink()
        editor.options[0].link!!.setTextAndPlaceCursorAtEnd(" https://example.org ")
        editor.options[1].toggleLink()

        assertEquals(
            listOf(NewPollOption("A", "https://example.org"), NewPollOption("B")),
            editor.poll()!!.options,
        )
        editor.options[0].toggleLink()
        assertNull(editor.poll()!!.options[0].link)
    }

    @Test
    fun `a quiz needs a filled-in correct option, takes several and forgets a removed one`() {
        val editor = editor("Kotlin keywords?", "suspend", "val", "defer")
        editor.isQuiz = true
        editor.allowsMultipleAnswers = true
        editor.explanation.setTextAndPlaceCursorAtEnd("defer isn't one")
        assertNull(editor.poll())

        editor.setCorrect(editor.options[0].key, true)
        editor.setCorrect(editor.options[1].key, true)
        val poll = editor.poll()!!
        editor.removeOption(editor.options[1].key)

        assertEquals(persistentListOf(0, 1), poll.correctOptionIndexes)
        assertEquals(listOf<Any>(false, false, "defer isn't one"), listOf(poll.allowsMultipleAnswers, poll.allowsRevoting, poll.explanation))
        assertEquals(listOf(editor.options[0].key), editor.correctOptions)
    }

    @Test
    fun `the correct options count among the filled-in ones`() {
        val editor = editor("2 + 2?", "", "3", "4")
        editor.isQuiz = true
        editor.setCorrect(editor.options[2].key, true)

        assertEquals(persistentListOf(1), editor.poll()?.correctOptionIndexes)
    }

    @Test
    fun `channels force anonymous voting, which leaves out members adding options`() {
        val editor = editor("Next?", "A", "B")
        editor.isAnonymous = false
        editor.allowsAddingOptions = true

        assertTrue(editor.poll()!!.allowsAddingOptions)
        val inChannel = editor.poll(forceAnonymous = true)!!
        assertTrue(inChannel.isAnonymous)
        assertFalse(inChannel.allowsAddingOptions)
    }

    @Test
    fun `a poll closes after its period, at a moment in the future, or never`() {
        val editor = editor("Next?", "A", "B")
        editor.limits = PollLimits(maxOpenPeriod = 2_678_400)

        editor.closing = PollClosing.Day
        assertEquals(86_400 to 0L, editor.poll(now = 1_000)!!.let { it.openPeriod to it.closeDate })
        editor.closing = PollClosing.Custom
        assertNull(editor.poll(now = 1_000))
        editor.customCloseDate = 500
        assertNull(editor.poll(now = 1_000))
        editor.customCloseDate = 5_000
        assertEquals(0 to 5_000L, editor.poll(now = 1_000)!!.let { it.openPeriod to it.closeDate })
    }

    @Test
    fun `a closing time beyond Telegram's limit is not offered`() {
        val editor = PollEditor()

        assertFalse(editor.canClose(PollClosing.Hour))
        editor.limits = PollLimits(maxOpenPeriod = 7_200)

        assertTrue(editor.canClose(PollClosing.Hour))
        assertFalse(editor.canClose(PollClosing.Day))
        assertTrue(editor.canClose(PollClosing.Custom))
    }

    @Test
    fun `the photo, the description and the settings go with the poll`() {
        val photo = Attachment.Photo("/uploads/a/photo.jpg", 1_000, 640, 480)
        val editor = editor("Book?", "A", "B")
        editor.photo = photo
        editor.description.setTextAndPlaceCursorAtEnd(" On Dec 14 ")
        editor.hidesResults = true
        editor.shufflesOptions = true
        editor.allowsRevoting = false

        val poll = editor.poll()!!

        assertEquals(listOf<Any?>(photo, "On Dec 14", true, true, false), listOf(poll.photo, poll.description, poll.hidesResults, poll.shufflesOptions, poll.allowsRevoting))
    }

    @Test
    fun `the draft holds the form, and clearing empties it`() {
        val editor = editor("2 + 2?", "3", "4")
        editor.isQuiz = true
        editor.setCorrect(editor.options[1].key, true)
        editor.options[0].toggleLink()
        editor.closing = PollClosing.Hour

        assertEquals(
            PollDraft("2 + 2?", listOf("3", "4"), links = listOf("", null), isQuiz = true, correctOptionIndexes = listOf(1), closing = PollClosing.Hour),
            editor.draft,
        )
        assertEquals(editor.draft, PollEditor(editor.draft).draft)
        editor.question.setTextAndPlaceCursorAtEnd("x")
        editor.clear()

        assertEquals(PollDraft(options = listOf("", ""), links = listOf(null, null)), editor.draft)
    }
}
