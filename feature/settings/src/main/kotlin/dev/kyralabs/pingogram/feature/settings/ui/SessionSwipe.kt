package dev.kyralabs.pingogram.feature.settings.ui

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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * A session row that a swipe toward the start ends (`docs/DESIGN_SYSTEM.md` §13.10, Active sessions): the row follows
 * the finger over "End session" on `errorContainer` in the row's [shape], with a `TextHandleMove` haptic where letting
 * go would end it. Letting go there springs the row back and calls [onSwiped], which asks first as a tap does. Screen
 * readers reach the same through the row's click, so the revealed label is left out of the semantics.
 */
@Composable
internal fun SwipeToEndSession(
    enabled: Boolean,
    shape: Shape,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit,
) {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    // Not rememberSwipeToDismissBoxState, which saves the state: the row springs back by itself after each swipe.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val scope = rememberCoroutineScope()
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    ThresholdHaptic(state)
    SwipeToDismissBox(
        state = state,
        backgroundContent = { EndSessionReveal(state, shape) },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        onDismiss = {
            scope.launch { state.reset() }
            currentOnSwiped()
        },
    ) { content() }
}

/** A `TextHandleMove` haptic each time the swipe crosses the point where letting go ends the session, either way. */
@Composable
private fun ThresholdHaptic(state: SwipeToDismissBoxState) {
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

/** What the swipe reveals: "End session" after the logout symbol at the row's end, on `errorContainer`. */
@Composable
private fun EndSessionReveal(
    state: SwipeToDismissBoxState,
    shape: Shape,
) {
    val swiping by remember(state) { derivedStateOf { state.dismissDirection == SwipeToDismissBoxValue.EndToStart } }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .drawBehind {
                    if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), colors.errorContainer)
                    }
                },
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (swiping) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_session_swipe_end),
                    color = colors.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = colors.onErrorContainer)
            }
        }
    }
}
