package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A reminder row that swipes both ways (the owner's board 29e): toward the end marks it done ([onDone], "Mark done" or
 * "Mark not done" after the check on `secondaryContainer`), toward the start deletes it ([onDelete], "Delete" after the
 * delete symbol on `errorContainer`), each revealed in the row's [shape], with a `TextHandleMove` haptic where letting
 * go would act. The row springs back after each; screen readers reach both through the row's custom actions, so the
 * revealed labels are left out of the semantics. Mirrored right to left, as the swipe's directions are.
 */
@Composable
internal fun ReminderSwipe(
    shape: Shape,
    labels: ReminderSwipeLabels,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    // Not rememberSwipeToDismissBoxState, which saves the state: the row springs back by itself after each swipe.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDelete by rememberUpdatedState(onDelete)
    ThresholdHaptic(state)
    val colors = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Reveal(
                        state,
                        shape,
                        RevealSide(colors.secondaryContainer, colors.onSecondaryContainer, labels.done, DONE_ICON),
                    )
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    Reveal(
                        state,
                        shape,
                        RevealSide(colors.errorContainer, colors.onErrorContainer, labels.delete, DELETE_ICON),
                    )
                }

                SwipeToDismissBoxValue.Settled -> {}
            }
        },
        onDismiss = { direction ->
            scope.launch { state.reset() }
            if (direction == SwipeToDismissBoxValue.StartToEnd) currentOnDone() else currentOnDelete()
        },
    ) { content() }
}

/** A `TextHandleMove` haptic each time the user's own swipe crosses the point where letting go acts, either way. */
@Composable
private fun ThresholdHaptic(state: SwipeToDismissBoxState) {
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .collect {
                // Dragged across by the user: springing back after a swipe starts from the dismissed value.
                if (state.currentValue == SwipeToDismissBoxValue.Settled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }
}

/**
 * What a swipe reveals: [label] beside [icon] at the side the row leaves, on [container]. The symbol pops to its full
 * size where letting go would act, and back below it, on the fast spatial spring, read in its graphics layer.
 */
@Composable
private fun Reveal(
    state: SwipeToDismissBoxState,
    shape: Shape,
    side: RevealSide,
) {
    val direction = state.dismissDirection
    val past by remember(state) { derivedStateOf { state.targetValue != SwipeToDismissBoxValue.Settled } }
    val iconScale =
        animateFloatAsState(
            targetValue = if (past) 1f else ICON_RESTING_SCALE,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "ReminderSwipeIcon",
        )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), side.container) },
        contentAlignment =
            if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(side.icon),
                contentDescription = null,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
                tint = side.content,
            )
            Text(side.label, color = side.content, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** What the row says it does when swiped toward the end ([done]: "Mark done" or "Mark not done") and the start. */
@Immutable
internal data class ReminderSwipeLabels(
    val done: String,
    val delete: String,
)

/** What one side of the swipe reveals: [label] beside [icon] in [content], on [container]. */
@Immutable
private data class RevealSide(
    val container: Color,
    val content: Color,
    val label: String,
    @param:DrawableRes val icon: Int,
)

/** The size of the symbol before the point where letting go acts, against its full size past it. */
private const val ICON_RESTING_SCALE = 0.6f

private val DONE_ICON = UiR.drawable.ic_check_circle
private val DELETE_ICON = UiR.drawable.ic_delete
