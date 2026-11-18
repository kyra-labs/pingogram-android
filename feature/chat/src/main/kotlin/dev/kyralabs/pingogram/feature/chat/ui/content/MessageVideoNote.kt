package dev.kyralabs.pingogram.feature.chat.ui.content

import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.util.lerp
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.transcription.MessageTranscription
import dev.kyralabs.pingogram.feature.chat.ui.transcription.TranscribeButton

/**
 * Where the round video message playing shows its picture: the conversation's player (`ChatViewModel`); null where
 * nothing plays round videos (previews), which then show their still.
 */
internal val LocalVideoNoteSurface = staticCompositionLocalOf<((Surface?) -> Unit)?> { null }

/**
 * A round video message (`REQUIREMENTS.md` FR-C4, `docs/DESIGN_SYSTEM.md` §6 "Round video messages"), bare: its still
 * in a `videoNote` circle with a [PlayBadge] and its length, with a dot while nobody has watched it yet.
 * Self-destructing ones show only the blurred minithumbnail.
 *
 * A tap ([onPlayPause]) plays it with sound in its circle, through the voice messages' player ([playback]): the circle
 * grows to the bubble's full width on the expressive spatial spring, in the layout phase, its picture on the player's
 * surface ([LocalVideoNoteSurface]), with a progress ring around it read in the draw phase; another tap pauses, and at
 * its end it shrinks back. Screen readers hear "Round video message, 0:12", and "Playing" or "Paused" when that
 * changes, never the progress.
 *
 * Where the device can transcribe ([transcription]), the Transcribe button sits on the circle's top end; the
 * transcript shows in a bubble under it (`VideoNoteTranscript`).
 */
@Composable
internal fun MessageVideoNote(
    content: MessageContent.VideoNote,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    transcription: MessageTranscription? = null,
    playback: Playback = Playback.Stopped,
) {
    val videoNote = content.videoNote
    val side = MaterialTheme.sizes.videoNote
    val duration = mediaDurationText(videoNote.duration)
    val description = videoNoteDescription(duration, content.isViewed)
    // Heard when it changes: when it starts, and when it pauses or ends.
    val state =
        when {
            playback.isPlaying -> stringResource(R.string.content_playing)
            playback.inPlayer -> stringResource(R.string.content_paused)
            else -> null
        }
    val action = stringResource(if (playback.isPlaying) R.string.content_pause else R.string.content_play)
    val growth =
        animateFloatAsState(
            targetValue = if (playback.inPlayer) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "videoNoteGrowth",
        )
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .growingCircle(side, MaterialTheme.sizes.bubbleMaxWidth, growth)
                    .progressRing(playback, colors.primary, colors.outlineVariant, MaterialTheme.sizes.avatarRing)
                    .clip(CircleShape)
                    .clickable(onClickLabel = action, onClick = onPlayPause)
                    .semantics {
                        contentDescription = description
                        state?.let { stateDescription = it }
                        liveRegion = LiveRegionMode.Polite
                    },
        ) {
            VideoThumbnail(
                still = videoNote.still(),
                displaySize = DpSize(side, side),
                hidden = content.isSecret,
                modifier = Modifier.matchParentSize(),
            )
            if (playback.inPlayer) VideoNoteSurface(Modifier.matchParentSize())
            VideoNoteOverlay(playback, duration, isViewed = content.isViewed)
        }
        // The Transcribe button on the circle's top end, clear of the time at its bottom end (board 28c).
        if (transcription?.showsButton == true) {
            TranscribeButton(
                transcription = transcription,
                colors = MaterialTheme.pingogramColors.incomingBubble,
                isVideo = true,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** "Round video message, 0:12", and "Not played yet" while nobody watched it. */
@Composable
private fun videoNoteDescription(
    duration: String,
    isViewed: Boolean,
): String {
    val kind = stringResource(R.string.content_round_video_message)
    val description = stringResource(R.string.content_kind_and_details, kind, duration)
    val notPlayed = stringResource(R.string.content_not_played)
    return if (isViewed) description else stringResource(R.string.content_kind_and_details, description, notPlayed)
}

/** Over the circle: the play badge unless it plays, and its length with the unwatched dot at rest. */
@Composable
private fun BoxScope.VideoNoteOverlay(
    playback: Playback,
    duration: String,
    isViewed: Boolean,
) {
    Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
        if (!playback.isPlaying) PlayBadge(Modifier.align(Alignment.Center))
        if (!playback.inPlayer) {
            MediaLabel(
                text = duration,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MaterialTheme.spacing.large),
                dot = !isViewed,
            )
        }
    }
}

