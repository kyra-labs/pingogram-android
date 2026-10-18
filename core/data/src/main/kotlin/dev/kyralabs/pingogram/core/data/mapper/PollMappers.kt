package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** The poll with its options in display order; each option keeps its TDLib index for voting. */
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
    )
}

/** In [order] (0-based indexes) if TDLib gives one that lists every option once; otherwise as they are. */
private fun List<PollOption>.inDisplayOrder(order: IntArray): ImmutableList<PollOption> =
    if (order.sorted() == indices.toList()) order.map(::get).toImmutableList() else toImmutableList()
