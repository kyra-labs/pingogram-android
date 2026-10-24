package dev.kyralabs.pingogram.feature.chat.ui.poll

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.PollEvent
import dev.kyralabs.pingogram.feature.chat.ui.content.PollMark
import dev.kyralabs.pingogram.feature.chat.ui.content.pollExplanation
import dev.kyralabs.pingogram.feature.chat.ui.content.pollLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.pollMark
import dev.kyralabs.pingogram.feature.chat.ui.content.pollRestriction
import dev.kyralabs.pingogram.feature.chat.ui.content.showsResults
import dev.kyralabs.pingogram.feature.chat.ui.content.showsVoters
import java.text.NumberFormat

/**
 * What screen readers hear of [bubble]'s poll within its bubble's description (`docs/DESIGN_SYSTEM.md` §9): what kind
 * of poll it is, then each answer as one entry ("Spring, 45%, 9 votes, your answer"; a quiz's "correct answer" or
 * "wrong answer"; before voting, whether it is checked or its vote on its way), why the user cannot vote, the number
 * of voters and a quiz's explanation. Empty for other content. The question is the bubble's content label.
 */
internal fun pollDescription(
    resources: Resources,
    bubble: ChatItem.Bubble,
): String {
    val poll = (bubble.content as? MessageContent.Poll)?.poll ?: return ""
    val choices = bubble.pollChoices.orEmpty()
    val number = NumberFormat.getIntegerInstance(resources.configuration.locales[0])
    val options =
        poll.options.map { option ->
            val parts =
                buildList {
                    add(option.text.text)
                    if (poll.showsResults) {
                        add(
                            resources.getQuantityString(
                                R.plurals.poll_option_share,
                                option.voterCount,
                                option.votePercentage,
                                number.format(option.voterCount),
                            ),
                        )
                        markText(pollMark(option, poll))?.let { add(resources.getString(it)) }
                    } else {
                        if (option.index in choices) add(resources.getString(R.string.poll_option_checked))
                        if (option.isBeingChosen) add(resources.getString(R.string.poll_option_sending))
                    }
                }
            parts.joinToString(", ")
        }
    return (
        listOf(resources.getString(pollLabel(poll).text)) +
            options +
            listOfNotNull(
                pollRestriction(poll)?.let(resources::getString),
                totalText(resources, poll, number),
                pollExplanation(poll)?.let { "${resources.getString(R.string.poll_explanation)}: ${it.text}" },
            )
    ).joinToString(", ")
}

/**
 * The custom actions of [bubble]'s poll, labels and events: "Vote for Spring" for each answer where one tap votes;
 * "Choose …" / "Unchoose …" and then "Vote" with answers checked where several are allowed; "View votes" with a public
 * poll's results. None for other content and polls the user cannot act on.
 */
internal fun pollActions(
    resources: Resources,
    bubble: ChatItem.Bubble,
): List<Pair<String, ChatEvent>> {
    val poll = (bubble.content as? MessageContent.Poll)?.poll ?: return emptyList()
    val choices = bubble.pollChoices.orEmpty()
    return when {
        poll.canVote && poll.allowsMultipleAnswers -> {
            poll.options.map { option ->
                val label = if (option.index in choices) R.string.poll_uncheck else R.string.poll_check
                resources.getString(label, option.text.text) to PollEvent.Toggle(bubble.id, option.index)
            } +
                listOfNotNull(
                    (resources.getString(R.string.poll_vote) to PollEvent.SubmitVotes(bubble.id))
                        .takeIf { choices.isNotEmpty() },
                )
        }

        poll.canVote -> {
            poll.options.map { option ->
                resources.getString(R.string.poll_vote_for, option.text.text) to PollEvent.Vote(bubble.id, option.index)
            }
        }

        poll.showsVoters -> {
            listOf(resources.getString(R.string.poll_view_votes) to PollEvent.OpenVoters(bubble.id))
        }

        else -> {
            emptyList()
        }
    }
}

private fun markText(mark: PollMark): Int? =
    when (mark) {
        PollMark.None -> null
        PollMark.Chosen -> R.string.content_poll_chosen
        PollMark.Correct -> R.string.content_quiz_correct
        PollMark.Wrong -> R.string.content_quiz_wrong
    }

private fun totalText(
    resources: Resources,
    poll: Poll,
    number: NumberFormat,
): String {
    val count = poll.totalVoterCount
    val isQuiz = poll.type is PollType.Quiz
    return when {
        count <= 0 && isQuiz -> resources.getString(R.string.content_quiz_no_answers)
        count <= 0 -> resources.getString(R.string.content_poll_no_votes)
        isQuiz -> resources.getQuantityString(R.plurals.content_quiz_answers, count, number.format(count))
        else -> resources.getQuantityString(R.plurals.content_poll_votes, count, number.format(count))
    }
}
