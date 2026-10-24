package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.PollVoteRestriction
import dev.kyralabs.pingogram.feature.chat.R

/** The line under a poll's question that says what kind of poll it is, or that it is closed. */
internal enum class PollLabel(
    @param:StringRes val text: Int,
) {
    AnonymousPoll(R.string.content_poll_anonymous),
    PublicPoll(R.string.content_poll_public),
    AnonymousQuiz(R.string.content_quiz_anonymous),
    PublicQuiz(R.string.content_quiz_public),
    Closed(R.string.content_poll_closed),
}

internal fun pollLabel(poll: Poll): PollLabel =
    when {
        poll.isClosed -> PollLabel.Closed
        poll.type is PollType.Quiz -> if (poll.isAnonymous) PollLabel.AnonymousQuiz else PollLabel.PublicQuiz
        else -> if (poll.isAnonymous) PollLabel.AnonymousPoll else PollLabel.PublicPoll
    }

/** How an answer is marked once a poll's results show. */
internal enum class PollMark {
    None,

    /** The user's answer in a regular poll. */
    Chosen,

    /** A correct answer of a quiz, chosen or not. */
    Correct,

    /** The user's answer to a quiz, when it is wrong. */
    Wrong,
}

internal fun pollMark(
    option: PollOption,
    poll: Poll,
): PollMark {
    val isQuiz = poll.type is PollType.Quiz
    return when {
        isQuiz && option.isCorrect -> PollMark.Correct
        isQuiz && option.isChosen -> PollMark.Wrong
        option.isChosen -> PollMark.Chosen
        else -> PollMark.None
    }
}

/**
 * The share of the votes [option] got, 0–1, for its bar. TDLib computes the percentages so that they add up to at most
 * 100 and answers with as many votes get the same percentage (a largest remainder method that keeps ties); in polls
 * with several answers per voter they are each rounded on their own.
 */
internal fun pollShare(option: PollOption): Float =
    option.votePercentage.coerceIn(0, MAX_PERCENT) / MAX_PERCENT.toFloat()

private const val MAX_PERCENT = 100

/**
 * Whether the poll shows its results rather than the answers to vote for: once the user may see them, unless they can
 * still vote (the poll's creator, before voting) or their vote is on its way.
 */
internal val Poll.showsResults: Boolean get() = canSeeResults && !canVote && !isVoting

/** Whether the results offer "View votes": a public poll whose voters TDLib lists, someone having voted. */
internal val Poll.showsVoters: Boolean
    get() = showsResults && !isAnonymous && canGetVoters && totalVoterCount > 0

/**
 * Why the user cannot vote, while the poll shows no results; null where they can, once it shows its results, or once
 * it is closed ("Final results" says so).
 */
@StringRes
internal fun pollRestriction(poll: Poll): Int? {
    if (poll.showsResults || poll.isClosed) return null
    return when (poll.voteRestriction) {
        null, PollVoteRestriction.Closed -> null
        is PollVoteRestriction.MembershipRequired -> R.string.poll_restricted_members
        is PollVoteRestriction.CountryRestricted -> R.string.poll_restricted_country
        PollVoteRestriction.Scheduled, PollVoteRestriction.NotSent -> R.string.poll_restricted_scheduled
        PollVoteRestriction.Other -> R.string.poll_restricted_other
    }
}

/** A quiz's explanation, once the user answered it or it closed; null before, for polls and when there is none. */
internal fun pollExplanation(poll: Poll): FormattedText? =
    (poll.type as? PollType.Quiz)?.explanation?.takeIf { it.text.isNotEmpty() && (poll.hasVoted || poll.isClosed) }

/** What a tap in a poll asks for, which the bubble turns into its message's event. */
internal sealed interface PollAction {
    /** Vote for answer [optionIndex] (`PollOption.index`) at once: a poll with one answer per voter. */
    data class Vote(
        val optionIndex: Int,
    ) : PollAction

    /** Check or uncheck answer [optionIndex] of a poll with several answers. */
    data class Toggle(
        val optionIndex: Int,
    ) : PollAction

    /** Send the checked answers. */
    data object Submit : PollAction

    /** Open the voters' sheet. */
    data object ShowVoters : PollAction
}
