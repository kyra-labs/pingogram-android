package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** A participant's video, which [GroupCallActivity] draws with WebRTC's renderer and previews as a placeholder. */
typealias ParticipantVideo = @Composable (endpointId: String, modifier: Modifier) -> Unit

/** A tonal placeholder for participants' videos in previews and screenshot tests, which cannot draw with OpenGL. */
internal val PlaceholderParticipantVideo: ParticipantVideo = { _, modifier ->
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/**
 * The voice chat's videos above its participants (`docs/adr/0024-video-in-voice-chats.md`): the pinned one across the
 * width at 16:9, the others two to a row, square, the user's own camera last; a single video alone across the width.
 * Each tile has `large` corners, the participant's name on a `mediaOverlay` pill, a `primary` outline while they speak,
 * and toggles its pin when tapped.
 */
@Composable
internal fun VideoGrid(
    state: GroupCallUiState.InCall,
    videos: ParticipantVideo,
    self: CallVideo,
    onEvent: (GroupCallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    // The user's own camera is the null tile, last.
    val tiles: List<ParticipantVideoUi?> =
        if (state.video.camera != null) state.video.remote + null else state.video.remote
    val wide =
        if (tiles.size == 1 || state.video.remote
                .firstOrNull()
                ?.isPinned == true
        ) {
            1
        } else {
            0
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        tiles.take(wide).forEach { tile ->
            key(tile?.key) {
                Tile(tile, state, videos, self, onEvent, WIDE_ASPECT, Modifier.fillMaxWidth())
            }
        }
        tiles.drop(wide).chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                row.forEach { tile ->
                    // Keyed, so a tile keeps its renderer as pinning moves it.
                    key(tile?.key) { Tile(tile, state, videos, self, onEvent, 1f, Modifier.weight(1f)) }
                }
                // A lone tile in the last row keeps its column's width.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A participant's [video], or with null the user's own camera. */
@Composable
@Suppress("LongParameterList") // The tile, the screen's state, both kinds of video, its events, its shape and width.
private fun Tile(
    video: ParticipantVideoUi?,
    state: GroupCallUiState.InCall,
    videos: ParticipantVideo,
    self: CallVideo,
    onEvent: (GroupCallEvent) -> Unit,
    aspectRatio: Float,
    modifier: Modifier,
) {
    if (video != null) {
        VideoTile(video, videos, { onEvent(GroupCallEvent.TogglePin(video.key)) }, modifier.aspectRatio(aspectRatio))
    } else {
        SelfView(
            mirror = state.video.camera == CameraFacing.Front,
            canSwitch = state.video.canSwitchCamera,
            aspectRatio = aspectRatio,
            video = self,
            onSwitch = { onEvent(GroupCallEvent.SwitchCamera) },
            modifier = modifier,
        )
    }
}

/** One participant's video: "Video of Ada", pinned or not; a tap pins or unpins it. */
@Composable
private fun VideoTile(
    video: ParticipantVideoUi,
    draw: ParticipantVideo,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.pingogramColors
    val name = video.name.ifBlank { stringResource(R.string.call_someone) }
    val description = stringResource(R.string.call_remote_video, name)
    val pinned = stringResource(R.string.group_call_video_pinned)
    val action = stringResource(if (video.isPinned) R.string.group_call_unpin else R.string.group_call_pin)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(
                role = Role.Button,
                onClickLabel = action,
                onClick = onTogglePin,
            ).then(
                if (video.isSpeaking) {
                    Modifier.border(MaterialTheme.sizes.avatarRing, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            ).semantics {
                contentDescription = description
                if (video.isPinned) stateDescription = pinned
            },
    ) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            if (video.isPaused) {
                Text(
                    stringResource(R.string.call_video_paused),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onMediaOverlay,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .clip(MaterialTheme.shapes.small)
                            .background(colors.mediaOverlay)
                            .padding(horizontal = MaterialTheme.spacing.small),
                )
            } else {
                draw(video.endpointId, Modifier.fillMaxSize())
            }
            NamePill(name, video.isPinned, Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.small))
        }
    }
}

/** The participant's name on a `mediaOverlay` pill, after the push-pin symbol while pinned. */
@Composable
private fun NamePill(
    name: String,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(colors.mediaOverlay)
                .padding(horizontal = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPinned) {
            Icon(
                painterResource(UiR.drawable.ic_push_pin),
                contentDescription = null,
                tint = colors.onMediaOverlay,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            )
            Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge.userContent,
            color = colors.onMediaOverlay,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val COLUMNS = 2
private const val WIDE_ASPECT = 16f / 9f
