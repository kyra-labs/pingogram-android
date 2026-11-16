package dev.kyralabs.pingogram.feature.media.ui

import android.content.res.Resources
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Seek the current video (`REQUIREMENTS.md` FR-C10, board 25; its play or pause and ±10 s are in the middle,
 * [VideoCenterControls]): the time played, an expressive [Slider] and the length, left to right in every language, then
 * the speed chip ([SpeedChip]) while the speed is not 1×, mute and picture-in-picture. The slider follows the playback
 * in its layout and draw phases ([SliderState], updated from [VideoPlayback.progress]), except while the user drags it;
 * letting go seeks there. Its inactive track shows in `outline` how far the player has the video
 * ([VideoPlayback.buffered], read in the draw phase), as a stream downloads (`docs/adr/0028-streaming-media.md`).
 * Screen readers hear it as "0:48 of 2:31" and move it 10 s at a time. The times recompose only when the second
 * changes.
 */
@Composable
internal fun VideoControls(
    playback: VideoPlayback,
    durationSeconds: Int,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
    canPictureInPicture: Boolean = false,
) {
    val sliderState = remember(playback.messageId) { SliderState(value = playback.progress()) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val touching by rememberUpdatedState(sliderState.isDragging || pressed)
    val progress = playback.progress
    LaunchedEffect(sliderState, progress) {
        snapshotFlow { progress() }.collect { fraction -> if (!touching) sliderState.value = fraction }
    }
    val played by remember(sliderState, durationSeconds) {
        derivedStateOf { (sliderState.value * durationSeconds).toInt() }
    }
    val locale = LocalConfiguration.current.locales[0]
    val seekLabel = stringResource(R.string.media_seek)
    val seekState =
        stringResource(
            R.string.media_seek_state,
            formatDuration(played, locale),
            formatDuration(durationSeconds, locale),
        )
    // From 150% font the times go under the seek bar (board 25), which keeps its length.
    val stacked = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Media timelines fill left to right in every language (board 25).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!stacked) TimeText(formatDuration(played, locale))
                    SeekSlider(
                        sliderState,
                        interactionSource,
                        seekLabel,
                        seekState,
                        playback.buffered,
                        onEvent,
                        Modifier.weight(1f),
                    )
                    if (!stacked) TimeText(formatDuration(durationSeconds, locale))
                }
            }
            if (playback.speed != 1f) SpeedChip(playback.speed) { onEvent(MediaViewerEvent.SetSpeed(it)) }
            MuteButton(playback.isMuted) { onEvent(MediaViewerEvent.SetMuted(!playback.isMuted)) }
            if (canPictureInPicture) PictureInPictureButton(onClick = { onEvent(MediaViewerEvent.PictureInPicture) })
        }
        if (stacked) StackedTimes(formatDuration(played, locale), formatDuration(durationSeconds, locale))
    }
}

/** The seek bar of [VideoControls], named and stepped 10 s at a time for screen readers. */
@Suppress("LongParameterList") // The slider's state and touches, what screen readers hear, the buffered part.
@Composable
private fun SeekSlider(
    sliderState: SliderState,
    interactionSource: MutableInteractionSource,
    seekLabel: String,
    seekState: String,
    buffered: () -> Float,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier,
) {
    Slider(
        state = sliderState,
        onValueChange = { sliderState.value = it },
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = seekLabel
                stateDescription = seekState
                progressBarRangeInfo = ProgressBarRangeInfo(sliderState.value, 0f..1f)
                // Screen readers move it 10 s at a time, as "Back" and "Forward 10 seconds" do.
                setProgress { target ->
                    val step =
                        if (target >=
                            sliderState.value
                        ) {
                            SEEK_STEP_SECONDS
                        } else {
                            -SEEK_STEP_SECONDS
                        }
                    onEvent(MediaViewerEvent.SeekBy(step))
                    true
                }
            },
        onValueChangeFinished = { onEvent(MediaViewerEvent.Seek(sliderState.value)) },
        interactionSource = interactionSource,
        track = { state -> BufferedTrack(state, buffered) },
    )
}

/** Picture-in-picture, an icon button named for screen readers and in a [PlainTooltip] on a long press or hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PictureInPictureButton(onClick: () -> Unit) {
    val label = stringResource(R.string.media_picture_in_picture)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_picture_in_picture), contentDescription = label)
        }
    }
}

/** A time beside the slider: `labelSmall` with tabular figures, so it does not jitter as it counts. */
@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

/** The time played and the length under the seek bar, at its ends, left to right in every language. */
@Composable
private fun StackedTimes(
    played: String,
    length: String,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeText(played)
            TimeText(length)
        }
    }
}

/** From this font scale on, the video's times go under its seek bar. */
private const val LARGE_FONT_SCALE = 1.5f
