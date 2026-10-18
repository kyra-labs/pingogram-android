package dev.kyralabs.pingogram.feature.chat.ui.content

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PollModelTest {
    private fun option(
        index: Int,
        percentage: Int = 0,
        isChosen: Boolean = false,
        isCorrect: Boolean = false,
    ) = PollOption(
        index = index,
        text = FormattedText("Option $index", persistentListOf()),
        voterCount = percentage,
        votePercentage = percentage,
        isChosen = isChosen,
        isCorrect = isCorrect,
    )

    private fun poll(
        vararg options: PollOption,
        quiz: Boolean = false,
        anonymous: Boolean = true,
        closed: Boolean = false,
    ) = Poll(
        id = 1,
        question = FormattedText("Question", persistentListOf()),
        options = options.toList().toImmutableList(),
        totalVoterCount = 100,
        isAnonymous = anonymous,
        allowsMultipleAnswers = false,
        canSeeResults = true,
        isClosed = closed,
        type = if (quiz) PollType.Quiz(FormattedText.Empty) else PollType.Regular,
    )

    @Test
    fun `the label says whether a poll or a quiz is anonymous`() {
        assertEquals(PollLabel.AnonymousPoll, pollLabel(poll()))
        assertEquals(PollLabel.PublicPoll, pollLabel(poll(anonymous = false)))
        assertEquals(PollLabel.AnonymousQuiz, pollLabel(poll(quiz = true)))
        assertEquals(PollLabel.PublicQuiz, pollLabel(poll(quiz = true, anonymous = false)))
    }

    @Test
    fun `a closed poll shows its final results`() {
        assertEquals(PollLabel.Closed, pollLabel(poll(closed = true)))
        assertEquals(PollLabel.Closed, pollLabel(poll(quiz = true, closed = true)))
    }

    @Test
    fun `the chosen answer of a poll is marked`() {
        val chosen = option(0, isChosen = true)
        val other = option(1)
        val regular = poll(chosen, other)

        assertEquals(PollMark.Chosen, pollMark(chosen, regular))
        assertEquals(PollMark.None, pollMark(other, regular))
    }

    @Test
    fun `a quiz marks the correct answers and a wrong choice`() {
        val wrong = option(0, isChosen = true)
        val correct = option(1, isCorrect = true)
        val other = option(2)
        val quiz = poll(wrong, correct, other, quiz = true)

        assertEquals(PollMark.Wrong, pollMark(wrong, quiz))
        assertEquals(PollMark.Correct, pollMark(correct, quiz))
        assertEquals(PollMark.None, pollMark(other, quiz))
    }

    @Test
    fun `a correct choice in a quiz is marked correct`() {
        val right = option(0, isChosen = true, isCorrect = true)

        assertEquals(PollMark.Correct, pollMark(right, poll(right, quiz = true)))
    }

    @Test
    fun `bars show TDLib's percentages as shares`() {
        assertEquals(0f, pollShare(option(0, percentage = 0)))
        assertEquals(0.41f, pollShare(option(0, percentage = 41)))
        assertEquals(1f, pollShare(option(0, percentage = 100)))
    }

    @Test
    fun `shares stay between 0 and 1`() {
        assertEquals(0f, pollShare(option(0, percentage = -5)))
        assertEquals(1f, pollShare(option(0, percentage = 120)))
    }
}
