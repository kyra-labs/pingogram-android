package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/**
 * The selection toolbar (`REQUIREMENTS.md` FR-L8, `docs/DESIGN_SYSTEM.md` §6): an expressive
 * [HorizontalFloatingToolbar] in vibrant colors with one [IconButton] per action on the selected chats, named by its
 * label for screen readers and in a [PlainTooltip] on a long press or hover. Five buttons fit the narrowest phones at
 * any font scale, as icons do not grow with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectionToolbar(
    actions: ImmutableList<ChatRowAction>,
    onAction: (ChatRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        actions.forEach { action ->
            val label = stringResource(action.label)
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
 * Where the selection toolbar floats: at the bottom center, where the floating navigation bar is in compact windows
 * (which hides meanwhile), at the bottom of the list on larger ones. It slides in and out as the navigation bar does,
 * on the motion scheme's springs, and shrinks with a predictive back gesture ([backProgress], 0 to 1) that leaves
 * selection mode.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectionToolbarHost(
    selection: ChatSelection?,
    backProgress: () -> Float,
    onAction: (ChatRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        // The toolbar changes its actions in place; only entering and leaving selection mode animates.
        targetState = selection?.actions,
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset)
                .predictiveBackScale(backProgress),
        transitionSpec = {
            slideInVertically(
                motionScheme.defaultSpatialSpec(),
            ) { it } + fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                slideOutVertically(
                    motionScheme.fastSpatialSpec(),
                ) { it } + fadeOut(motionScheme.fastEffectsSpec()) using
                null
        },
        contentAlignment = Alignment.BottomCenter,
        label = "selectionToolbar",
        contentKey = { it != null },
    ) { actions ->
        if (actions != null) SelectionToolbar(actions = actions, onAction = onAction)
    }
}

/**
 * Leaves selection mode with back while [enabled] (`docs/ARCHITECTURE.md`, Navigation: `PredictiveBackHandler` for
 * selection mode): the gesture's progress, for the toolbar to follow ([SelectionToolbarHost]), and [onBack] once it
 * completes. A cancelled gesture springs back on the motion scheme's fast spatial spring.
 */
@Composable
internal fun rememberSelectionBack(
    enabled: Boolean,
    onBack: () -> Unit,
): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            currentOnBack()
        } finally {
            // Also after a cancelled gesture, when this coroutine can no longer animate.
            scope.launch { progress.animateTo(0f, settle) }
        }
    }
    return progress
}

@PingogramPreviews
@Composable
private fun SelectionToolbarPreview() {
    PingogramPreview {
        SelectionToolbar(
            actions =
                persistentListOf(
                    ChatRowAction.Pin,
                    ChatRowAction.Mute,
                    ChatRowAction.MarkAsRead,
                    ChatRowAction.Archive,
                    ChatRowAction.Delete,
                ),
            onAction = {},
        )
    }
}
