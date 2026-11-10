package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A poll the user creates (`REQUIREMENTS.md` FR-C5): a [question] and its answer [options], within the limits of
 * td_api.tl `inputMessagePoll` ([MAX_QUESTION_LENGTH], from [MIN_OPTIONS] to Telegram's `poll_answer_count_max` options
 * of up to [MAX_OPTION_LENGTH] characters, `PollLimits`), with an optional [description] and [photo] (a prepared copy,
 * `AttachmentRepository.prepare`). A quiz has one or more correct answers, [correctOptionIndexes] in [options], and
 * then as many answers per voter as it has correct ones, with an optional [explanation]; a regular poll has none and
 * may allow several answers ([allowsMultipleAnswers]), changing the vote ([allowsRevoting]) and members adding answers
 * ([allowsAddingOptions], not in channels or anonymous polls). Voters' names show unless [isAnonymous]; channels allow
 * anonymous polls only. [hidesResults] keeps the results from voters until the poll closes, [shufflesOptions] shows
 * each voter the answers in an order of their own. The poll closes [openPeriod] seconds after it is sent, or at
 * [closeDate] (Unix time), or when its author stops it (both 0). `toString()` leaves the texts out.
 */
data class NewPoll(
    val question: String,
    val options: ImmutableList<NewPollOption>,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val correctOptionIndexes: ImmutableList<Int> = persistentListOf(),
    val description: String = "",
    val photo: Attachment.Photo? = null,
    val explanation: String = "",
    val allowsRevoting: Boolean = true,
    val hidesResults: Boolean = false,
    val shufflesOptions: Boolean = false,
    val allowsAddingOptions: Boolean = false,
    val openPeriod: Int = 0,
    val closeDate: Long = 0,
) {
    val isQuiz: Boolean get() = correctOptionIndexes.isNotEmpty()

    override fun toString() =
        "NewPoll(options=${options.size}, isAnonymous=$isAnonymous, allowsMultipleAnswers=$allowsMultipleAnswers, " +
            "correctOptionIndexes=$correctOptionIndexes, photo=${photo != null}, openPeriod=$openPeriod, " +
            "closeDate=$closeDate, …)"

    companion object {
        /** The longest question, in characters (td_api.tl: 1–255 for users). */
        const val MAX_QUESTION_LENGTH = 255

        /** The longest answer option, in characters (td_api.tl `inputPollOption`: 1–100). */
        const val MAX_OPTION_LENGTH = 100

        /** The longest quiz explanation, in characters (td_api.tl `inputPollTypeQuiz`: 0–200). */
        const val MAX_EXPLANATION_LENGTH = 200

        const val MIN_OPTIONS = 2
    }
}

/** One answer of a [NewPoll]: its [text], and a [link] it carries (`inputPollMediaLink`), if any. */
data class NewPollOption(
    val text: String,
    val link: String? = null,
) {
    override fun toString() = "NewPollOption(link=${link != null}, …)"
}

/**
 * What Telegram allows in a new poll (TDLib's options): at most [maxOptions] answers (`poll_answer_count_max`), and
 * open for at most [maxOpenPeriod] seconds (`poll_open_period_max`, 0 where it does not say: no time limit then).
 */
data class PollLimits(
    val maxOptions: Int = DEFAULT_MAX_OPTIONS,
    val maxOpenPeriod: Int = 0,
) {
    companion object {
        /** The answers allowed while TDLib has not said: as many as Telegram allowed before 2026. */
        const val DEFAULT_MAX_OPTIONS = 10
    }
}
