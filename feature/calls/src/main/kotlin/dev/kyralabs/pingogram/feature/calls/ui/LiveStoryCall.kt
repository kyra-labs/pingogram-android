package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.ui.comments.CommentsVisibility
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsHost
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsPane
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A live story being watched (board 12, `docs/DESIGN_SYSTEM.md` §6 "Live story"): the poster's stream full width on
 * `scrim` (their camera's video, or the stream of an RTMP live story), under a header on a `mediaOverlay` gradient:
 * the poster's avatar, their name over where the stream is ("1,204 watching"), a "LIVE" pill on `error` and close,
 * which leaves. Below it the voice chat's comments pane ([GroupCallCommentsPane]), where Telegram offers comments,
 * taking the lower share of the height; without comments the stream takes it all.
 */
@Composable
internal fun ColumnScope.LiveStoryCall(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
    stream: @Composable (Modifier) -> Unit,
    comments: GroupCallCommentsHost,
    snackbarHost: (@Composable (Modifier) -> Unit)?,
) {
    val hasComments = comments.visibility.value != CommentsVisibility.Unavailable && state.hasControls
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        stream(Modifier.fillMaxSize())
        LiveStoryHeader(state, onEvent, Modifier.align(Alignment.TopCenter))
        snackbarHost?.invoke(Modifier.align(Alignment.BottomCenter))
    }
    if (hasComments) {
        GroupCallCommentsPane(
            state = comments.state,
            announcement = comments.announcement,
            onEvent = comments.onEvent,
            modifier = Modifier.weight(LIVE_COMMENTS_WEIGHT).fillMaxWidth(),
        )
    }
}

/** The poster, where the stream is, "LIVE" and close, on a gradient from `mediaOverlay` so they read over the video. */
@Composable
private fun LiveStoryHeader(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.pingogramColors
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.mediaOverlay, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = spacing.medium, end = spacing.extraSmall, top = spacing.small, bottom = spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Avatar(
            photo = state.chat.photo,
            name = state.chat.title,
            id = state.chat.chatId,
            size = MaterialTheme.sizes.avatarSmall,
        )
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(
                text = state.chat.title,
                style = MaterialTheme.typography.titleSmallEmphasized.userContent,
                color = colors.onMediaOverlay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMediaOverlay,
                maxLines = 2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            text = stringResource(R.string.live_story_badge),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        )
        IconButton(
            onClick = { onEvent(GroupCallEvent.Leave) },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.live_story_close),
                tint = colors.onMediaOverlay,
            )
        }
    }
}

/** The comments' share of the height under the stream. */
private const val LIVE_COMMENTS_WEIGHT = 0.8f
