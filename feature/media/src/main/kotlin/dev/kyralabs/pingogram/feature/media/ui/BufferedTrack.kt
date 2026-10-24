package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * The slider's track with what is buffered drawn over its inactive part: from past the thumb's gap to [buffered] (0–1)
 * of the track's width, in the track's own shape, read in the draw phase.
 */
@Composable
internal fun BufferedTrack(
    state: SliderState,
    buffered: () -> Float,
) {
    val color = MaterialTheme.colorScheme.outline
    val inset = MaterialTheme.sizes.sliderInactiveTrackInset
    SliderDefaults.Track(
        sliderState = state,
        modifier =
            Modifier.drawWithContent {
                drawContent()
                // The seek slider's range is 0–1, so its value is the thumb's fraction of the track.
                val start = size.width * state.value.coerceIn(0f, 1f) + inset.toPx()
                val end = size.width * buffered().coerceIn(0f, 1f)
                if (end > start) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(color, Offset(start, 0f), Size(end - start, size.height), radius)
                }
            },
    )
}
