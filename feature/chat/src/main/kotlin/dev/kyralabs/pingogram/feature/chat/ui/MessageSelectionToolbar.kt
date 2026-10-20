package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The selection toolbar of a conversation (`REQUIREMENTS.md` FR-C6; `docs/DESIGN_SYSTEM.md` §6 "Selection mode"), as
 * the chat list's: an expressive [HorizontalFloatingToolbar] in vibrant colors with one [IconButton] per action on the
 * selected messages, all in the toolbar's content color (`error` would lose contrast on its container), named by its
 * label for screen readers and in a [PlainTooltip] on a long press or hover. Icons do not grow with the font scale, so
 * the four fit the narrowest phones.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageSelectionToolbar(
    actions: ImmutableList<MessageAction>,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        actions.forEach { action ->
            val label =
                stringResource(
                    if (action ==
                        MessageAction.Copy
                    ) {
                        R.string.selection_action_copy
                    } else {
                        action.label
                    },
                )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(label) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = { onAction(action) }) {
                    Icon(painter = painterResource(action.icon), contentDescription = label)
                }
            }
        }
    }
}

/**
 * The bottom of the conversation: the [composer], or in selection mode ([actions] not null) the selection toolbar
 * ([MessageSelectionToolbar]) centred in its place, [FloatingToolbarDefaults.ScreenOffset] above the system navigation
 * bar. The toolbar slides up and fades in as the composer fades out, and back again, on the motion scheme's springs,
 * the bar's height following on its default spatial spring; it shrinks with a predictive back gesture ([backProgress],
 * 0 to 1) that leaves selection mode. The toolbar changes its actions in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectionBottomBar(
    actions: ImmutableList<MessageAction>?,
    backProgress: () -> Float,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
    composer: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = actions,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val toolbarIn =
                slideInVertically(motionScheme.defaultSpatialSpec()) { it } + fadeIn(motionScheme.defaultEffectsSpec())
            val toolbarOut =
                slideOutVertically(motionScheme.fastSpatialSpec()) { it } + fadeOut(motionScheme.fastEffectsSpec())
            val transform =
                if (targetState != null) {
                    toolbarIn togetherWith fadeOut(motionScheme.fastEffectsSpec())
                } else {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith toolbarOut
                }
            transform using SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.BottomCenter,
        label = "selectionBottomBar",
        contentKey = { it != null },
    ) { shown ->
        if (shown != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = FloatingToolbarDefaults.ScreenOffset),
                contentAlignment = Alignment.Center,
            ) {
                MessageSelectionToolbar(
                    actions = shown,
                    onAction = onAction,
                    modifier = Modifier.predictiveBackScale(backProgress),
                )
            }
        } else {
            composer()
        }
    }
}

/**
 * In selection mode the composer gives way to the selection toolbar: its text field gives up the focus, which closes
 * the keyboard. The text stays (the view model keeps it), and the composer comes back with it.
 */
@Composable
internal fun LeaveComposerWhileSelecting(selecting: Boolean) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(selecting) { if (selecting) focusManager.clearFocus() }
}

/** Whether this bubble shows as selected among [selectedIds]: an album's when any of its items is. */
internal fun ChatItem.Bubble.selectionIn(selectedIds: ImmutableSet<Long>): RowSelection {
    val selected = selectedIds.contains(id) || album?.messageIds?.any(selectedIds::contains) == true
    return if (selected) SelectedRow else UnselectedRow
}

private val SelectedRow = RowSelection(isSelected = true)
private val UnselectedRow = RowSelection(isSelected = false)

/** A bubble in selection mode: [isSelected] or not. */
@Immutable
internal data class RowSelection(
    val isSelected: Boolean,
)

/**
 * The check at the start of a row in selection mode: a ring in `outline`, or, once selected, a filled check in
 * `primary` that springs in on the motion scheme's fast spatial spring (read in the draw phase). Decorative: the row
 * tells screen readers whether it is selected.
 */
@Composable
internal fun SelectionCheck(isSelected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else SELECTION_CHECK_RESTING_SCALE,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "selectionCheck",
    )
    Icon(
        painter =
            painterResource(
                if (isSelected) UiR.drawable.ic_check_circle_filled else UiR.drawable.ic_radio_button_unchecked,
            ),
        contentDescription = null,
        tint = if (isSelected) colors.primary else colors.outline,
        modifier =
            Modifier
                .padding(end = MaterialTheme.spacing.small)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    )
}

/** How small the check rests before it is selected, which it grows from. */
private const val SELECTION_CHECK_RESTING_SCALE = 0.85f

/**
 * A bubble's toggle in selection mode: a toggle haptic (off when it was [isSelected]), then
 * [SelectionEvent.Toggle] for message [messageId].
 */
@Composable
internal fun rememberSelectionToggle(
    messageId: Long,
    isSelected: Boolean,
    onEvent: (ChatEvent) -> Unit,
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(messageId, isSelected, onEvent, haptics) {
        {
            haptics.performHapticFeedback(if (isSelected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onEvent(SelectionEvent.Toggle(messageId))
        }
    }
}

/**
 * What screen readers hear of a bubble in selection mode: whether it [isSelected], and "Select" or "Deselect" as its
 * click and long click, which [toggle] it. Built in the semantics block (`docs/PERFORMANCE.md` §4).
 */
internal fun SemanticsPropertyReceiver.selectionSemantics(
    resources: Resources,
    isSelected: Boolean,
    toggle: () -> Unit,
) {
    selected = isSelected
    val label = resources.getString(if (isSelected) R.string.selection_deselect else R.string.selection_select)
    onClick(label = label) {
        toggle()
        true
    }
    onLongClick(label = label) {
        toggle()
        true
    }
}

@PingogramPreviews
@Composable
private fun MessageSelectionToolbarPreview() {
    PingogramPreview {
        MessageSelectionToolbar(
            actions =
                persistentListOf(
                    MessageAction.Copy,
                    MessageAction.Forward,
                    MessageAction.Report,
                    MessageAction.Delete,
                ),
            onAction = {},
        )
    }
}
