package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A poll or a quiz (`REQUIREMENTS.md` FR-C4; `docs/DESIGN_SYSTEM.md` §6 "Polls"), padded in its bubble and
 * `mediaMaxWidth` wide: the question, what kind of poll it is ([pollLabel]: anonymous or public, poll or quiz, or
 * "Final results" once closed), the answers, the number of voters, and what the user can do.
 *
 * - **Voting** ([Poll.canVote]): each answer a row of at least 48 dp beside an empty radio button; a tap votes for it
 *   ([PollAction.Vote]) with a `ToggleOn` haptic. Where several answers are allowed, check boxes that taps check and
 *   uncheck ([choices], `ToggleOn` / `ToggleOff`), and a "Vote" button that sends them ([PollAction.Submit]),
 *   enabled once one is checked. While the vote goes to Telegram (`PollOption.isBeingChosen`) the chosen answers
 *   show a [LoadingIndicator] in place of their button, and nothing takes a tap.
 * - **Results** (the user voted, the poll is closed, or the user created it): each answer with its percentage
 *   (tabular figures) over a bar of its share ([pollBar], animated to it in the draw phase on the motion scheme's
 *   default spatial spring). The user's answer is marked ([pollMark]) with an icon; in a quiz the correct answers are
 *   marked and their bars are green, a wrong answer is marked and red: icons and colors, never color alone. A public
 *   poll whose voters TDLib lists offers "View votes" ([PollAction.ShowVoters]).
 * - **Not allowed to vote** ([Poll.voteRestriction]): the answers next to disabled buttons, and why
 *   ([pollRestriction]): never a tap that does nothing.
 * - **Quiz explanation** once answered or closed: on the bubble's block color with `medium` corners, its formatting
 *   kept (links open with the app's handler).
 *
 * The bubble reads the poll to screen readers as one with its message (`pollDescription`) and offers voting as its
 * custom actions (`pollActions`), as `docs/DESIGN_SYSTEM.md` §9 reads every bubble; out of a bubble (previews), each
 * answer is one item.
 */
@Composable
internal fun MessagePoll(
    content: MessageContent.Poll,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    choices: ImmutableSet<Int> = persistentSetOf(),
    onAction: (PollAction) -> Unit = {},
) {
    val poll = content.poll
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            Text(
                text = poll.question.text,
                color = colors.content,
                style = MaterialTheme.typography.titleMedium.userContent,
            )
            Text(
                text = stringResource(pollLabel(poll).text),
                color = colors.content,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (poll.showsResults) {
            poll.options.forEach { option -> PollResult(option, pollMark(option, poll), colors) }
        } else {
            PollChoices(poll, choices, colors, onAction)
        }
        pollRestriction(poll)?.let { text ->
            Text(
                text = stringResource(text),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        PollExplanation(poll, colors)
        PollFooter(poll, choices, colors, onAction)
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
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
            PollMarkIcon(mark, barColor)
            Text(
                text = stringResource(R.string.content_poll_percentage, option.votePercentage),
                color = colors.content,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(MaterialTheme.sizes.pollBar)
                .pollBar(
                    share = pollShare(option),
                    color = barColor,
                    trackColor = colors.track,
                    gap = ProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
                    spec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
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

/**
 * The answers before the results show: rows that vote or check where the user can vote, plain ones (disabled buttons)
 * where they cannot; one being sent shows its progress.
 */
@Composable
private fun PollChoices(
    poll: Poll,
    choices: ImmutableSet<Int>,
    colors: BubbleColors,
    onAction: (PollAction) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val voteLabel = stringResource(R.string.poll_vote)
    Column {
        poll.options.forEach { option ->
            val checked = option.index in choices
            val interaction =
                when {
                    !poll.canVote -> {
                        Modifier
                    }

                    poll.allowsMultipleAnswers -> {
                        Modifier.toggleable(value = checked, role = Role.Checkbox) { checking ->
                            haptics.performHapticFeedback(
                                if (checking) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onAction(PollAction.Toggle(option.index))
                        }
                    }

                    else -> {
                        Modifier.clickable(onClickLabel = voteLabel, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onAction(PollAction.Vote(option.index))
                        }
                    }
                }
            PollChoice(
                option = option,
                multipleAnswers = poll.allowsMultipleAnswers,
                checked = checked || option.isBeingChosen,
                enabled = poll.canVote || poll.isVoting,
                colors = colors,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(interaction)
                        .minimumInteractiveComponentSize(),
            )
        }
    }
}

/** An answer to vote for: its radio button or check box ([checked] or not), or its progress, and its text. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun PollChoice(
    option: PollOption,
    multipleAnswers: Boolean,
    checked: Boolean,
    enabled: Boolean,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            option.isBeingChosen -> {
                Box(Modifier.padding(MaterialTheme.sizes.textBlockPadding)) {
                    LoadingIndicator(
                        modifier = Modifier.size(MaterialTheme.sizes.inlineProgressIndicator),
                        color = colors.accent,
                    )
                }
            }

            multipleAnswers -> {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = colors.accent,
                            checkmarkColor = colors.onAccent,
                            uncheckedColor = colors.content,
                        ),
                )
            }

            else -> {
                RadioButton(
                    selected = checked,
                    onClick = null,
                    enabled = enabled,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = colors.accent,
                            unselectedColor = colors.content,
                        ),
                )
            }
        }
        Text(text = option.text.text, color = colors.content, style = MaterialTheme.typography.bodyMedium.userContent)
    }
}

/** A quiz's explanation once the user answered it or it closed: on the bubble's block color, with its formatting. */
@Composable
private fun PollExplanation(
    poll: Poll,
    colors: BubbleColors,
) {
    val explanation = pollExplanation(poll) ?: return
    val styles = rememberFormattedTextStyles(codeBackground = colors.container, spoilerColor = colors.spoiler)
    val text = remember(explanation, styles) { explanation.toAnnotatedString(styles, spoilersRevealed = true) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.block, MaterialTheme.shapes.medium)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            text = stringResource(R.string.poll_explanation),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = text, color = colors.blockContent, style = MaterialTheme.typography.bodyMedium.userContent)
    }
}

/** The number of voters, then "Vote" while the user checks answers, or "View votes" for a public poll's results. */
@Composable
private fun PollFooter(
    poll: Poll,
    choices: ImmutableSet<Int>,
    colors: BubbleColors,
    onAction: (PollAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = voterCountText(poll),
            modifier = Modifier.weight(1f),
            color = colors.content,
            style = MaterialTheme.typography.labelMedium,
        )
        when {
            poll.canVote && poll.allowsMultipleAnswers -> {
                Button(
                    onClick = { onAction(PollAction.Submit) },
                    enabled = choices.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                ) {
                    Text(stringResource(R.string.poll_vote), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }

            poll.showsVoters -> {
                TextButton(
                    onClick = { onAction(PollAction.ShowVoters) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                ) {
                    Text(stringResource(R.string.poll_view_votes))
                }
            }
        }
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
