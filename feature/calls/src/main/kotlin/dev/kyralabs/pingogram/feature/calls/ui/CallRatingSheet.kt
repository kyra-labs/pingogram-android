package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.coroutines.launch

/**
 * "Rate this call" (`docs/DESIGN_SYSTEM.md` §6 "Call rating"), a [ModalBottomSheet] over the ended call that Telegram
 * asked to have rated: one to five stars, what went wrong for one to three, and a note that the rating goes to
 * Telegram to help improve calls. It never blocks: dragging it down, the scrim, back and "Not now" close it without
 * rating ([onDismiss]); "Send" closes it, then rates ([onRate], the problems only below four stars).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallRatingSheet(
    isVideo: Boolean,
    onRate: (stars: Int, problems: Set<CallProblem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnRate by rememberUpdatedState(onRate)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    ModalBottomSheet(onDismissRequest = { currentOnDismiss() }, sheetState = sheetState) {
        CallRatingContent(
            isVideo = isVideo,
            onSend = { stars, problems ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnRate(stars, problems) }
            },
            onNotNow = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (sheetState.currentValue == SheetValue.Hidden) currentOnDismiss()
                }
            },
        )
    }
}

/**
 * The sheet's content: the title, the stars as one radio group, the problems as filter chips growing in below four
 * stars (the video ones only for [isVideo]), the note, then "Not now" at the start and "Send" at the end, enabled once
 * a star is chosen. What is chosen survives a configuration change.
 */
@Composable
internal fun CallRatingContent(
    isVideo: Boolean,
    onSend: (stars: Int, problems: Set<CallProblem>) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    initialStars: Int = 0,
    initialProblems: Set<CallProblem> = emptySet(),
) {
    var stars by rememberSaveable { mutableIntStateOf(initialStars) }
    var problems by rememberSaveable(stateSaver = ProblemsSaver) { mutableStateOf(initialProblems) }
    val haptics = LocalHapticFeedback.current
    val spacing = MaterialTheme.spacing
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large)
            .padding(bottom = spacing.large),
    ) {
        Text(
            text = stringResource(R.string.call_rating_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(vertical = spacing.small).semantics { heading() },
        )
        Stars(
            stars = stars,
            onChoose = {
                stars = it
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = spacing.medium),
        )
        Problems(
            visible = stars in 1..MAX_STARS_WITH_PROBLEMS,
            isVideo = isVideo,
            problems = problems,
            onToggle = { problem ->
                val chosen = problem in problems
                problems = if (chosen) problems - problem else problems + problem
                haptics.performHapticFeedback(
                    if (chosen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
            },
        )
        Text(
            text = stringResource(R.string.call_rating_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Actions(
            canSend = stars > 0,
            onSend = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSend(stars, if (stars <= MAX_STARS_WITH_PROBLEMS) problems.filterFor(isVideo) else emptySet())
            },
            onNotNow = onNotNow,
        )
    }
}

/** "What went wrong?" and Telegram's problems as filter chips, the video ones only for [isVideo], growing in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Problems(
    visible: Boolean,
    isVideo: Boolean,
    problems: Set<CallProblem>,
    onToggle: (CallProblem) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val spacing = MaterialTheme.spacing
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        label = "callProblems",
    ) {
        Column(Modifier.padding(bottom = spacing.medium)) {
            Text(
                text = stringResource(R.string.call_rating_problems),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = spacing.small).semantics { heading() },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                for (problem in CallProblem.entries) {
                    if (problem.isVideo && !isVideo) continue
                    FilterChip(
                        selected = problem in problems,
                        onClick = { onToggle(problem) },
                        label = { Text(stringResource(problem.label)) },
                        shapes = FilterChipDefaults.shapes(),
                    )
                }
            }
        }
    }
}

/** "Not now" at the start and "Send" at the end, enabled once a star is chosen ([canSend]). */
@Composable
private fun Actions(
    canSend: Boolean,
    onSend: () -> Unit,
    onNotNow: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onNotNow, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.call_rating_not_now))
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSend, enabled = canSend, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.call_rating_send), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/**
 * Five stars as a radio group (Material has no rating component): each a 48 dp target with the icon button's ripple,
 * the chosen one and those below it filled in `primary`, the others outlined in `onSurfaceVariant`; the chosen star
 * pops on the fast spatial spring, in its graphics layer. Screen readers hear "3 stars", selected or not.
 */
@Composable
private fun Stars(
    stars: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        for (star in 1..MAX_STARS) {
            val filled = star <= stars
            val description = pluralStringResource(R.plurals.call_rating_stars, star, star)
            val scale by animateFloatAsState(
                targetValue = if (star == stars) CHOSEN_STAR_SCALE else 1f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "starScale",
            )
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .size(IconButtonDefaults.mediumContainerSize())
                        .clip(CircleShape)
                        .selectable(
                            selected = star == stars,
                            role = Role.RadioButton,
                            indication = ripple(),
                            interactionSource = null,
                            onClick = { onChoose(star) },
                        ).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (filled) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = null,
                    tint = if (filled) colors.primary else colors.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(IconButtonDefaults.mediumIconSize)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                )
            }
        }
    }
}

private fun Set<CallProblem>.filterFor(isVideo: Boolean) = filterTo(mutableSetOf()) { isVideo || !it.isVideo }

private val CallProblem.label: Int
    get() =
        when (this) {
            CallProblem.Echo -> R.string.call_problem_echo
            CallProblem.Noise -> R.string.call_problem_noise
            CallProblem.Interruptions -> R.string.call_problem_interruptions
            CallProblem.DistortedSpeech -> R.string.call_problem_distorted_speech
            CallProblem.SilentLocal -> R.string.call_problem_silent_local
            CallProblem.SilentRemote -> R.string.call_problem_silent_remote
            CallProblem.Dropped -> R.string.call_problem_dropped
            CallProblem.DistortedVideo -> R.string.call_problem_distorted_video
            CallProblem.PixelatedVideo -> R.string.call_problem_pixelated_video
        }

private val ProblemsSaver =
    listSaver<Set<CallProblem>, String>(
        save = { problems -> problems.map { it.name } },
        restore = { names -> names.mapTo(mutableSetOf()) { CallProblem.valueOf(it) } },
    )

private const val MAX_STARS = 5

/** Telegram's apps ask what went wrong for one to three stars. */
private const val MAX_STARS_WITH_PROBLEMS = 3

private const val CHOSEN_STAR_SCALE = 1.2f

@PingogramPreviews
@Composable
private fun CallRatingPreview() {
    PingogramPreview { CallRatingContent(isVideo = false, onSend = { _, _ -> }, onNotNow = {}) }
}

@PingogramPreviews
@Composable
private fun CallRatingProblemsPreview() {
    PingogramPreview {
        CallRatingContent(
            isVideo = true,
            onSend = { _, _ -> },
            onNotNow = {},
            initialStars = 2,
            initialProblems = setOf(CallProblem.Echo),
        )
    }
}
