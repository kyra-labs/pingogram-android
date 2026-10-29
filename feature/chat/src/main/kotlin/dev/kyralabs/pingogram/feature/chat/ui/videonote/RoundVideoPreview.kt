package dev.kyralabs.pingogram.feature.chat.ui.videonote

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordingMeter
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.recordingLength
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The round preview of a video message being recorded (`docs/adr/0032-round-video-messages.md`,
 * `docs/DESIGN_SYSTEM.md` §6 Composer), over the messages above the composer while [recording] is a video message:
 * the camera's picture ([surfaceRequest], camera-compose's [CameraXViewfinder], already cropped to the recording's
 * square) clipped to `MaterialShapes.Circle`, two thirds of the window's shorter side and no taller than the room above
 * the composer, inside a determinate [CircularWavyProgressIndicator] that fills toward the minute a video message may
 * last ([VideoNoteLimits.MAX_DURATION_MILLIS]), its progress read in the draw phase. The messages behind are dimmed by
 * the `scrim` at 32%. Until the camera's picture comes, and once the recording ended as the screen stopped, a
 * `surfaceContainerHighest` circle with the camera symbol stands in. It grows in and out on the motion scheme's
 * spatial spring, fading on its effects spring.
 *
 * Screen readers hear "Video message, 0:07 of 1:00", once a second.
 */
@Composable
internal fun RoundVideoPreview(
    recording: VoiceRecordingUiState?,
    surfaceRequest: SurfaceRequest?,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val video = recording?.takeIf { it.video != null }
    // The last recording stays while the preview leaves.
    val shown = remember { mutableStateOf(video) }
    if (video != null) shown.value = video
    AnimatedVisibility(
        visible = video != null,
        modifier = modifier,
        enter = fadeIn(motionScheme.defaultEffectsSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            val margin = MaterialTheme.spacing.medium
            val side = min(previewSide(), min(maxWidth, maxHeight) - margin * 2)
            shown.value?.let { current ->
                RoundVideoFrame(
                    recording = current,
                    surfaceRequest = surfaceRequest.takeIf { current.video?.isStopped == false },
                    side = side,
                    modifier =
                        Modifier.animateEnterExit(
                            enter = scaleIn(motionScheme.defaultSpatialSpec()),
                            exit = scaleOut(motionScheme.fastSpatialSpec()),
                        ),
                )
            }
        }
    }
}

/**
 * The preview's circle of [side]: the progress ring around the camera's picture ([surfaceRequest]) or, without one,
 * the camera symbol on `surfaceContainerHighest`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes and RoundedPolygon.toShape
@Composable
internal fun RoundVideoFrame(
    recording: VoiceRecordingUiState,
    surfaceRequest: SurfaceRequest?,
    side: Dp,
    modifier: Modifier = Modifier,
) {
    val meter = recording.meter
    val description = previewDescription(meter)
    Box(
        modifier =
            modifier
                .size(side)
                .clearAndSetSemantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
        contentAlignment = Alignment.Center,
    ) {
        val stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke
        CircularWavyProgressIndicator(
            progress = { (meter.elapsedMillis.toFloat() / VideoNoteLimits.MAX_DURATION_MILLIS).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            stroke = stroke,
            trackStroke = stroke,
        )
        val circle = MaterialShapes.Circle.toShape()
        val inset = with(LocalDensity.current) { stroke.width.toDp() } + MaterialTheme.spacing.small
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .clip(circle)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (surfaceRequest != null) {
                CameraXViewfinder(surfaceRequest = surfaceRequest, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    painter = painterResource(UiR.drawable.ic_videocam),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.contentIcon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Video message, 0:07 of 1:00", changing once a second. */
@Composable
private fun previewDescription(meter: RecordingMeter): String {
    val seconds by remember(meter) { derivedStateOf { meter.elapsedMillis / MILLIS_PER_SECOND } }
    return stringResource(
        R.string.video_note_preview,
        recordingLength(seconds),
        recordingLength(VideoNoteLimits.MAX_DURATION_MILLIS / MILLIS_PER_SECOND),
    )
}

/** Two thirds of the window's shorter side, as `docs/adr/0032-round-video-messages.md` sets the preview. */
@Composable
private fun previewSide(): Dp {
    val size = LocalWindowInfo.current.containerSize
    val shorter = with(LocalDensity.current) { minOf(size.width, size.height).toDp() }
    return shorter * PREVIEW_SHARE
}

private const val PREVIEW_SHARE = 2f / 3f

/** Material's scrim opacity (`docs/DESIGN_SYSTEM.md` §2.2). */
private const val SCRIM_ALPHA = 0.32f

private const val MILLIS_PER_SECOND = 1_000L
