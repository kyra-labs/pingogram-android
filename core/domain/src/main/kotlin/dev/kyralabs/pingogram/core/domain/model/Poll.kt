package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/** A poll or a quiz (`REQUIREMENTS.md` FR-C4). */
data class Poll(
    val id: Long,
    val question: FormattedText,
    /** In the order in which they must be shown, which can differ from [PollOption.index]. */
    val options: ImmutableList<PollOption>,
    val totalVoterCount: Int,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    /** The user may see the results: they voted, the poll is closed, or they created it. */
    val canSeeResults: Boolean,
    val isClosed: Boolean,
    val type: PollType,
)

/**
 * One answer of a [Poll]. [index] identifies it when voting (0-based, TDLib's order). [voterCount] and
 * [votePercentage] (0–100) are known only when the results may be seen.
 */
data class PollOption(
    val index: Int,
    val text: FormattedText,
    val voterCount: Int,
    val votePercentage: Int,
    /** The user chose this answer. */
    val isChosen: Boolean,
    /** A correct answer of a quiz, once the user answered it or it is closed; false in regular polls. */
    val isCorrect: Boolean,
)

sealed interface PollType {
    data object Regular : PollType

    /** A quiz with correct answers; [explanation] is shown after a wrong answer, empty until the quiz was answered. */
    data class Quiz(
        val explanation: FormattedText,
    ) : PollType
}
