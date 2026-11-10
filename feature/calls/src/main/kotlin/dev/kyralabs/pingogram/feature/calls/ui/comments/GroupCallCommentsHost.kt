package dev.kyralabs.pingogram.feature.calls.ui.comments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the voice chat's screen needs of its comments: their state and screen readers' announcements (read only by the
 * pane, [GroupCallCommentsPane]) and where the user's actions go. [visibility] is derived once, so the screen around
 * the pane recomposes only when the pane comes or goes, never for a comment.
 */
@Stable
class GroupCallCommentsHost(
    val state: State<GroupCallCommentsUiState>,
    val announcement: State<CommentAnnouncement?>,
    val onEvent: (CommentEvent) -> Unit,
) {
    val visibility: State<CommentsVisibility> =
        derivedStateOf {
            when (val current = state.value) {
                GroupCallCommentsUiState.Unavailable -> {
                    CommentsVisibility.Unavailable
                }

                is GroupCallCommentsUiState.Available -> {
                    if (current.isShown) CommentsVisibility.Shown else CommentsVisibility.Hidden
                }
            }
        }

    companion object {
        /** No comments: previews and goldens of the screen without them. */
        val None =
            GroupCallCommentsHost(
                mutableStateOf(GroupCallCommentsUiState.Unavailable),
                mutableStateOf(null),
                onEvent = {},
            )
    }
}

/** Whether the call offers comments, and whether the user shows them. */
enum class CommentsVisibility { Unavailable, Hidden, Shown }

/**
 * The header's "Comments" toggle (`docs/DESIGN_SYSTEM.md` §6, "Voice chat comments"): a `ToggleButton` with the
 * comment symbol, `primary` while the pane shows. A switch for screen readers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentsToggle(
    shown: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    ToggleButton(
        checked = shown,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle()
        },
        modifier = modifier.semantics { role = Role.Switch },
    ) {
        Icon(
            painterResource(if (shown) UiR.drawable.ic_mode_comment_filled else UiR.drawable.ic_mode_comment),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(ToggleButtonDefaults.MinHeight)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ToggleButtonDefaults.MinHeight)))
        Text(stringResource(R.string.group_call_comments), maxLines = 1)
    }
}
