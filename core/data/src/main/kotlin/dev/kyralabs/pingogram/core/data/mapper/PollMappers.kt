package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.PollVoteRestriction
import dev.kyralabs.pingogram.core.domain.model.PollVoter
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * The poll with its options in display order; each option keeps its TDLib index for voting. td_api.tl's
 * `correct_option_ids` and `setPollAnswer.option_ids` are such indexes: 0-based positions in `poll.options`.
 */
internal fun TdApi.Poll.toPoll(): Poll {
    val correctIndexes = (type as? TdApi.PollTypeQuiz)?.correctOptionIds?.toSet().orEmpty()
    val options =
        options.mapIndexed { index, option ->
            PollOption(
                index = index,
                text = option.text.toFormattedText(),
                voterCount = option.voterCount,
                votePercentage = option.votePercentage,
                isChosen = option.isChosen,
                isCorrect = index in correctIndexes,
                isBeingChosen = option.isBeingChosen,
            )
        }
    return Poll(
        id = id,
        question = question.toFormattedText(),
        options = options.inDisplayOrder(optionOrder),
        totalVoterCount = totalVoterCount,
        isAnonymous = isAnonymous,
        allowsMultipleAnswers = allowsMultipleAnswers,
        canSeeResults = canSeeResults,
        isClosed = isClosed,
        type =
            (type as? TdApi.PollTypeQuiz)?.let { PollType.Quiz(it.explanation.toFormattedText()) } ?: PollType.Regular,
        canGetVoters = canGetVoters,
        allowsRevoting = allowsRevoting,
        voteRestriction = voteRestrictionReason?.toVoteRestriction(),
    )
}

/** In [order] (0-based indexes) if TDLib gives one that lists every option once; otherwise as they are. */
private fun List<PollOption>.inDisplayOrder(order: IntArray): ImmutableList<PollOption> =
    if (order.sorted() == indices.toList()) order.map(::get).toImmutableList() else toImmutableList()

private fun TdApi.PollVoteRestrictionReason.toVoteRestriction(): PollVoteRestriction =
    when (this) {
        is TdApi.PollVoteRestrictionReasonClosed -> PollVoteRestriction.Closed

        is TdApi.PollVoteRestrictionReasonScheduled -> PollVoteRestriction.Scheduled

        is TdApi.PollVoteRestrictionReasonYetUnsent -> PollVoteRestriction.NotSent

        is TdApi.PollVoteRestrictionReasonMembershipRequired -> PollVoteRestriction.MembershipRequired(chatId)

        is TdApi.PollVoteRestrictionReasonCountryRestricted -> PollVoteRestriction.CountryRestricted(countryCode)

        // PollVoteRestrictionReasonOther, and a reason a newer TDLib adds.
        else -> PollVoteRestriction.Other
    }

/** A page of voters; a voter of a kind of sender a newer TDLib adds is left out. */
internal fun TdApi.PollVoters.toPollVoters(): PollVoters =
    PollVoters(
        totalCount = totalCount,
        voters =
            voters
                .mapNotNull { voter -> voter.voterId?.toMessageSender()?.let { PollVoter(it, voter.date.toLong()) } }
                .toImmutableList(),
    )
