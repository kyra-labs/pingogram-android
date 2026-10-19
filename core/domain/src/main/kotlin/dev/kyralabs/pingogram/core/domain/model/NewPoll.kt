package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A poll the user creates (`REQUIREMENTS.md` FR-C5): a [question] and its answer [options], within the limits of
 * td_api.tl `inputMessagePoll` ([MAX_QUESTION_LENGTH], [MIN_OPTIONS]–[MAX_OPTIONS] options of up to
 * [MAX_OPTION_LENGTH] characters). A quiz has one correct answer, [correctOptionIndex] in [options]; a regular poll has
 * none (null) and may allow several answers ([allowsMultipleAnswers], never in a quiz). Voters' names show unless
 * [isAnonymous]; channels allow anonymous polls only.
 *
 * `toString()` leaves the texts out.
 */
data class NewPoll(
    val question: String,
    val options: ImmutableList<String>,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val correctOptionIndex: Int?,
) {
    val isQuiz: Boolean get() = correctOptionIndex != null

    override fun toString() =
        "NewPoll(options=${options.size}, isAnonymous=$isAnonymous, allowsMultipleAnswers=$allowsMultipleAnswers, " +
            "correctOptionIndex=$correctOptionIndex, …)"

    companion object {
        /** The longest question, in characters (td_api.tl: 1–255 for users). */
        const val MAX_QUESTION_LENGTH = 255

        /** The longest answer option, in characters (td_api.tl `inputPollOption`: 1–100). */
        const val MAX_OPTION_LENGTH = 100

        const val MIN_OPTIONS = 2

        /** At most this many options; Telegram's `poll_answer_count_max` allows at least as many. */
        const val MAX_OPTIONS = 10
    }
}