/**
 * The player's picture on a surface of its own (a `TextureView`, which the circle clips), handed to the player while
 * it exists; see-through until the first frame, so the still shows until then.
 */
@Composable
private fun VideoNoteSurface(modifier: Modifier) {
    val sink = LocalVideoNoteSurface.current ?: return
    AndroidEmbeddedExternalSurface(modifier = modifier, isOpaque = false) {
        onSurface { surface, _, _ ->
            sink(surface)
            surface.onDestroyed { sink(null) }
        }
    }
}

/**
 * A square [side] across at rest, growing with [growth] (0–1) to the width the bubble may take, at most [max]: read in
 * the layout phase, so the spring measures the circle anew in each frame without recomposing it.
 */
private fun Modifier.growingCircle(
    side: Dp,
    max: Dp,
    growth: State<Float>,
): Modifier =
    layout { measurable, constraints ->
        val small = side.roundToPx()
        val full = minOf(constraints.maxWidth, max.roundToPx()).coerceAtLeast(small)
        val size = lerp(small, full, growth.value)
        val placeable = measurable.measure(constraints.copy(minWidth = size, maxWidth = size, minHeight = size))
        layout(size, size) { placeable.place(0, 0) }
    }

/** While [playback] is in the player, a ring around the circle: [ring] for how much has played, over [track]. */
private fun Modifier.progressRing(
    playback: Playback,
    ring: Color,
    track: Color,
    width: Dp,
): Modifier =
    if (!playback.inPlayer) {
        this
    } else {
        drawWithContent {
            drawContent()
            val stroke = Stroke(width.toPx())
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            val arc = size.copy(width = size.width - stroke.width, height = size.height - stroke.width)
            drawArc(track, startAngle = 0f, sweepAngle = FULL_TURN, useCenter = false, topLeft, arc, style = stroke)
            val sweep = FULL_TURN * playback.progress().coerceIn(0f, 1f)
            drawArc(ring, START_ANGLE, sweep, useCenter = false, topLeft = topLeft, size = arc, style = stroke)
        }
    }

/** The ring starts at the top, as a clock's hand. */
private const val START_ANGLE = -90f

private const val FULL_TURN = 360f

@PingogramPreviews
@Composable
private fun RoundVideoIdlePreview() {
    PingogramPreview { RoundVideoSample(Playback.Stopped) }
}

@PingogramPreviews
@Composable
private fun RoundVideoPlayingPreview() {
    PingogramPreview { RoundVideoSample(Playback(isPlaying = true, progress = { PLAYING_PROGRESS })) }
}

@PingogramPreviews
@Composable
private fun RoundVideoPausedPreview() {
    PingogramPreview { RoundVideoSample(Playback(isPlaying = false, progress = { PAUSED_PROGRESS })) }
}

@Composable
private fun RoundVideoSample(playback: Playback) {
    MessageVideoNote(
        content = ContentPreviewData.videoNote(rememberSampleMinithumbnail(), isViewed = playback.inPlayer),
        onPlayPause = {},
        playback = playback,
    )
}

private const val PLAYING_PROGRESS = 0.4f
private const val PAUSED_PROGRESS = 0.7f
