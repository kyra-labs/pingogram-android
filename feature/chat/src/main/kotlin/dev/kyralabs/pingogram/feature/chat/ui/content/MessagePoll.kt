package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A poll or a quiz, view only (`REQUIREMENTS.md` FR-C4; voting comes with M3), padded in its bubble and
 * `mediaMaxWidth` wide: the question, what kind of poll it is ([pollLabel]: anonymous or public, poll or quiz, or
 * "Final results" once closed), the answers and the number of voters.
 *
 * - **Results** (the user voted, the poll is closed, or the user created it): each answer with its percentage and a
 *   [LinearProgressIndicator] bar of its share ([pollShare]) on the bubble's track. The user's answer is marked
 *   ([pollMark]); in a quiz the correct answers are marked and their bars are green, a wrong answer is marked and red.
 * - **Before voting**: the answers next to empty radio buttons, or check boxes where several answers are allowed.
 *
 * Screen readers hear each answer as one item: its text, its mark and its percentage.
 *
 * The poll's description and a quiz's explanation are formatted text, for the bubble's text renderer.
 */
@Composable
internal fun MessagePoll(
    content: MessageContent.Poll,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val poll = content.poll
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            Text(text = poll.question.text, color = colors.content, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(pollLabel(poll).text),
                color = colors.content,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        poll.options.forEach { option ->
            if (poll.canSeeResults) {
                PollResult(option, pollMark(option, poll), colors)
            } else {
                PollChoice(option, poll.allowsMultipleAnswers, colors)
            }
        }
        Text(text = voterCountText(poll), color = colors.content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PollResult(
    option: PollOption,
    mark: PollMark,
    colors: BubbleColors,
) {
    val barColor =
        when (mark) {
            PollMark.Correct -> colors.correct
            PollMark.Wrong -> colors.wrong
            PollMark.Chosen, PollMark.None -> colors.accent
        }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.text.text,
                modifier = Modifier.weight(1f),
                color = colors.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            PollMarkIcon(mark, barColor)
            Text(
                text = stringResource(R.string.content_poll_percentage, option.votePercentage),
                color = colors.content,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        LinearProgressIndicator(
            progress = { pollShare(option) },
            modifier = Modifier.fillMaxWidth(),
            color = barColor,
            trackColor = colors.track,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun PollMarkIcon(
    mark: PollMark,
    tint: Color,
) {
    val (icon, description) =
        when (mark) {
            PollMark.None -> return
            PollMark.Chosen -> UiR.drawable.ic_check_circle_filled to R.string.content_poll_chosen
            PollMark.Correct -> UiR.drawable.ic_check_circle_filled to R.string.content_quiz_correct
            PollMark.Wrong -> UiR.drawable.ic_cancel_filled to R.string.content_quiz_wrong
        }
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
        tint = tint,
    )
}

/** An answer before voting: an empty radio button or check box (not clickable until voting comes) and its text. */
@Composable
private fun PollChoice(
    option: PollOption,
    multipleAnswers: Boolean,
    colors: BubbleColors,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multipleAnswers) {
            Checkbox(
                checked = false,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(uncheckedColor = colors.content),
            )
        } else {
            RadioButton(
                selected = false,
                onClick = null,
                colors = RadioButtonDefaults.colors(unselectedColor = colors.content),
            )
        }
        Text(text = option.text.text, color = colors.content, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "12 votes" or "12 answers" (quiz), with the digits of the user's language; "No votes yet" for none. */
@Composable
private fun voterCountText(poll: Poll): String {
    val count = poll.totalVoterCount
    val isQuiz = poll.type is PollType.Quiz
    if (count <= 0) {
        return stringResource(if (isQuiz) R.string.content_quiz_no_answers else R.string.content_poll_no_votes)
    }
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(count)
    return pluralStringResource(
        if (isQuiz) R.plurals.content_quiz_answers else R.plurals.content_poll_votes,
        count,
        number,
    )
}
