package dev.kyralabs.pingogram.feature.chat.ui.content

import android.content.res.Resources
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollMedia
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.feature.chat.R
import java.net.URI
import java.util.Locale

// How the parts of polls Telegram added in 2026 are worded (board 4a, 4b), for the bubble and screen readers alike.

/**
 * The kicker over a poll's question (board 4a), in capitals for the eye ([pollKicker] keeps its case for screen
 * readers): "Poll" or "Quiz", then what sets it apart: closed, a quiz's correct answers once known, several answers,
 * or whether voters' names show.
 */
internal fun pollKicker(
    resources: Resources,
    poll: Poll,
): String {
    val isQuiz = poll.type is PollType.Quiz
    val kind = resources.getString(if (isQuiz) R.string.content_quiz_kind else R.string.content_poll_kind)
    val correct = poll.correctOptionCount
    val detail =
        when {
            poll.isClosed -> resources.getString(R.string.content_poll_tag_closed)
            isQuiz && correct > 1 -> resources.getQuantityString(R.plurals.content_quiz_tag_correct, correct, correct)
            !isQuiz && poll.allowsMultipleAnswers -> resources.getString(R.string.content_poll_tag_multiple)
            poll.isAnonymous -> resources.getString(R.string.content_poll_tag_anonymous)
            else -> resources.getString(R.string.content_poll_tag_public)
        }
    return resources.getString(R.string.content_poll_kicker, kind, detail)
}

/** [pollKicker] in capitals, as the board sets it, in [locale]'s rules. */
internal fun pollKickerShown(
    resources: Resources,
    poll: Poll,
    locale: Locale,
): String = pollKicker(resources, poll).uppercase(locale)

/** "Ends in 2 h": how long a poll that closes by itself stays open, from [seconds] left; null for none. */
internal fun pollEndsText(
    resources: Resources,
    seconds: Long?,
): String? {
    seconds ?: return null
    val minutes = ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
    return when {
        minutes < MINUTES_PER_HOUR -> {
            resources.getQuantityString(R.plurals.poll_ends_in_minutes, minutes, minutes)
        }

        minutes < MINUTES_PER_DAY -> {
            val hours = minutes / MINUTES_PER_HOUR
            resources.getQuantityString(R.plurals.poll_ends_in_hours, hours, hours)
        }

        else -> {
            val days = minutes / MINUTES_PER_DAY
            resources.getQuantityString(R.plurals.poll_ends_in_days, days, days)
        }
    }
}

/**
 * The tag under an answer of a quiz the user answered (board 4b): their answer and whether it was right, or a correct
 * answer they missed; null for other answers and polls.
 */
@StringRes
internal fun quizTag(
    option: PollOption,
    poll: Poll,
): Int? {
    if (poll.type !is PollType.Quiz || !poll.hasVoted) return null
    return when {
        option.isChosen && option.isCorrect -> R.string.content_quiz_your_correct
        option.isChosen -> R.string.content_quiz_your_wrong
        option.isCorrect -> R.string.content_quiz_missed
        else -> null
    }
}

/** "You got 1 of 2": a quiz with several correct answers the user answered; null for others. */
internal fun quizScore(
    resources: Resources,
    poll: Poll,
): String? {
    val correct = poll.correctOptionCount
    if (poll.type !is PollType.Quiz || !poll.hasVoted || correct < 2) return null
    val right = poll.options.count { it.isChosen && it.isCorrect }
    return resources.getString(R.string.content_quiz_score, right, correct)
}

/**
 * The answers a closed poll's results emphasise: those most voted for, if anyone voted; empty otherwise and while it
 * is open.
 */
internal fun Poll.winners(): Set<Int> {
    if (!isClosed) return emptySet()
    val most = options.maxOfOrNull { it.voterCount } ?: 0
    return if (most > 0) options.filter { it.voterCount == most }.mapTo(HashSet()) { it.index } else emptySet()
}

/** What an answer's link shows: its preview's title, else its site's host ("goodreads.com"), else the link. */
internal fun PollMedia.Link.label(): String =
    title.ifBlank {
        runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
    }

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1_440
